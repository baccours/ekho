package com.baccours.ekho.audio

/** Equalizer band layout. Custom DSP ensures consistent bands and maintains low-latency path. */
object EqualizerBands {
    val FREQUENCIES_HZ: List<Int> = listOf(60, 230, 910, 3600, 14000)
    val COUNT: Int = FREQUENCIES_HZ.size

    /** Band gain limits in mB (1/100 dB). */
    const val MIN_LEVEL_MB = -1500
    const val MAX_LEVEL_MB = 1500
}
