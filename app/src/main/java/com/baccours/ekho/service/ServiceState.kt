package com.baccours.ekho.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ServiceUiState(
    val isRunning: Boolean = false,
    val isStreaming: Boolean = false,
    /** Indicates streaming failure; retries are active. */
    val hasError: Boolean = false
)

/** Shared state between AudioService and UI. */
class ServiceState {
    private val _state = MutableStateFlow(ServiceUiState())
    val state: StateFlow<ServiceUiState> = _state.asStateFlow()

    fun setRunning(running: Boolean) {
        _state.update { it.copy(isRunning = running) }
    }

    fun setStreamingState(isStreaming: Boolean, hasError: Boolean) {
        _state.update { it.copy(isStreaming = isStreaming, hasError = hasError) }
    }

    fun reset() {
        _state.value = ServiceUiState()
    }
}
