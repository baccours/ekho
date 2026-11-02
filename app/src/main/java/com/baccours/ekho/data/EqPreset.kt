package com.baccours.ekho.data

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
