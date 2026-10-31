package com.baccours.ekho.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Five peaking biquad filters (RBJ cookbook) followed by a peak limiter, operating in place on
 * 16-bit mono PCM.
 *
 * Threading: [setLevels] may be called from any thread; [process] must only be called from the
 * audio thread. Filter state is owned by the audio thread, coefficients are published through a
 * volatile reference to an array that is never mutated after publication.
 */
class BiquadEqualizer(private val sampleRate: Int) {

    companion object {
        private const val COEFFICIENTS_PER_BAND = 5 // b0, b1, b2, a1, a2 (normalized by a0)
        private const val PEAKING_Q = 0.9
        private const val MAX_FREQUENCY_RATIO = 0.45 // skip bands too close to Nyquist
        private const val LIMITER_CEILING = 0.9
        private const val LIMITER_RELEASE_SECONDS = 0.05
        private const val FULL_SCALE = 32768.0
    }

    private val bandCount = EqualizerBands.COUNT

    @Volatile
    private var coefficients: DoubleArray = identityCoefficients()

    private val z1 = DoubleArray(bandCount)
    private val z2 = DoubleArray(bandCount)
    private var limiterGain = 1.0
    private val limiterRelease = 1.0 - exp(-1.0 / (sampleRate * LIMITER_RELEASE_SECONDS))

    /** Sets the gain of every band in millibels. Missing entries are treated as 0 dB. */
    fun setLevels(levelsMillibel: List<Int>) {
        val values = DoubleArray(bandCount * COEFFICIENTS_PER_BAND)
        for (band in 0 until bandCount) {
            val gainDb = levelsMillibel
                .getOrElse(band) { 0 }
                .coerceIn(EqualizerBands.MIN_LEVEL_MB, EqualizerBands.MAX_LEVEL_MB) / 100.0
            writePeaking(values, band, EqualizerBands.FREQUENCIES_HZ[band], gainDb)
        }
        coefficients = values
    }

    fun process(samples: ShortArray, count: Int) {
        val c = coefficients
        for (i in 0 until count) {
            var x = samples[i] / FULL_SCALE
            for (band in 0 until bandCount) {
                val o = band * COEFFICIENTS_PER_BAND
                val y = c[o] * x + z1[band]
                z1[band] = c[o + 1] * x - c[o + 3] * y + z2[band]
                z2[band] = c[o + 2] * x - c[o + 4] * y
                x = y
            }

            // Instant-attack / smooth-release limiter: never lets a sample exceed the ceiling.
            val peak = abs(x)
            val needed = if (peak > LIMITER_CEILING) LIMITER_CEILING / peak else 1.0
            val relaxed = limiterGain + (1.0 - limiterGain) * limiterRelease
            limiterGain = min(relaxed, needed)

            samples[i] = (x * limiterGain * FULL_SCALE)
                .roundToInt()
                .coerceIn(-32768, 32767)
                .toShort()
        }
    }

    private fun writePeaking(target: DoubleArray, band: Int, frequencyHz: Int, gainDb: Double) {
        val o = band * COEFFICIENTS_PER_BAND
        if (gainDb == 0.0 || frequencyHz >= sampleRate * MAX_FREQUENCY_RATIO) {
            writeIdentity(target, o)
            return
        }
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * frequencyHz / sampleRate
        val alpha = sin(w0) / (2.0 * PEAKING_Q)
        val cosW0 = cos(w0)

        val a0 = 1.0 + alpha / a
        target[o] = (1.0 + alpha * a) / a0
        target[o + 1] = (-2.0 * cosW0) / a0
        target[o + 2] = (1.0 - alpha * a) / a0
        target[o + 3] = (-2.0 * cosW0) / a0
        target[o + 4] = (1.0 - alpha / a) / a0
    }

    private fun identityCoefficients(): DoubleArray {
        val values = DoubleArray(bandCount * COEFFICIENTS_PER_BAND)
        for (band in 0 until bandCount) writeIdentity(values, band * COEFFICIENTS_PER_BAND)
        return values
    }

    private fun writeIdentity(target: DoubleArray, offset: Int) {
        target[offset] = 1.0
        target[offset + 1] = 0.0
        target[offset + 2] = 0.0
        target[offset + 3] = 0.0
        target[offset + 4] = 0.0
    }
}
