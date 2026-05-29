package com.baccours.ekho.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.Equalizer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import timber.log.Timber
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class AudioProcessor(
    private val sampleRate: Int = 44100,
    private val audioEncoding: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioEncoding)
    private val bufferSize = minBufferSize * 2

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var equalizer: Equalizer? = null

    private val isProcessing = AtomicBoolean(false)

    suspend fun start(onProcessing: suspend (Equalizer?) -> Unit) {
        if (!isProcessing.compareAndSet(expectedValue = false, newValue = true)) return
        
        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfigIn,
                audioEncoding,
                bufferSize
            )
            
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord failed to initialize")
            }
            audioRecord = record

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioEncoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfigOut)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                throw IllegalStateException("AudioTrack failed to initialize")
            }
            audioTrack = track

            val eq = Equalizer(0, track.audioSessionId).apply {
                enabled = true
            }
            equalizer = eq
            
            onProcessing(eq)

            record.startRecording()
            track.play()

            val buffer = ShortArray(bufferSize)
            while (isProcessing.load() && currentCoroutineContext().isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    track.write(buffer, 0, read)
                } else if (read < 0) {
                    Timber.e("Error reading audio data: $read")
                    break
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException in AudioProcessor")
        } catch (e: Exception) {
            Timber.e(e, "Error in AudioProcessor")
        } finally {
            isProcessing.store(false)
            releaseResources()
        }
    }

    fun stop() {
        if (!isProcessing.compareAndSet(expectedValue = true, newValue = false)) return
        try {
            // Calling stop() here forces record.read() in the start() loop to return immediately
            audioRecord?.stop()
        } catch (e: Exception) {
            Timber.e(e, "Error unblocking AudioRecord")
        }
    }

    private fun releaseResources() {
        equalizer?.release()
        equalizer = null
        
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

    fun applyBandLevels(levels: Map<Int, Int>) {
        val eq = equalizer ?: return
        try {
            levels.forEach { (band, level) ->
                if (band < eq.numberOfBands) {
                    eq.setBandLevel(band.toShort(), level.toShort())
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting band levels")
        }
    }
}
