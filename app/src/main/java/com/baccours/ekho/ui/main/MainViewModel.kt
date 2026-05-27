package com.baccours.ekho.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baccours.ekho.data.SettingsRepository
import com.baccours.ekho.util.AudioDeviceMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val audioDeviceMonitor = AudioDeviceMonitor(application)

    val preset = repository.presetFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.PRESET_FLAT
    )

    val allowSpeaker = repository.allowSpeakerFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    private val _bandLevels = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val bandLevels: StateFlow<Map<Int, Int>> = _bandLevels.asStateFlow()

    val isHeadphoneConnected: StateFlow<Boolean> = audioDeviceMonitor.headphoneStatusFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), audioDeviceMonitor.isHeadphoneConnected())

    val isServiceRunning: StateFlow<Boolean> = com.baccours.ekho.service.ServiceState.isServiceRunning

    init {
        viewModelScope.launch {
            repository.bandLevelsFlow.collect {
                _bandLevels.value = it
            }
        }
    }

    fun updateBandLevel(bandId: Int, level: Int) {
        _bandLevels.value = _bandLevels.value.toMutableMap().apply {
            put(bandId, level)
        }
        viewModelScope.launch {
            repository.saveBandLevel(bandId, level)
        }
    }

    fun setPreset(presetName: String) {
        viewModelScope.launch {
            repository.savePreset(presetName)
        }
    }

    fun setAllowSpeaker(allow: Boolean) {
        viewModelScope.launch {
            repository.saveAllowSpeaker(allow)
        }
    }
}
