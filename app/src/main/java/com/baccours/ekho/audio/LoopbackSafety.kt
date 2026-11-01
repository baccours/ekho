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

    /** True for wired/USB outputs, which win over the other safe types when both are present. */
    fun isPreferredType(deviceType: Int): Boolean = when (deviceType) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> true
        else -> false
    }

    fun isSafeType(deviceType: Int): Boolean = isPreferredType(deviceType) || when (deviceType) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_HEARING_AID -> true
        else -> false
    }

    /** Classifies the device a stream is routed to; null means the route is not known (yet). */
    fun classify(routedDeviceType: Int?): RouteState = when {
        routedDeviceType == null -> RouteState.Unknown
        isSafeType(routedDeviceType) -> RouteState.Safe
        else -> RouteState.Unsafe
    }
}
