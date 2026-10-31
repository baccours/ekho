package com.baccours.ekho.data

/**
 * Equalizer presets. [id] is what gets persisted, so it must stay stable; display names are
 * resolved by the UI.
 */
enum class EqPreset(val id: String) {
    FLAT("flat"),
    VOICE("voice"),
    BOOST("boost"),
    CUSTOM("custom");

    /** Gain in millibels (1/100 dB) this preset applies to a band centred on [frequencyHz]. */
    fun levelFor(frequencyHz: Int): Int = when (this) {
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
