package com.baccours.ekho.audio

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopbackSafetyTest {

    @Test
    fun headphonesAreSafe() {
        assertTrue(LoopbackSafety.isSafeType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertTrue(LoopbackSafety.isSafeType(AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertTrue(LoopbackSafety.isSafeType(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertTrue(LoopbackSafety.isSafeType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
    }

    @Test
    fun theDevicesOwnSpeakerAndEarpieceAreNeverSafe() {
        assertFalse(LoopbackSafety.isSafeType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertFalse(LoopbackSafety.isSafeType(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
    }

    @Test
    fun externalBleSpeakerIsSafeForBabyMonitorUse() {
        assertTrue(LoopbackSafety.isSafeType(AudioDeviceInfo.TYPE_BLE_SPEAKER))
        assertEquals(RouteState.Safe, LoopbackSafety.classify(AudioDeviceInfo.TYPE_BLE_SPEAKER))
    }

    @Test
    fun wiredAndUsbOutputsArePreferredOverWireless() {
        assertTrue(LoopbackSafety.isPreferredType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertTrue(LoopbackSafety.isPreferredType(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertFalse(LoopbackSafety.isPreferredType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertFalse(LoopbackSafety.isPreferredType(AudioDeviceInfo.TYPE_BLE_SPEAKER))
    }

    @Test
    fun routeClassificationDistinguishesSafeUnsafeAndUnknown() {
        assertEquals(RouteState.Safe, LoopbackSafety.classify(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertEquals(RouteState.Unsafe, LoopbackSafety.classify(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals(RouteState.Unknown, LoopbackSafety.classify(null))
    }
}
