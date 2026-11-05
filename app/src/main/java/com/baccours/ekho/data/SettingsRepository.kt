package com.baccours.ekho.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
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
import timber.log.Timber
import java.io.IOException

// Replaces corrupt DataStore file with defaults instead of crashing.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

// Falls back to empty preferences on read IO error.
private fun DataStore<Preferences>.safeData(): Flow<Preferences> = data.catch { e ->
    if (e is IOException) emit(emptyPreferences()) else throw e
}

/** Persisted app configuration. [bandLevels] are in mB. */
data class AppSettings(
    val preset: EqPreset = EqPreset.FLAT,
    val bandLevels: List<Int> = List(EqualizerBands.COUNT) { 0 },
    val bypassFeedbackProtection: Boolean = false
)

class SettingsRepository(private val context: Context) {

    companion object {
        private val PRESET_KEY = stringPreferencesKey("equalizer_preset")
        // Persisted key name.
        private val BYPASS_FEEDBACK_PROTECTION_KEY = booleanPreferencesKey("bypass_feedback_protection")
        private fun bandKey(band: Int) = intPreferencesKey("band_$band")
    }

    /** App settings flow from DataStore. */
    val settingsFlow: Flow<AppSettings> = context.dataStore.safeData()
        .map { preferences ->
            AppSettings(
                preset = EqPreset.fromId(preferences[PRESET_KEY]),
                bandLevels = List(EqualizerBands.COUNT) { band ->
                    (preferences[bandKey(band)] ?: 0)
                        .coerceIn(EqualizerBands.MIN_LEVEL_MB, EqualizerBands.MAX_LEVEL_MB)
                },
                bypassFeedbackProtection = preferences[BYPASS_FEEDBACK_PROTECTION_KEY] ?: false
            )
        }
        .distinctUntilChanged()

    val bandLevelsFlow: Flow<List<Int>> = settingsFlow
        .map { it.bandLevels }
        .distinctUntilChanged()

    /** Feedback protection bypass state flow. */
    val bypassFeedbackProtectionFlow: Flow<Boolean> = settingsFlow
        .map { it.bypassFeedbackProtection }
        .distinctUntilChanged()

    // Logs and swallows write failures (e.g. full disk).
    private suspend fun write(transform: suspend (MutablePreferences) -> Unit) {
        try {
            context.dataStore.edit(transform)
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist settings")
        }
    }

    suspend fun saveBypassFeedbackProtection(bypass: Boolean) = write { preferences ->
        preferences[BYPASS_FEEDBACK_PROTECTION_KEY] = bypass
    }

    suspend fun savePreset(preset: EqPreset) = write { preferences ->
        preferences[PRESET_KEY] = preset.id
        // Preserve user levels for CUSTOM.
        preset.defaultBandLevels?.forEachIndexed { band, level ->
            preferences[bandKey(band)] = level
        }
    }

    /** Persists band levels (band index -> mB) and sets preset to CUSTOM. */
    suspend fun saveBandLevels(levels: Map<Int, Int>) {
        if (levels.isEmpty()) return
        write { preferences ->
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
