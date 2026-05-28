package com.baccours.ekho.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ServiceUiState(
    val isRunning: Boolean = false,
    val isStreaming: Boolean = false
)

object ServiceState {
    private val _state = MutableStateFlow(ServiceUiState())
    val state: StateFlow<ServiceUiState> = _state.asStateFlow()

    fun setRunning(running: Boolean) {
        _state.update { it.copy(isRunning = running) }
    }

    fun setStreaming(streaming: Boolean) {
        _state.update { it.copy(isStreaming = streaming) }
    }

    fun reset() {
        _state.update { ServiceUiState() }
    }
}