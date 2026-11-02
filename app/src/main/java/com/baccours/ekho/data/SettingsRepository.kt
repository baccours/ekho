package com.baccours.ekho.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.baccours.ekho.audio.EqualizerBands
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

// A corrupt file is replaced by defaults (flat EQ, protection on) instead of crashing.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

// Read failures fall back to defaults (flat EQ, protection on) instead of crashing collectors.
private fun DataStore<Preferences>.safeData(): Flow<Preferences> = data.catch { e ->
    if (e is IOException) emit(emptyPreferences()) else throw e
}

/** Persisted app configuration. [bandLevels] are in millibels, one per band. */
data class AppSettings(
    val preset: EqPreset = EqPreset.FLAT,
    val bandLevels: List<Int> = List(EqualizerBands.COUNT) { 0 },
    val bypassLoopbackProtection: Boolean = false
)

class SettingsRepository(private val context: Context) {

    companion object {
        private val PRESET_KEY = stringPreferencesKey("equalizer_preset")
        private val BYPASS_LOOPBACK_PROTECTION_KEY = booleanPreferencesKey("bypass_loopback_protection")
        private fun bandKey(band: Int) = intPreferencesKey("band_$band")
    }

    /** Preset, band levels, and safety settings from one DataStore subscription. */
    val settingsFlow: Flow<AppSettings> = context.dataStore.safeData()
        .map { preferences ->
            AppSettings(
                preset = EqPreset.fromId(preferences[PRESET_KEY]),
                bandLevels = List(EqualizerBands.COUNT) { band ->
                    (preferences[bandKey(band)] ?: 0)
                        .coerceIn(EqualizerBands.MIN_LEVEL_MB, EqualizerBands.MAX_LEVEL_MB)
                },
                bypassLoopbackProtection = preferences[BYPASS_LOOPBACK_PROTECTION_KEY] ?: false
            )
        }
        .distinctUntilChanged()

    val bandLevelsFlow: Flow<List<Int>> = settingsFlow
        .map { it.bandLevels }
        .distinctUntilChanged()

    /** Whether the user switched off feedback-loop protection. Protected (false) by default. */
    val bypassLoopbackProtectionFlow: Flow<Boolean> = settingsFlow
        .map { it.bypassLoopbackProtection }
        .distinctUntilChanged()

    suspend fun saveBypassLoopbackProtection(bypass: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BYPASS_LOOPBACK_PROTECTION_KEY] = bypass
        }
    }

    suspend fun savePreset(preset: EqPreset) {
        context.dataStore.edit { preferences ->
            preferences[PRESET_KEY] = preset.id
            if (preset == EqPreset.CUSTOM) return@edit

            preset.defaultBandLevels.forEachIndexed { band, level ->
                preferences[bandKey(band)] = level
            }
        }
    }

    /** Persists several band levels (band index -> millibels) in a single write. */
    suspend fun saveBandLevels(levels: Map<Int, Int>) {
        if (levels.isEmpty()) return
        context.dataStore.edit { preferences ->
            levels.forEach { (band, level) ->
                if (band in 0 until EqualizerBands.COUNT) {
                    preferences[bandKey(band)] = level
                        .coerceIn(EqualizerBands.MIN_LEVEL_MB, EqualizerBands.MAX_LEVEL_MB)
                }
            }
            preferences[PRESET_KEY] = EqPreset.CUSTOM.id
        }
    }
}
