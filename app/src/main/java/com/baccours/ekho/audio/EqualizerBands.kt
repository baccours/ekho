package com.baccours.ekho.audio

/** Equalizer band configuration and gain limits (mB = 1/100 dB). */
object EqualizerBands {
    val FREQUENCIES_HZ: List<Int> = listOf(60, 230, 910, 3600, 14000)
    val COUNT: Int = FREQUENCIES_HZ.size

    /** Gain limits in mB (-15 to +15 dB). */
    const val MIN_LEVEL_MB = -1500
    const val MAX_LEVEL_MB = 1500
}
