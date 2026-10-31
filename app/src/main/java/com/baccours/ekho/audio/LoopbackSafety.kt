package com.baccours.ekho.audio

import android.media.AudioDeviceInfo

/** What we know about the device an active stream is actually routed to. */
enum class RouteState { Safe, Unsafe, Unknown }

/**
 * Decides which output device types are considered safe for microphone pass-through, i.e. unlikely
 * to be a loudspeaker that could feed back into the microphone.
 */
object LoopbackSafety {

    /**
     * Lower values are preferred; `null` means the device type is not safe.
     * Note: Android does not tell a Bluetooth headset from a Bluetooth speaker, so A2DP is
     * accepted as the best effort.
     */
    fun priority(deviceType: Int): Int? = when (deviceType) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 0

        AudioDeviceInfo.TYPE_USB_HEADSET -> 1

        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_LINE_ANALOG -> 2

        AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_BLE_HEADSET -> 3

        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 4
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 5
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> 6

        // Deliberately excluded: TYPE_BLE_SPEAKER and TYPE_BUILTIN_SPEAKER are loudspeakers.
        else -> null
    }

    fun isSafeType(deviceType: Int): Boolean = priority(deviceType) != null

    /** Classifies the device a stream is routed to; null means the route is not known (yet). */
    fun classify(routedDeviceType: Int?): RouteState = when {
        routedDeviceType == null -> RouteState.Unknown
        isSafeType(routedDeviceType) -> RouteState.Safe
        else -> RouteState.Unsafe
    }
}
