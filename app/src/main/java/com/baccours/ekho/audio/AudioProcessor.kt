package com.baccours.ekho.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRouting
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

enum class StreamResult {
    /** The stream ended because it was stopped or canceled. */
    Stopped,

    /** The stream ended because audio could not be opened or broke down. Retrying may help. */
    Failed,

    /** The stream was cut because it was (or was about to be) routed to an unsafe output. */
    RouteUnsafe
}

class AudioProcessor(
    private val preferredSampleRate: Int = DEFAULT_SAMPLE_RATE
) : AudioEngine {

    companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000
        private const val CHUNK_MILLIS = 10
        private const val BYTES_PER_SAMPLE = 2
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ROUTE_POLL_MILLIS = 50L

        // If the platform never reports a route, stop waiting for confirmation and rely on the
        // device pinning and the device callbacks instead of staying silent forever.
        private const val UNKNOWN_ROUTE_TIMEOUT_MILLIS = 1_500L

        /** The device's native output rate; using it avoids resampling and keeps the fast path. */
        fun nativeSampleRate(context: Context): Int {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            return audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: DEFAULT_SAMPLE_RATE
        }
    }

    private val isProcessing = AtomicBoolean(false)

    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var dsp: BiquadEqualizer? = null
    @Volatile private var latestLevels: List<Int> = emptyList()

    // Route supervision (only used when a safe route is required).
    private val routeLock = Any()
    @Volatile private var routingListener: AudioRouting.OnRoutingChangedListener? = null
    @Volatile private var routeViolated = false
    private var unknownRouteSince = 0L // guarded by routeLock

    // Blocking reads must not occupy a shared pool thread; the audio thread also gets audio priority.
    private val audioDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            runnable.run()
        }, "ekho-audio")
    }.asCoroutineDispatcher()

    override suspend fun start(preferredOutput: AudioDeviceInfo?, requireSafeRoute: Boolean): StreamResult {
        if (!isProcessing.compareAndSet(false, true)) return StreamResult.Stopped
        routeViolated = false
        synchronized(routeLock) { unknownRouteSince = 0L }

        return try {
            coroutineScope {
                // A blocked AudioRecord.read() cannot observe coroutine cancellation, so a watchdog
                // stops the recorder the moment we are canceled. This unblocks the read at once.
                val watchdog = launch(Dispatchers.Default) {
                    try {
                        awaitCancellation()
                    } finally {
                        stop()
                    }
                }
                // Backup for the routing callback (which arrives on the main thread): confirms the
                // route before unmuting and cuts the stream if it ever stops being safe.
                val routeMonitor = if (requireSafeRoute) {
                    launch(Dispatchers.Default) {
                        while (true) {
                            delay(ROUTE_POLL_MILLIS.milliseconds)
                            evaluateRoute()
                        }
                    }
                } else {
                    null
                }
                try {
                    withContext(audioDispatcher) { runSession(preferredOutput, requireSafeRoute) }
                } finally {
                    routeMonitor?.cancel()
                    watchdog.cancel()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error in AudioProcessor")
            StreamResult.Failed
        } finally {
            isProcessing.set(false)
            releaseResources()
        }
    }

    override fun stop() {
        if (!isProcessing.compareAndSet(true, false)) return
        try {
            audioTrack?.setVolume(0f)
        } catch (e: Exception) {
            Timber.e(e, "Error muting AudioTrack")
        }
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Timber.e(e, "Error unblocking AudioRecord")
        }
    }

    /** Releases the audio thread. The processor cannot be used afterward. */
    fun close() {
        stop()
        audioDispatcher.close()
    }

    override fun setBandLevels(levels: List<Int>) {
        latestLevels = levels
        dsp?.setLevels(levels)
    }

    // -------------------------------------------------------------------------
    // Audio thread
    // -------------------------------------------------------------------------
    private fun runSession(preferredOutput: AudioDeviceInfo?, requireSafeRoute: Boolean): StreamResult {
        val (record, sampleRate) = openRecord() ?: run {
            Timber.e("AudioRecord failed to initialize")
            return StreamResult.Failed
        }
        audioRecord = record

        val chunkSamples = sampleRate * CHUNK_MILLIS / 1000
        val track = buildTrack(sampleRate, chunkSamples * BYTES_PER_SAMPLE, preferredOutput)
        audioTrack = track

        val equalizer = BiquadEqualizer(sampleRate).also { it.setLevels(latestLevels) }
        dsp = equalizer

        if (!isProcessing.get()) return StreamResult.Stopped

        if (requireSafeRoute) {
            // Stay silent until the route is confirmed safe, so a wrong route can never leak audio.
            track.setVolume(0f)
            val listener = AudioRouting.OnRoutingChangedListener { evaluateRoute() }
            routingListener = listener
            track.addOnRoutingChangedListener(listener, null)
        }

        record.startRecording()
        track.play()

        var result = StreamResult.Stopped
        val buffer = ShortArray(chunkSamples)
        while (isProcessing.get()) {
            val read = record.read(buffer, 0, buffer.size)
            if (read > 0) {
                equalizer.process(buffer, read)
                val written = track.write(buffer, 0, read)
                if (written < 0) {
                    if (isProcessing.get()) {
                        Timber.e("Error writing audio data: $written")
                        result = StreamResult.Failed
                    }
                    break
                }
            } else if (read < 0) {
                if (isProcessing.get()) {
                    Timber.e("Error reading audio data: $read")
                    result = StreamResult.Failed
                }
                break
            }
        }
        return if (routeViolated) StreamResult.RouteUnsafe else result
    }

    /**
     * Checks where the stream is actually routed. Called from the routing callback and from a
     * poller; idempotent and safe to call from any thread.
     */
    private fun evaluateRoute() {
        val track = audioTrack ?: return
        if (!isProcessing.get()) return

        val state = try {
            LoopbackSafety.classify(track.routedDevice?.type)
        } catch (e: Exception) {
            Timber.w(e, "Could not query the routed device")
            RouteState.Unknown
        }

        synchronized(routeLock) { applyRouteState(state, track) }
    }

    private fun applyRouteState(state: RouteState, track: AudioTrack) {
        when (state) {
            RouteState.Safe -> {
                unknownRouteSince = 0L
                unmute(track)
            }

            RouteState.Unsafe -> {
                if (!routeViolated) {
                    Timber.w("Stream routed to an unsafe output; cutting it")
                    routeViolated = true
                    stop()
                }
            }

            RouteState.Unknown -> {
                val now = SystemClock.elapsedRealtime()
                if (unknownRouteSince == 0L) {
                    unknownRouteSince = now
                } else if (now - unknownRouteSince > UNKNOWN_ROUTE_TIMEOUT_MILLIS) {
                    Timber.w("Route still unknown; unmuting based on device pinning")
                    unmute(track)
                }
            }
        }
    }

    private fun unmute(track: AudioTrack) {
        if (!routeViolated && isProcessing.get()) {
            try {
                track.setVolume(1f)
            } catch (e: Exception) {
                Timber.e(e, "Error unmuting AudioTrack")
            }
        }
    }

    private fun openRecord(): Pair<AudioRecord, Int>? {
        for (rate in listOf(preferredSampleRate, 48_000, 44_100).distinct()) {
            val minBuffer = AudioRecord.getMinBufferSize(rate, CHANNEL_IN, ENCODING)
            if (minBuffer <= 0) continue
            val chunkBytes = rate * CHUNK_MILLIS / 1000 * BYTES_PER_SAMPLE

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    rate,
                    CHANNEL_IN,
                    ENCODING,
                    maxOf(minBuffer, chunkBytes * 4)
                )
            } catch (e: SecurityException) {
                Timber.e(e, "Missing RECORD_AUDIO permission for AudioRecord")
                return null
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "AudioRecord rejected sample rate $rate")
                continue
            }

            if (record.state == AudioRecord.STATE_INITIALIZED) return record to rate
            record.release()
        }
        return null
    }

    private fun buildTrack(
        sampleRate: Int,
        chunkBytes: Int,
        preferredOutput: AudioDeviceInfo?
    ): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_OUT, ENCODING)
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(sampleRate)
                    .setChannelMask(CHANNEL_OUT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, chunkBytes * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        val track = builder.build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw IllegalStateException("AudioTrack failed to initialize")
        }

        // Pin the output to the safe device instead of trusting default routing.
        if (preferredOutput != null && !track.setPreferredDevice(preferredOutput)) {
            Timber.w("Could not pin output to device ${preferredOutput.id}")
        }
        return track
    }

    private fun releaseResources() {
        dsp = null

        audioRecord?.apply {
            try {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    stop()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error stopping AudioRecord")
            }
            release()
        }
        audioRecord = null

        routingListener?.let { listener ->
            try {
                audioTrack?.removeOnRoutingChangedListener(listener)
            } catch (e: Exception) {
                Timber.e(e, "Error removing routing listener")
            }
        }
        routingListener = null

        audioTrack?.apply {
            try {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    pause()
                    flush()
                    stop()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error stopping AudioTrack")
            }
            release()
        }
        audioTrack = null
    }
}
