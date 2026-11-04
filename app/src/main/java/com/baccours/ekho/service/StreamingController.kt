package com.baccours.ekho.service

import com.baccours.ekho.audio.AudioEngine
import com.baccours.ekho.audio.OutputDevices
import com.baccours.ekho.audio.StreamResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

enum class StreamingStatus { Streaming, Waiting, Error }

/** Target output state and safety requirements for streaming. */
data class StreamTarget(
    val allowed: Boolean,
    val outputDeviceId: Int?,
    val requireSafeRoute: Boolean
)

object StreamingPolicy {
    /** Determines streaming eligibility and target parameters based on device availability and bypass state. */
    fun target(bypassProtection: Boolean, safeOutputDeviceId: Int?) = StreamTarget(
        allowed = bypassProtection || safeOutputDeviceId != null,
        outputDeviceId = safeOutputDeviceId,
        requireSafeRoute = !bypassProtection
    )
}

/**
 * Manages [AudioEngine] lifecycle based on device changes, bypass settings, and EQ updates with retry handling.
 */
class StreamingController(
    private val engine: AudioEngine,
    private val devices: OutputDevices,
    private val bypassProtection: Flow<Boolean>,
    private val bandLevels: Flow<List<Int>>,
    private val onStatusChanged: (StreamingStatus) -> Unit,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS
) {
    companion object {
        const val DEFAULT_RETRY_DELAY_MS = 3_000L
        private const val MAX_BACKOFF_SHIFT = 3 // max 8x backoff multiplier for route violations
        private val HEALTHY_SESSION = 10.seconds // minimum duration to reset violation streak
    }

    private var lastStatus: StreamingStatus? = null

    private fun report(status: StreamingStatus) {
        if (status != lastStatus) {
            lastStatus = status
            onStatusChanged(status)
        }
    }

    /** Runs streaming controller until canceled. */
    suspend fun run() = coroutineScope {
        launch {
            bandLevels.collect { engine.setBandLevels(it) }
        }

        combine(bypassProtection, devices.safeOutputDeviceIdFlow) { bypass, safeDeviceId ->
            StreamingPolicy.target(bypass, safeDeviceId)
        }
            .distinctUntilChanged()
            .collectLatest { target -> stream(target) }
    }

    /*
     * Runs streaming loop for current target. Re-verifies target state on each iteration
     * to handle unobserved device events.
     */
    private suspend fun stream(initial: StreamTarget) {
        var target = initial
        var routeViolations = 0
        try {
            while (true) {
                if (target.allowed) {
                    val output = target.outputDeviceId?.let { devices.findOutputDevice(it) }
                    val startedAt = TimeSource.Monotonic.markNow()
                    // Reported by engine once audible.
                    val result = engine.start(
                        output,
                        target.requireSafeRoute,
                        onStreaming = { report(StreamingStatus.Streaming) }
                    )
                    when (result) {
                        StreamResult.Stopped -> {
                            report(StreamingStatus.Waiting)
                            break
                        }

                        StreamResult.Failed -> {
                            // Hardware/route failure: report error and retry.
                            routeViolations = 0
                            report(StreamingStatus.Error)
                        }

                        StreamResult.RouteUnsafe -> {
                            // Cut on unsafe route: back off retry interval and report error.
                            routeViolations =
                                if (startedAt.elapsedNow() > HEALTHY_SESSION) 1 else routeViolations + 1
                            report(StreamingStatus.Error)
                        }
                    }
                } else {
                    report(StreamingStatus.Waiting)
                }

                delay((retryDelayMs * (1L shl (routeViolations - 1).coerceIn(0, MAX_BACKOFF_SHIFT))).milliseconds)
                target = StreamingPolicy.target(
                    bypassProtection.first(),
                    devices.currentSafeOutputDeviceId()
                )
            }
        } finally {
            engine.stop()
        }
    }
}
