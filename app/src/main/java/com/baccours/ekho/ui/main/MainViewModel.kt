package com.baccours.ekho.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.baccours.ekho.EkhoApplication
import com.baccours.ekho.audio.AudioDeviceMonitor
import com.baccours.ekho.audio.EqualizerBands
import com.baccours.ekho.data.AppSettings
import com.baccours.ekho.data.EqPreset
import com.baccours.ekho.data.SettingsRepository
import com.baccours.ekho.service.ServiceState
import com.baccours.ekho.service.ServiceUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isLoaded: Boolean = false, // False only for the placeholder shown before DataStore has been read.
    val settings: AppSettings = AppSettings(),
    val service: ServiceUiState = ServiceUiState(),
    val isLoopbackSafe: Boolean = true
)

class MainViewModel(
    private val repository: SettingsRepository,
    audioDeviceMonitor: AudioDeviceMonitor,
    serviceState: ServiceState
) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EkhoApplication).container
                MainViewModel(
                    container.settingsRepository,
                    container.audioDeviceMonitor,
                    container.serviceState
                )
            }
        }
    }

    /**
     * Band edits that have not been persisted yet. They are shown immediately (so sliders never
     * wait on disk) and written in coalesced batches instead of once per drag tick.
     */
    private val pendingBandLevels = MutableStateFlow<Map<Int, Int>>(emptyMap())

    val uiState: StateFlow<MainUiState> = combine(
        repository.settingsFlow,
        pendingBandLevels,
        audioDeviceMonitor.loopbackSafeStatusFlow,
        serviceState.state
    ) { settings, pending, safe, service ->
        MainUiState(
            isLoaded = true,
            settings = settings.copy(
                bandLevels = settings.bandLevels.mapIndexed { band, level -> pending[band] ?: level }
            ),
            service = service,
            isLoopbackSafe = safe
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState(
            service = serviceState.state.value,
            isLoopbackSafe = audioDeviceMonitor.isLoopbackSafe()
        )
    )

    init {
        // A StateFlow collector that is busy saving only sees the latest map afterward, which is
        // exactly the coalescing we want while a slider is being dragged.
        viewModelScope.launch {
            pendingBandLevels.collect { snapshot ->
                if (snapshot.isEmpty()) return@collect
                repository.saveBandLevels(snapshot)
                pendingBandLevels.update { current ->
                    current.filter { (band, level) -> snapshot[band] != level }
                }
            }
        }
    }

    fun updateBandLevel(bandId: Int, level: Int) {
        val clamped = level.coerceIn(EqualizerBands.MIN_LEVEL_MB, EqualizerBands.MAX_LEVEL_MB)
        pendingBandLevels.update { it + (bandId to clamped) }
    }

    fun setPreset(preset: EqPreset) {
        viewModelScope.launch {
            repository.savePreset(preset)
            // Drop pending edits only once the preset is stored, so sliders never fall back to old values.
            pendingBandLevels.value = emptyMap()
        }
    }

    fun setBypassLoopbackProtection(bypass: Boolean) {
        viewModelScope.launch {
            repository.saveBypassLoopbackProtection(bypass)
        }
    }
}
