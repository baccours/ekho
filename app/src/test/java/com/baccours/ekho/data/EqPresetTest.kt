package com.baccours.ekho.data

import com.baccours.ekho.audio.EqualizerBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqPresetTest {

    @Test
    fun flatAppliesNoGainAndCustomHasNoLevels() {
        assertEquals(List(EqualizerBands.COUNT) { 0 }, EqPreset.FLAT.defaultBandLevels)
        assertNull(EqPreset.CUSTOM.defaultBandLevels)
    }

    @Test
    fun voicePresetEmphasisesTheSpeechBand() {
        assertEquals(-200, EqPreset.VOICE.defaultBandLevels!![0]) // 60 Hz
        assertEquals(800, EqPreset.VOICE.defaultBandLevels[3]) // 3.6 kHz
    }

    @Test
    fun presetLevelsStayWithinBandLimits() {
        EqPreset.entries.mapNotNull { it.defaultBandLevels }.forEach { levels ->
            assertEquals(EqualizerBands.COUNT, levels.size)
            assertTrue(levels.all { it in EqualizerBands.MIN_LEVEL_MB..EqualizerBands.MAX_LEVEL_MB })
        }
    }

    @Test
    fun idsRoundTrip() {
        EqPreset.entries.forEach { assertEquals(it, EqPreset.fromId(it.id)) }
    }

    @Test
    fun legacyDisplayNamesAreMigrated() {
        assertEquals(EqPreset.VOICE, EqPreset.fromId("Voice Clarity"))
        assertEquals(EqPreset.BOOST, EqPreset.fromId("Low Latency Boost"))
        assertEquals(EqPreset.CUSTOM, EqPreset.fromId("Custom"))
    }

    @Test
    fun unknownOrMissingIdFallsBackToFlat() {
        assertEquals(EqPreset.FLAT, EqPreset.fromId(null))
        assertEquals(EqPreset.FLAT, EqPreset.fromId("nonsense"))
    }

    @Test
    fun customIsNotUserSelectable() {
        assertTrue(EqPreset.CUSTOM !in EqPreset.selectable)
    }
}
