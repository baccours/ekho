package com.baccours.ekho.data

/** Equalizer presets. [id] is persisted; display names are resolved by UI. */
enum class EqPreset(
    val id: String,
    /** Gain in mB (1/100 dB) for 5 bands (60Hz..14kHz). Null for [CUSTOM]. */
    val defaultBandLevels: List<Int>?,
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
        defaultBandLevels = null
    );

    companion object {
        /** User-selectable presets. */
        val selectable: List<EqPreset> = listOf(FLAT, VOICE, BOOST)

        // Migration map for legacy persisted display names.
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
