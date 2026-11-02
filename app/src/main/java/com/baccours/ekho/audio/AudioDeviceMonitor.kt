package com.baccours.ekho.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Output-device information the streaming logic depends on. */
interface OutputDevices {
    /** Emits the id of the preferred safe output device, or null when none is connected. */
    val safeOutputDeviceIdFlow: Flow<Int?>

    fun findOutputDevice(id: Int): AudioDeviceInfo?

    /** Synchronous snapshot of [safeOutputDeviceIdFlow], for re-checking without trusting events. */
    fun currentSafeOutputDeviceId(): Int?
}

class AudioDeviceMonitor(context: Context) : OutputDevices {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** The preferred connected output device (wired/USB first) that is safe for pass-through, if any. */
    fun findSafeOutputDevice(): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { LoopbackSafety.isSafeType(it.type) }
            .minByOrNull { LoopbackSafety.devicePriority(it.type) ?: Int.MAX_VALUE }

    /** The phone's own microphone, if the platform lists it. */
    fun findBuiltInMic(): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }

    override fun findOutputDevice(id: Int): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }

    override fun currentSafeOutputDeviceId(): Int? = findSafeOutputDevice()?.id

    fun isLoopbackSafe(): Boolean = findSafeOutputDevice() != null

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
        // Initial value after registering, so no change can fall between snapshot and callback.
        trySend(currentSafeOutputDeviceId())
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }.distinctUntilChanged()

    val loopbackSafeStatusFlow: Flow<Boolean> = safeOutputDeviceIdFlow
        .map { it != null }
        .distinctUntilChanged()
}
