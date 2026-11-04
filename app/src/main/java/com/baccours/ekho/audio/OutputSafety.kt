package com.baccours.ekho.audio

import android.media.AudioDeviceInfo

/** Output device safety state. */
enum class RouteState { Safe, Unsafe, Unknown }

/** Classifies output device safety for mic pass-through (excludes built-in speaker/earpiece). */
object OutputSafety {
    /** Device priority (lower value = higher priority). Returns null if unsafe. */
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
        // SCO fallback
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 7

        // Built-in speaker / earpiece
        else -> null
    }

    fun isSafeType(deviceType: Int): Boolean = devicePriority(deviceType) != null

    /** Classifies routed device safety. */
    fun classify(routedDeviceType: Int?): RouteState = when {
        routedDeviceType == null -> RouteState.Unknown
        isSafeType(routedDeviceType) -> RouteState.Safe
        else -> RouteState.Unsafe
    }
}
