package com.baccours.ekho.service

import android.media.AudioDeviceInfo
import com.baccours.ekho.audio.AudioEngine
import com.baccours.ekho.audio.OutputDevices
import com.baccours.ekho.audio.StreamResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Unit tests for [StreamingController] using virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamingControllerTest {

    private companion object {
        const val RETRY_MS = 10L
    }

    private class FakeEngine(vararg scripted: StreamResult) : AudioEngine {
        private val script = ArrayDeque(scripted.toList())
        var starts = 0
        var stops = 0
        var lastRequireSafeRoute: Boolean? = null
        val levels = mutableListOf<List<Int>>()

        // Returns scripted result, then streams until canceled.
        override suspend fun start(
            preferredOutput: AudioDeviceInfo?,
            requireSafeRoute: Boolean,
            onStreaming: () -> Unit
        ): StreamResult {
            lastRequireSafeRoute = requireSafeRoute
            starts++
            script.removeFirstOrNull()?.let { return it }
            onStreaming()
            awaitCancellation()
        }

        override fun stop() {
            stops++
        }

        override fun setBandLevels(levels: List<Int>) {
            this.levels.add(levels)
        }
    }

    private class FakeDevices(initialSafeDeviceId: Int?) : OutputDevices {
        private val reported = MutableStateFlow(initialSafeDeviceId)

        /** Direct query result; allows simulating missed device callbacks. */
        var actualSafeDeviceId: Int? = initialSafeDeviceId

        override val safeOutputDeviceIdFlow: Flow<Int?> = reported
        override fun findOutputDevice(id: Int): AudioDeviceInfo? = null
        override fun currentSafeOutputDeviceId(): Int? = actualSafeDeviceId

        /** Emits device change event and updates direct query snapshot. */
        fun setSafeDevice(id: Int?) {
            actualSafeDeviceId = id
            reported.value = id
        }
    }

    /** Launches controller and collects status transitions. */
    private fun TestScope.launchController(
        engine: FakeEngine,
        devices: FakeDevices,
        bypass: MutableStateFlow<Boolean> = MutableStateFlow(false),
        levels: Flow<List<Int>> = MutableStateFlow(emptyList())
    ): List<StreamingStatus> {
        val statuses = mutableListOf<StreamingStatus>()
        val controller = StreamingController(
            engine = engine,
            devices = devices,
            bypassProtection = bypass,
            bandLevels = levels,
            onStatusChanged = { statuses += it },
            retryDelayMs = RETRY_MS
        )
        backgroundScope.launch { controller.run() }
        runCurrent()
        return statuses
    }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms.milliseconds)
        runCurrent()
    }

    @Test
    fun policyRequiresSafeOutputUnlessBypassed() {
        assertFalse(StreamingPolicy.target(bypassProtection = false, safeOutputDeviceId = null).allowed)
        assertTrue(StreamingPolicy.target(bypassProtection = true, safeOutputDeviceId = null).allowed)
        assertTrue(StreamingPolicy.target(bypassProtection = false, safeOutputDeviceId = 7).allowed)
    }

    @Test
    fun policyKeepsSafeDeviceEvenWhenBypassed() {
        assertEquals(7, StreamingPolicy.target(bypassProtection = true, safeOutputDeviceId = 7).outputDeviceId)
        assertNull(StreamingPolicy.target(bypassProtection = true, safeOutputDeviceId = null).outputDeviceId)
    }

    @Test
    fun waitsWithoutSafeOutput() = runTest {
        val engine = FakeEngine()
        val statuses = launchController(engine, FakeDevices(null))

        assertEquals(listOf(StreamingStatus.Waiting), statuses)
        advance(100)
        assertEquals(0, engine.starts)
    }

    @Test
    fun streamsWhenSafeOutputIsConnected() = runTest {
        val engine = FakeEngine()
        val statuses = launchController(engine, FakeDevices(7))

        assertEquals(listOf(StreamingStatus.Streaming), statuses)
        assertEquals(1, engine.starts)
    }

    @Test
    fun bypassAllowsStreamingWithoutSafeOutput() = runTest {
        val engine = FakeEngine()
        val statuses = launchController(engine, FakeDevices(null), bypass = MutableStateFlow(true))

        assertEquals(listOf(StreamingStatus.Streaming), statuses)
        assertEquals(1, engine.starts)
    }

    @Test
    fun unpluggingStopsStreaming() = runTest {
        val engine = FakeEngine()
        val devices = FakeDevices(7)
        val statuses = launchController(engine, devices)

        devices.setSafeDevice(null)
        runCurrent()

        assertEquals(listOf(StreamingStatus.Streaming, StreamingStatus.Waiting), statuses)
        assertTrue(engine.stops >= 1)
    }

    @Test
    fun switchingOutputDeviceRestartsTheStream() = runTest {
        val engine = FakeEngine()
        val devices = FakeDevices(7)
        launchController(engine, devices)

        devices.setSafeDevice(9)
        runCurrent()

        assertEquals(2, engine.starts)
    }

    @Test
    fun failuresAreSurfacedAndRetried() = runTest {
        val engine = FakeEngine(StreamResult.Failed)
        val statuses = launchController(engine, FakeDevices(7))
        assertEquals(listOf(StreamingStatus.Error), statuses)

        advance(RETRY_MS)

        assertEquals(listOf(StreamingStatus.Error, StreamingStatus.Streaming), statuses)
        assertEquals(2, engine.starts)
    }

    @Test
    fun unexpectedStopIsRetried() = runTest {
        val engine = FakeEngine(StreamResult.Stopped)
        val statuses = launchController(engine, FakeDevices(7))
        assertEquals(listOf(StreamingStatus.Waiting), statuses)

        advance(RETRY_MS)

        assertEquals(listOf(StreamingStatus.Waiting, StreamingStatus.Streaming), statuses)
        assertEquals(2, engine.starts)
    }

    @Test
    fun persistentFailuresDoNotFlapTheStatus() = runTest {
        val engine = FakeEngine(StreamResult.Failed, StreamResult.Failed, StreamResult.Failed)
        val statuses = launchController(engine, FakeDevices(7))

        advance(2 * RETRY_MS)

        assertEquals(3, engine.starts)
        assertEquals(listOf(StreamingStatus.Error), statuses)
    }

    @Test
    fun bandLevelsAreForwardedToTheEngine() = runTest {
        val engine = FakeEngine()
        val levels = MutableStateFlow(listOf(100, 0, 0, 0, 0))
        launchController(engine, FakeDevices(null), levels = levels)
        assertTrue(engine.levels.contains(listOf(100, 0, 0, 0, 0)))

        levels.value = listOf(0, 200, 0, 0, 0)
        runCurrent()

        assertTrue(engine.levels.contains(listOf(0, 200, 0, 0, 0)))
    }

    @Test
    fun routeIsVerifiedWhenProtectionIsActive() = runTest {
        val engine = FakeEngine()
        launchController(engine, FakeDevices(7))

        assertEquals(true, engine.lastRequireSafeRoute)
    }

    @Test
    fun routeIsNotVerifiedWhenProtectionIsBypassed() = runTest {
        val engine = FakeEngine()
        launchController(engine, FakeDevices(7), bypass = MutableStateFlow(true))

        assertEquals(false, engine.lastRequireSafeRoute)
    }

    @Test
    fun unsafeRouteIsCutAndRetried() = runTest {
        val engine = FakeEngine(StreamResult.RouteUnsafe)
        val statuses = launchController(engine, FakeDevices(7))
        assertEquals(listOf(StreamingStatus.Error), statuses)

        advance(RETRY_MS)

        assertEquals(listOf(StreamingStatus.Error, StreamingStatus.Streaming), statuses)
        assertEquals(2, engine.starts)
    }

    @Test
    fun repeatedRouteViolationsBackOff() = runTest {
        val engine = FakeEngine(StreamResult.RouteUnsafe, StreamResult.RouteUnsafe)
        launchController(engine, FakeDevices(7))

        advance(RETRY_MS) // first retry after 1x
        assertEquals(2, engine.starts)
        advance(2 * RETRY_MS - 1) // second retry uses 2x backoff
        assertEquals(2, engine.starts)
        advance(1)
        assertEquals(3, engine.starts)
    }

    @Test
    fun retryDoesNotTrustAStaleDeviceAfterAMissedEvent() = runTest {
        val engine = FakeEngine(StreamResult.Failed)
        val devices = FakeDevices(7)
        devices.actualSafeDeviceId = null // simulate missed unplug event
        val statuses = launchController(engine, devices)

        advance(RETRY_MS)

        assertEquals(listOf(StreamingStatus.Error, StreamingStatus.Waiting), statuses)
        advance(100)
        assertEquals(1, engine.starts)
    }

    @Test
    fun retryResumesWhenTheDeviceReturnsWithoutAnEvent() = runTest {
        val engine = FakeEngine(StreamResult.Failed)
        val devices = FakeDevices(7)
        devices.actualSafeDeviceId = null
        val statuses = launchController(engine, devices)
        advance(RETRY_MS)
        assertEquals(StreamingStatus.Waiting, statuses.last())

        devices.actualSafeDeviceId = 7
        advance(RETRY_MS)

        assertEquals(StreamingStatus.Streaming, statuses.last())
        assertEquals(2, engine.starts)
    }
}
