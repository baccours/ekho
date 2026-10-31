package com.baccours.ekho.audio

/**
 * Fixed band layout of the in-app equalizer. Using our own DSP (instead of the platform
 * `Equalizer` effect) makes the bands identical on every device and keeps the output on the
 * low-latency audio path, which platform effects disable.
 */
object EqualizerBands {
    val FREQUENCIES_HZ: List<Int> = listOf(60, 230, 910, 3600, 14000)
    val COUNT: Int = FREQUENCIES_HZ.size

    /** Band gain limits in millibels (1/100 dB). */
    const val MIN_LEVEL_MB = -1500
    const val MAX_LEVEL_MB = 1500
}
