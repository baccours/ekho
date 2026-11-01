package com.baccours.ekho.data

import com.baccours.ekho.audio.EqualizerBands

/**
 * Equalizer presets. [id] is what gets persisted, so it must stay stable; display names are
 * resolved by the UI.
 */
enum class EqPreset(
    val id: String,
    /** Default gain in millibels (1/100 dB) for the 5 bands: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz. */
    val defaultBandLevels: List<Int>,
) {
    FLAT(
        id = "flat",
        defaultBandLevels = listOf(0, 0, 0, 0, 0)
    ),
    VOICE(
        id = "voice",
        defaultBandLevels = listOf(-200, 200, 200, 800, 200)
    ),
    BOOST(
        id = "boost",
        defaultBandLevels = listOf(400, 100, 100, 100, 400)
    ),
    CUSTOM(
        id = "custom",
        defaultBandLevels = listOf(0, 0, 0, 0, 0)
    );

    /**
     * Gain in millibels (1/100 dB) this preset applies to a band centred on [frequencyHz].
     *
     * Frequency boundaries reference:
     * - < 200 Hz: Bass / sub-bass (band 60Hz)
     * - < 1000 Hz: Low / vocal mids (bands 230Hz, 910Hz)
     * - < 4000 Hz: High / presence mids (band 3.6kHz)
     * - >= 4000 Hz: Treble / air (band 14kHz)
     */
    fun levelFor(frequencyHz: Int): Int {
        val bandIndex = EqualizerBands.FREQUENCIES_HZ.indexOf(frequencyHz)
        if (bandIndex >= 0) {
            return defaultBandLevels[bandIndex]
        }
        // Fallback calculation for arbitrary frequencies using frequency boundaries:
        return when (this) {
            VOICE -> when {
                frequencyHz < 200 -> -200
                frequencyHz < 1000 -> 200
                frequencyHz < 4000 -> 800
                else -> 200
            }
            BOOST -> when {
                frequencyHz < 200 -> 400
                frequencyHz < 1000 -> 100
                frequencyHz < 4000 -> 100
                else -> 400
            }
            FLAT, CUSTOM -> 0
        }
    }

    companion object {
        /** Presets the user can pick; [CUSTOM] is only entered by moving a slider. */
        val selectable: List<EqPreset> = listOf(FLAT, VOICE, BOOST)

        // Earlier versions persisted display names.
        private val legacyNames = mapOf(
            "Flat" to FLAT,
            "Voice Clarity" to VOICE,
            "Low Latency Boost" to BOOST,
            "Custom" to CUSTOM
        )

        fun fromId(id: String?): EqPreset =
            entries.firstOrNull { it.id == id } ?: legacyNames[id] ?: FLAT
    }
}
