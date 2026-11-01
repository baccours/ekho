package com.baccours.ekho.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ServiceUiState(
    val isRunning: Boolean = false,
    val isStreaming: Boolean = false,
    /** Audio could not be opened or broke down; the service keeps retrying. */
    val hasError: Boolean = false
)

/** State shared between the service and the UI. One instance lives in the app container. */
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
