package com.baccours.ekho.data

import android.content.Context
import android.media.audiofx.Equalizer
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val PRESET_KEY = stringPreferencesKey("equalizer_preset")
        val BYPASS_LOOPBACK_PROTECTION_KEY = booleanPreferencesKey("bypass_loopback_protection")
        const val BAND_PREFIX = "band_"
        
        const val PRESET_FLAT = "Flat"
        const val PRESET_VOICE = "Voice Clarity"
        const val PRESET_BOOST = "Low Latency Boost"
        const val PRESET_CUSTOM = "Custom"

        private val DEFAULT_FREQS = listOf(60, 230, 910, 3600, 14000)
    }

    val bandCount: Int
    val minBandLevel: Int
    val maxBandLevel: Int
    val bandFrequencies: List<Int>

    init {
        var count = DEFAULT_FREQS.size
        var min = -1500
        var max = 1500
        val freqs = mutableListOf<Int>()
        
        try {
            val eq = Equalizer(0, 0)
            count = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            if (range != null && range.size >= 2) {
                min = range[0].toInt()
                max = range[1].toInt()
            }
            for (i in 0 until count) {
                freqs.add(eq.getCenterFreq(i.toShort()) / 1000) // Convert to Hz
            }
            eq.release()
        } catch (e: Exception) {
            Timber.e(e, "Failed to query hardware equalizer, using defaults")
            // Fallback to defaults already set
            if (freqs.isEmpty()) {
                freqs.addAll(DEFAULT_FREQS)
                count = DEFAULT_FREQS.size
            }
        }
        
        bandCount = count
        minBandLevel = min
        maxBandLevel = max
        bandFrequencies = freqs
    }

    val presetFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PRESET_KEY] ?: PRESET_FLAT
    }

    val bypassLoopbackProtectionFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BYPASS_LOOPBACK_PROTECTION_KEY] ?: false
    }

    val bandLevelsFlow: Flow<Map<Int, Int>> = context.dataStore.data.map { preferences ->
        (0 until bandCount).associateWith { band ->
            preferences[intPreferencesKey("$BAND_PREFIX$band")] ?: 0
        }
    }

    suspend fun savePreset(preset: String) {
        context.dataStore.edit { preferences ->
            preferences[PRESET_KEY] = preset
            
            if (preset == PRESET_CUSTOM) return@edit

            for (band in 0 until bandCount) {
                val freq = bandFrequencies.getOrNull(band) ?: 0
                val level = when (preset) {
                    PRESET_VOICE -> when {
                        freq < 200 -> -200
                        freq < 1000 -> 200
                        freq < 4000 -> 800
                        else -> 200
                    }
                    PRESET_BOOST -> when {
                        freq < 200 -> 400
                        freq < 1000 -> 100
                        freq < 4000 -> 100
                        else -> 400
                    }
                    else -> 0 // PRESET_FLAT
                }
                preferences[intPreferencesKey("$BAND_PREFIX$band")] = level
            }
        }
    }

    suspend fun saveBandLevel(bandId: Int, level: Int) {
        context.dataStore.edit { preferences ->
            preferences[intPreferencesKey("$BAND_PREFIX$bandId")] = level
            preferences[PRESET_KEY] = PRESET_CUSTOM
        }
    }

    suspend fun saveBypassLoopbackProtection(bypass: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BYPASS_LOOPBACK_PROTECTION_KEY] = bypass
        }
    }
    
    suspend fun getAllBandLevels(): Map<Int, Int> {
        val prefs = context.dataStore.data.map { it }.first()
        return (0 until bandCount).associateWith { band ->
            prefs[intPreferencesKey("$BAND_PREFIX$band")] ?: 0
        }
    }
}
