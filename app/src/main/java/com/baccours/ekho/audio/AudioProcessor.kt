package com.baccours.ekho.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

class AudioProcessor(
    private val context: Context,
    private val sampleRate: Int = 44100,
    private val audioEncoding: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private val tag = "AudioProcessor"
    
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioEncoding)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var equalizer: Equalizer? = null
    
    private var isProcessing = false

    suspend fun start(onProcessing: suspend (Equalizer?) -> Unit) {
        if (isProcessing) return
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(tag, "RECORD_AUDIO permission not granted")
            return
        }

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
                Log.e(tag, "AudioRecord failed to initialize")
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
                Log.e(tag, "AudioTrack failed to initialize")
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
            while (isProcessing && coroutineContext.isActive) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(tag, "Permission revoked during streaming")
                    break
                }
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    track.write(buffer, 0, read)
                }
            }
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException in AudioProcessor", e)
        } catch (e: Exception) {
            Log.e(tag, "Error in AudioProcessor", e)
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
                Log.e(tag, "Error stopping AudioRecord", e)
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
                Log.e(tag, "Error stopping AudioTrack", e)
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
                Log.e(tag, "Error setting band $band to $level", e)
            }
        }
    }
}
