package com.baccours.ekho.audio

import android.media.AudioDeviceInfo

/** What we know about the device an active stream is actually routed to. */
enum class RouteState { Safe, Unsafe, Unknown }

/**
 * Decides which output device types are safe for microphone pass-through.
 *
 * "Safe" means an output that is not the phone's own speaker or earpiece, which sit right next to
 * its microphone. External speakers (e.g. a Bluetooth LE speaker in another room, as used for a
 * baby monitor) are allowed on purpose. Android cannot tell a Bluetooth headset from a Bluetooth
 * speaker, so Bluetooth outputs are accepted as a whole.
 */
object LoopbackSafety {
    /** Priority rank for safe output selection (lower is better). */
    fun devicePriority(deviceType: Int): Int? = when (deviceType) {
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
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> 5
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> 6
        // SCO voice call outputs (fallback only, as SCO conflicts with USAGE_MEDIA tracks)
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 7

        // Deliberately excluded: TYPE_BUILTIN_SPEAKER is loudspeaker.
        else -> null
    }

    fun isSafeType(deviceType: Int): Boolean = devicePriority(deviceType) != null

    /** Classifies the device a stream is routed to; null means the route is not known (yet). */
    fun classify(routedDeviceType: Int?): RouteState = when {
        routedDeviceType == null -> RouteState.Unknown
        isSafeType(routedDeviceType) -> RouteState.Safe
        else -> RouteState.Unsafe
    }
}
