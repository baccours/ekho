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

class AudioProcessor(
    private val preferredSampleRate: Int = DEFAULT_SAMPLE_RATE,
    /** Microphone to record from, or null for default. Pinning built-in mic avoids headset mic issues. */
    private val inputDevice: () -> AudioDeviceInfo? = { null }
) : AudioEngine {

    companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000
        private const val CHUNK_MILLIS = 10
        private const val BYTES_PER_SAMPLE = 2
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        // Polling interval before/after output is unmuted.
        private const val ROUTE_POLL_FAST_MILLIS = 50L
        private const val ROUTE_POLL_SLOW_MILLIS = 1_000L

        /** Native output sample rate to avoid resampling. */
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

    // Applied by the audio thread on change.
    @Volatile private var latestLevels: List<Int> = emptyList()

    // Route supervision for safe routes.
    private val routeLock = Any()
    private val routeGuard = RouteGuard() // guarded by routeLock
    @Volatile private var routingListener: AudioRouting.OnRoutingChangedListener? = null
    @Volatile private var routeViolated = false
    @Volatile private var audible = false // set by runSession/unmute
    private var lastLoggedRouteType: Int? = null // guarded by routeLock

    // Single-thread executor with high audio priority for blocking IO.
    private val audioDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            runnable.run()
        }, "ekho-audio")
    }.asCoroutineDispatcher()

    override suspend fun start(
        preferredOutput: AudioDeviceInfo?,
        requireSafeRoute: Boolean,
        onStreaming: () -> Unit
    ): StreamResult {
        // Never stream on default route if safe route is required but output is missing.
        if (requireSafeRoute && preferredOutput == null) return StreamResult.RouteUnsafe
        if (!isProcessing.compareAndSet(false, true)) return StreamResult.Stopped
        routeViolated = false
        audible = false
        synchronized(routeLock) {
            routeGuard.reset(SystemClock.elapsedRealtime())
            lastLoggedRouteType = null
        }

        return try {
            coroutineScope {
                // Watchdog to stop recorder and unblock read() on coroutine cancellation.
                val watchdog = launch(Dispatchers.Default) {
                    try {
                        awaitCancellation()
                    } finally {
                        stop()
                    }
                }
                // Poll route safety as a backup to the main-thread routing callback.
                val routeMonitor = if (requireSafeRoute) {
                    launch(Dispatchers.Default) {
                        while (true) {
                            val pollMillis =
                                if (audible) ROUTE_POLL_SLOW_MILLIS else ROUTE_POLL_FAST_MILLIS
                            delay(pollMillis.milliseconds)
                            evaluateRoute()
                        }
                    }
                } else {
                    null
                }
                try {
                    withContext(audioDispatcher) { runSession(preferredOutput, requireSafeRoute, onStreaming) }
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
            audioTrack?.apply {
                setVolume(0f)
                // Pause and flush to unblock a stalled write().
                pause()
                flush()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error muting AudioTrack")
        }
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Timber.e(e, "Error unblocking AudioRecord")
        }
    }

    /** Releases resources and stops the audio thread. Cannot be reused. */
    fun close() {
        stop()
        audioDispatcher.close()
    }

    override fun setBandLevels(levels: List<Int>) {
        latestLevels = levels
    }

    // -------------------------------------------------------------------------
    // Audio thread
    // -------------------------------------------------------------------------
    private fun runSession(
        preferredOutput: AudioDeviceInfo?,
        requireSafeRoute: Boolean,
        onStreaming: () -> Unit
    ): StreamResult {
        val (record, sampleRate) = openRecord() ?: run {
            Timber.e("AudioRecord failed to initialize")
            return StreamResult.Failed
        }
        audioRecord = record

        inputDevice()?.let { mic ->
            val pinned = record.setPreferredDevice(mic)
            Timber.i("Input pinned to built-in microphone (id=${mic.id}): $pinned")
        }

        val chunkSamples = sampleRate * CHUNK_MILLIS / 1000
        val track = buildTrack(sampleRate, chunkSamples * BYTES_PER_SAMPLE, preferredOutput)
        audioTrack = track

        val equalizer = BiquadEqualizer(sampleRate)
        var appliedLevels: List<Int>? = null

        if (!isProcessing.get()) return if (routeViolated) StreamResult.RouteUnsafe else StreamResult.Stopped

        if (requireSafeRoute) {
            // Mute until route is verified safe.
            track.setVolume(0f)
            val listener = AudioRouting.OnRoutingChangedListener { evaluateRoute() }
            routingListener = listener
            track.addOnRoutingChangedListener(listener, null)
        } else {
            audible = true
        }

        record.startRecording()
        track.play()

        var result = StreamResult.Stopped
        var announced = false
        val buffer = ShortArray(chunkSamples)
        while (isProcessing.get()) {
            val read = record.read(buffer, 0, buffer.size)
            if (read > 0) {
                val levels = latestLevels
                if (levels !== appliedLevels) {
                    equalizer.setLevels(levels)
                    appliedLevels = levels
                }
                equalizer.process(buffer, read)
                val written = track.write(buffer, 0, read)
                if (written < 0) {
                    if (isProcessing.get()) {
                        Timber.e("Error writing audio data: $written")
                        result = StreamResult.Failed
                    }
                    break
                }
                if (!announced && audible) {
                    announced = true
                    onStreaming()
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

    /** Evaluates actual stream routing. Thread-safe and idempotent. */
    private fun evaluateRoute() {
        val track = audioTrack ?: return
        if (!isProcessing.get()) return

        val routedType = try {
            track.routedDevice?.type
        } catch (e: Exception) {
            Timber.w(e, "Could not query the routed device")
            null
        }

        val state = OutputSafety.classify(routedType)
        synchronized(routeLock) {
            if (routedType != lastLoggedRouteType) {
                lastLoggedRouteType = routedType
                Timber.i("Routed output type=$routedType -> $state")
            }

            when (routeGuard.onRoute(state, SystemClock.elapsedRealtime())) {
                RouteGuard.Action.Unmute -> unmute(track)
                RouteGuard.Action.Cut -> cut(routedType)
                RouteGuard.Action.None -> Unit
            }
        }
    }

    private fun cut(routedType: Int? = null) {
        if (routeViolated) return
        Timber.w("Stream routed to an unsafe output (type=$routedType); cutting it")
        routeViolated = true
        stop()
    }

    private fun unmute(track: AudioTrack) {
        if (!routeViolated && isProcessing.get()) {
            try {
                track.setVolume(1f)
                audible = true
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

        // Pin output device instead of defaulting.
        if (preferredOutput != null) {
            val pinned = track.setPreferredDevice(preferredOutput)
            Timber.i("Output pinned to type=${preferredOutput.type} id=${preferredOutput.id}: $pinned")
        }
        return track
    }

    private fun releaseResources() {
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
