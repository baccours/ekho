package com.baccours.ekho.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Output device monitor. */
interface OutputDevices {
    /** Emits preferred safe output device ID, or null if none connected. */
    val safeOutputDeviceIdFlow: Flow<Int?>

    fun findOutputDevice(id: Int): AudioDeviceInfo?

    /** Snapshot of [safeOutputDeviceIdFlow]. */
    fun currentSafeOutputDeviceId(): Int?
}

class AudioDeviceMonitor(context: Context) : OutputDevices {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Safe output device (wired/USB prioritized), if connected. */
    fun findSafeOutputDevice(): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { OutputSafety.isSafeType(it.type) }
            .minByOrNull { OutputSafety.devicePriority(it.type) ?: Int.MAX_VALUE }

    /** Built-in microphone, if available. */
    fun findBuiltInMic(): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }

    override fun findOutputDevice(id: Int): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }

    override fun currentSafeOutputDeviceId(): Int? = findSafeOutputDevice()?.id

    override val safeOutputDeviceIdFlow: Flow<Int?> = callbackFlow {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                trySend(currentSafeOutputDeviceId())
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                trySend(currentSafeOutputDeviceId())
            }
        }

        audioManager.registerAudioDeviceCallback(callback, null)
        // Emit initial state.
        trySend(currentSafeOutputDeviceId())
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }.distinctUntilChanged()
}
