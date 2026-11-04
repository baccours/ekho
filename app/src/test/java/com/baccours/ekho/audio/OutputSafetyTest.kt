package com.baccours.ekho.audio

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputSafetyTest {

    @Test
    fun headphonesAreSafe() {
        assertTrue(OutputSafety.isSafeType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertTrue(OutputSafety.isSafeType(AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertTrue(OutputSafety.isSafeType(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertTrue(OutputSafety.isSafeType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
    }

    @Test
    fun theDevicesOwnSpeakerAndEarpieceAreNeverSafe() {
        assertFalse(OutputSafety.isSafeType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertFalse(OutputSafety.isSafeType(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
    }

    @Test
    fun externalBleSpeakerIsSafeForBabyMonitorUse() {
        assertTrue(OutputSafety.isSafeType(AudioDeviceInfo.TYPE_BLE_SPEAKER))
        assertEquals(RouteState.Safe, OutputSafety.classify(AudioDeviceInfo.TYPE_BLE_SPEAKER))
    }

    @Test
    fun a2dpIsPrioritizedOverScoForMediaTracks() {
        val wired = OutputSafety.devicePriority(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)!!
        val bluetooth = OutputSafety.devicePriority(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)!!
        val sco = OutputSafety.devicePriority(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)!!
        assertTrue(wired < bluetooth)
        assertTrue(bluetooth < sco)
    }

    @Test
    fun routeClassificationDistinguishesSafeUnsafeAndUnknown() {
        assertEquals(RouteState.Safe, OutputSafety.classify(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertEquals(RouteState.Unsafe, OutputSafety.classify(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals(RouteState.Unknown, OutputSafety.classify(null))
    }
}
