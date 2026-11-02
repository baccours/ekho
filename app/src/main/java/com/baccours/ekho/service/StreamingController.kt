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

/**
 * Whether streaming may run, which output device it is pinned to, and whether the actual route
 * must be verified as safe (it must, unless the user bypassed the protection).
 */
data class StreamTarget(
    val allowed: Boolean,
    val outputDeviceId: Int?,
    val requireSafeRoute: Boolean
)

object StreamingPolicy {
    /**
     * The single place that decides if streaming is allowed: it needs a safe output device unless
     * the user explicitly bypassed the protection. A safe device is still preferred when present.
     */
    fun target(bypassProtection: Boolean, safeOutputDeviceId: Int?) = StreamTarget(
        allowed = bypassProtection || safeOutputDeviceId != null,
        outputDeviceId = safeOutputDeviceId,
        requireSafeRoute = !bypassProtection
    )
}

/**
 * Orchestrates streaming independently of Android components: starts/stops the [AudioEngine] as
 * the output devices and the safety bypass change, forwards equalizer levels, and retries after
 * audio failures. The service only hosts it and renders [onStatusChanged].
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
        private const val MAX_BACKOFF_SHIFT = 3 // retries back off up to 8x after route violations
        private val HEALTHY_SESSION = 10.seconds // a session this long is not part of a violation streak
    }

    private var lastStatus: StreamingStatus? = null

    private fun report(status: StreamingStatus) {
        if (status != lastStatus) {
            lastStatus = status
            onStatusChanged(status)
        }
    }

    /** Runs until canceled. */
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

    /**
     * Canceled automatically by [collectLatest] when the target changes or the controller stops;
     * the 'finally' block always runs.
     *
     * Besides reacting to device events, every pass of the loop re-reads the current conditions
     * instead of trusting what it started with, so a missed event can never leave the controller
     * streaming (or retrying) against a device that is gone.
     */
    private suspend fun stream(initial: StreamTarget) {
        var target = initial
        var routeViolations = 0
        try {
            while (true) {
                if (target.allowed) {
                    val output = target.outputDeviceId?.let { devices.findOutputDevice(it) }
                    val startedAt = TimeSource.Monotonic.markNow()
                    // Streaming is reported by the engine once audible, so failed starts never flash it.
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
                            // Audio failed (microphone busy, route lost, ...): surface and retry.
                            routeViolations = 0
                            report(StreamingStatus.Error)
                        }

                        StreamResult.RouteUnsafe -> {
                            // Cut before any audio was audible; back off in case the route
                            // cannot be fixed by retrying. Reported as an error so the user
                            // sees that streaming is not working instead of a silent wait.
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
