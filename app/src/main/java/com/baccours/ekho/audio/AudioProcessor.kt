package com.baccours.ekho.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.Equalizer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import timber.log.Timber

class AudioProcessor(
    private val sampleRate: Int = 44100,
    private val audioEncoding: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioEncoding)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var equalizer: Equalizer? = null
    
    private var isProcessing = false

    suspend fun start(onProcessing: suspend (Equalizer?) -> Unit) {
        if (isProcessing) return
        
        isProcessing = true
        
        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfigIn,
                audioEncoding,
                bufferSize
            )
            
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Timber.e("AudioRecord failed to initialize")
                return
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
                Timber.e("AudioTrack failed to initialize")
                return
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
            while (isProcessing && currentCoroutineContext().isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    track.write(buffer, 0, read)
                } else if (read < 0) {
                    Timber.e("Error reading audio data: $read")
                    break
                }

                yield()
            }
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException in AudioProcessor")
        } catch (e: Exception) {
            Timber.e(e, "Error in AudioProcessor")
        } finally {
            stop()
        }
    }

    fun stop() {
        isProcessing = false
        releaseResources()
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
        levels.forEach { (band, level) ->
            try {
                if (band < eq.numberOfBands) {
                    eq.setBandLevel(band.toShort(), level.toShort())
                }
            } catch (e: Exception) {
                Timber.e(e, "Error setting band $band to $level")
            }
        }
    }
}
