package com.baccours.ekho.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.baccours.ekho.audio.EqualizerBands
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persisted equalizer configuration. [bandLevels] are in millibels, one per band. */
data class EqSettings(
    val preset: EqPreset,
    val bandLevels: List<Int>
)

class SettingsRepository(private val context: Context) {

    companion object {
        val PRESET_KEY = stringPreferencesKey("equalizer_preset")
        private fun bandKey(band: Int) = intPreferencesKey("band_$band")
    }

    /** Preset and band levels from one DataStore subscription. */
    val settingsFlow: Flow<EqSettings> = context.dataStore.data
        .map { preferences ->
            EqSettings(
                preset = EqPreset.fromId(preferences[PRESET_KEY]),
                bandLevels = List(EqualizerBands.COUNT) { band ->
                    (preferences[bandKey(band)] ?: 0)
                        .coerceIn(EqualizerBands.MIN_LEVEL_MB, EqualizerBands.MAX_LEVEL_MB)
                }
            )
        }
        .distinctUntilChanged()

    val bandLevelsFlow: Flow<List<Int>> = settingsFlow
        .map { it.bandLevels }
        .distinctUntilChanged()

    suspend fun savePreset(preset: EqPreset) {
        context.dataStore.edit { preferences ->
            preferences[PRESET_KEY] = preset.id
            if (preset == EqPreset.CUSTOM) return@edit

            EqualizerBands.FREQUENCIES_HZ.forEachIndexed { band, frequency ->
                preferences[bandKey(band)] = preset.levelFor(frequency)
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
