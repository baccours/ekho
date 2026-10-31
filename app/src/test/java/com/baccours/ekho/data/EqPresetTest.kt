package com.baccours.ekho.data

import com.baccours.ekho.audio.EqualizerBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqPresetTest {

    @Test
    fun flatAndCustomApplyNoGain() {
        EqualizerBands.FREQUENCIES_HZ.forEach { frequency ->
            assertEquals(0, EqPreset.FLAT.levelFor(frequency))
            assertEquals(0, EqPreset.CUSTOM.levelFor(frequency))
        }
    }

    @Test
    fun voicePresetEmphasisesTheSpeechBand() {
        assertEquals(-200, EqPreset.VOICE.levelFor(60))
        assertEquals(800, EqPreset.VOICE.levelFor(3600))
    }

    @Test
    fun presetLevelsStayWithinBandLimits() {
        EqPreset.entries.forEach { preset ->
            EqualizerBands.FREQUENCIES_HZ.forEach { frequency ->
                assertTrue(preset.levelFor(frequency) in EqualizerBands.MIN_LEVEL_MB..EqualizerBands.MAX_LEVEL_MB)
            }
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
