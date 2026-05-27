package com.baccours.ekho.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baccours.ekho.audio.AudioDeviceMonitor
import com.baccours.ekho.data.SettingsRepository
import com.baccours.ekho.service.ServiceState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val preset: String = SettingsRepository.PRESET_FLAT,
    val bandLevels: Map<Int, Int> = emptyMap(),
    val bypassLoopbackProtection: Boolean = false,
    val isLoopbackSafe: Boolean = true,
    val isServiceRunning: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val audioDeviceMonitor = AudioDeviceMonitor(application)

    val uiState: StateFlow<MainUiState> = combine(
        repository.presetFlow,
        repository.bandLevelsFlow,
        repository.bypassLoopbackProtectionFlow,
        audioDeviceMonitor.loopbackSafeStatusFlow,
        ServiceState.isServiceRunning
    ) { preset, levels, bypass, loopbackSafe, serviceRunning ->
        MainUiState(
            preset = preset,
            bandLevels = levels,
            bypassLoopbackProtection = bypass,
            isLoopbackSafe = loopbackSafe,
            isServiceRunning = serviceRunning
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState(
            isLoopbackSafe = audioDeviceMonitor.isLoopbackSafe(),
            isServiceRunning = ServiceState.isServiceRunning.value
        )
    )

    val bandFrequencies = repository.bandFrequencies
    val bandLevelRange = repository.minBandLevel.toFloat()..repository.maxBandLevel.toFloat()

    fun updateBandLevel(bandId: Int, level: Int) {
        viewModelScope.launch {
            repository.saveBandLevel(bandId, level)
        }
    }

    fun setPreset(presetName: String) {
        viewModelScope.launch {
            repository.savePreset(presetName)
        }
    }

    fun setBypassLoopbackProtection(bypass: Boolean) {
        viewModelScope.launch {
            repository.saveBypassLoopbackProtection(bypass)
        }
    }
}