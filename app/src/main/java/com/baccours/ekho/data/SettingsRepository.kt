package com.baccours.ekho.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val PRESET_KEY = stringPreferencesKey("equalizer_preset")
        val ALLOW_SPEAKER_KEY = booleanPreferencesKey("allow_speaker_transmission")
        val BAND_PREFIX = "band_"
        
        const val PRESET_FLAT = "Flat"
        const val PRESET_VOICE = "Voice Clarity"
        const val PRESET_BOOST = "Low Latency Boost"
        const val PRESET_CUSTOM = "Custom"
    }

    val presetFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PRESET_KEY] ?: PRESET_FLAT
    }

    val allowSpeakerFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ALLOW_SPEAKER_KEY] ?: false
    }

    val bandLevelsFlow: Flow<Map<Int, Int>> = context.dataStore.data.map { preferences ->
        (0 until 5).associateWith { band ->
            preferences[intPreferencesKey("$BAND_PREFIX$band")] ?: 0
        }
    }

    fun getBandLevelFlow(bandId: Int): Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[intPreferencesKey("$BAND_PREFIX$bandId")] ?: 0
    }

    suspend fun savePreset(preset: String) {
        context.dataStore.edit { preferences ->
            preferences[PRESET_KEY] = preset
            
            val levels = when (preset) {
                PRESET_VOICE -> mapOf(0 to -200, 1 to 0, 2 to 400, 3 to 800, 4 to 200)
                PRESET_BOOST -> mapOf(0 to 300, 1 to 200, 2 to 0, 3 to 200, 4 to 300)
                PRESET_FLAT -> mapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0)
                else -> null
            }

            levels?.forEach { (band, level) ->
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

    suspend fun saveAllowSpeaker(allow: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ALLOW_SPEAKER_KEY] = allow
        }
    }
    
    suspend fun getAllBandLevels(numBands: Int): Map<Int, Int> {
        val prefs = context.dataStore.data.map { it }.first()
        return (0 until numBands).associateWith { band ->
            prefs[intPreferencesKey("$BAND_PREFIX$band")] ?: 0
        }
    }
}
