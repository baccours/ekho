package com.baccours.ekho.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class BiquadEqualizerTest {

    private val sampleRate = 48_000

    private fun sine(frequencyHz: Int, amplitude: Double, samples: Int) = ShortArray(samples) { n ->
        (amplitude * 32767 * sin(2 * PI * frequencyHz * n / sampleRate)).roundToInt().toShort()
    }

    private fun steadyStatePeak(buffer: ShortArray) =
        buffer.drop(buffer.size / 2).maxOf { abs(it.toInt()) }

    @Test
    fun flatLevelsLeaveSamplesUntouched() {
        val equalizer = BiquadEqualizer(sampleRate)
        equalizer.setLevels(List(EqualizerBands.COUNT) { 0 })

        val input = sine(910, 0.5, 4_800)
        val buffer = input.copyOf()
        equalizer.process(buffer, buffer.size)

        assertTrue(input.indices.all { input[it] == buffer[it] })
    }

    @Test
    fun boostAtCentreFrequencyMatchesRequestedGain() {
        val equalizer = BiquadEqualizer(sampleRate)
        equalizer.setLevels(listOf(0, 0, 1200, 0, 0)) // +12 dB at 910 Hz

        val input = sine(910, 0.1, 48_000)
        val buffer = input.copyOf()
        equalizer.process(buffer, buffer.size)

        val ratio = steadyStatePeak(buffer).toDouble() / steadyStatePeak(input)
        assertEquals(3.98, ratio, 0.2)
    }

    @Test
    fun cutAtCentreFrequencyMatchesRequestedGain() {
        val equalizer = BiquadEqualizer(sampleRate)
        equalizer.setLevels(listOf(0, 0, -1200, 0, 0)) // -12 dB at 910 Hz

        val input = sine(910, 0.5, 48_000)
        val buffer = input.copyOf()
        equalizer.process(buffer, buffer.size)

        val ratio = steadyStatePeak(buffer).toDouble() / steadyStatePeak(input)
        assertEquals(0.25, ratio, 0.03)
    }

    @Test
    fun limiterKeepsPeaksBelowCeilingEvenWithMaximumBoost() {
        val equalizer = BiquadEqualizer(sampleRate)
        equalizer.setLevels(List(EqualizerBands.COUNT) { EqualizerBands.MAX_LEVEL_MB })

        val buffer = sine(910, 1.0, 48_000)
        equalizer.process(buffer, buffer.size)

        assertTrue(buffer.all { abs(it.toInt()) <= (0.9 * 32768).toInt() + 1 })
    }

    @Test
    fun levelsOutsideTheSupportedRangeAreClamped() {
        val equalizer = BiquadEqualizer(sampleRate)
        equalizer.setLevels(listOf(0, 0, 9_999, 0, 0))

        val buffer = sine(910, 0.01, 48_000)
        equalizer.process(buffer, buffer.size)

        // +15 dB is the ceiling (~5.6x), never the requested ~100 dB.
        assertTrue(steadyStatePeak(buffer) < 0.01 * 32767 * 6.5)
    }
}
