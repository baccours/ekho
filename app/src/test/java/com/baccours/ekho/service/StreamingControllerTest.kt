package com.baccours.ekho.service

import android.media.AudioDeviceInfo
import com.baccours.ekho.audio.AudioEngine
import com.baccours.ekho.audio.OutputDevices
import com.baccours.ekho.audio.StreamResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class StreamingControllerTest {

    private class FakeEngine(vararg scripted: StreamResult) : AudioEngine {
        private val script = ConcurrentLinkedQueue(scripted.toList())
        val starts = AtomicInteger(0)
        @Volatile var lastRequireSafeRoute: Boolean? = null
        val stops = AtomicInteger(0)
        val levels = CopyOnWriteArrayList<List<Int>>()

        // Returns the next scripted result, then streams until canceled.
        override suspend fun start(preferredOutput: AudioDeviceInfo?, requireSafeRoute: Boolean): StreamResult {
            lastRequireSafeRoute = requireSafeRoute
            starts.incrementAndGet()
            return script.poll() ?: awaitCancellation()
        }

        override fun stop() {
            stops.incrementAndGet()
        }

        override fun setBandLevels(levels: List<Int>) {
            this.levels.add(levels)
        }
    }

    private class FakeDevices(initialSafeDeviceId: Int?) : OutputDevices {
        private val reported = MutableStateFlow(initialSafeDeviceId)

        /** What a direct query returns; can differ from the events to simulate a missed event. */
        @Volatile var actualSafeDeviceId: Int? = initialSafeDeviceId

        override val safeOutputDeviceIdFlow: Flow<Int?> = reported
        override fun findOutputDevice(id: Int): AudioDeviceInfo? = null
        override fun currentSafeOutputDeviceId(): Int? = actualSafeDeviceId

        /** A normal change: the event is delivered and the state matches. */
        fun setSafeDevice(id: Int?) {
            actualSafeDeviceId = id
            reported.value = id
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val statuses = Channel<StreamingStatus>(Channel.UNLIMITED)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun launchController(
        engine: FakeEngine,
        devices: FakeDevices,
        bypass: MutableStateFlow<Boolean> = MutableStateFlow(false),
        levels: Flow<List<Int>> = MutableStateFlow(emptyList())
    ) {
        val controller = StreamingController(
            engine = engine,
            devices = devices,
            bypassProtection = bypass,
            bandLevels = levels,
            onStatusChanged = { statuses.trySend(it) },
            retryDelayMs = 10
        )
        scope.launch { controller.run() }
    }

    private suspend fun nextStatus(): StreamingStatus = withTimeout(2_000.milliseconds) { statuses.receive() }

    private suspend fun awaitUntil(condition: () -> Boolean) = withTimeout(2_000.milliseconds) {
        while (!condition()) delay(5.milliseconds)
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
    fun waitsWithoutSafeOutput() = runBlocking {
        val engine = FakeEngine()
        launchController(engine, FakeDevices(null))

        assertEquals(StreamingStatus.Waiting, nextStatus())
        delay(100.milliseconds)
        assertEquals(0, engine.starts.get())
    }

    @Test
    fun streamsWhenSafeOutputIsConnected() = runBlocking {
        val engine = FakeEngine()
        launchController(engine, FakeDevices(7))

        assertEquals(StreamingStatus.Streaming, nextStatus())
        awaitUntil { engine.starts.get() == 1 }
    }

    @Test
    fun bypassAllowsStreamingWithoutSafeOutput() = runBlocking {
        val engine = FakeEngine()
        launchController(engine, FakeDevices(null), bypass = MutableStateFlow(true))

        assertEquals(StreamingStatus.Streaming, nextStatus())
        awaitUntil { engine.starts.get() == 1 }
    }

    @Test
    fun unpluggingStopsStreaming() = runBlocking {
        val engine = FakeEngine()
        val devices = FakeDevices(7)
        launchController(engine, devices)
        assertEquals(StreamingStatus.Streaming, nextStatus())
        awaitUntil { engine.starts.get() == 1 }

        devices.setSafeDevice(null)

        assertEquals(StreamingStatus.Waiting, nextStatus())
        awaitUntil { engine.stops.get() >= 1 }
    }

    @Test
    fun switchingOutputDeviceRestartsTheStream() = runBlocking {
        val engine = FakeEngine()
        val devices = FakeDevices(7)
        launchController(engine, devices)
        assertEquals(StreamingStatus.Streaming, nextStatus())
        awaitUntil { engine.starts.get() == 1 }

        devices.setSafeDevice(9)

        awaitUntil { engine.starts.get() == 2 }
    }

    @Test
    fun failuresAreSurfacedAndRetried() = runBlocking {
        val engine = FakeEngine(StreamResult.Failed)
        launchController(engine, FakeDevices(7))

        assertEquals(StreamingStatus.Streaming, nextStatus())
        assertEquals(StreamingStatus.Error, nextStatus())
        assertEquals(StreamingStatus.Streaming, nextStatus())
        awaitUntil { engine.starts.get() == 2 }
    }

    @Test
    fun bandLevelsAreForwardedToTheEngine() = runBlocking {
        val engine = FakeEngine()
        val levels = MutableStateFlow(listOf(100, 0, 0, 0, 0))
        launchController(engine, FakeDevices(null), levels = levels)

        awaitUntil { engine.levels.contains(listOf(100, 0, 0, 0, 0)) }
        levels.value = listOf(0, 200, 0, 0, 0)
        awaitUntil { engine.levels.contains(listOf(0, 200, 0, 0, 0)) }
    }

    @Test
    fun routeIsVerifiedWhenProtectionIsActive() = runBlocking {
        val engine = FakeEngine()
        launchController(engine, FakeDevices(7))

        awaitUntil { engine.starts.get() == 1 }
        assertEquals(true, engine.lastRequireSafeRoute)
    }

    @Test
    fun routeIsNotVerifiedWhenProtectionIsBypassed() = runBlocking {
        val engine = FakeEngine()
        launchController(engine, FakeDevices(7), bypass = MutableStateFlow(true))

        awaitUntil { engine.starts.get() == 1 }
        assertEquals(false, engine.lastRequireSafeRoute)
    }

    @Test
    fun unsafeRouteIsCutAndRetried() = runBlocking {
        val engine = FakeEngine(StreamResult.RouteUnsafe)
        launchController(engine, FakeDevices(7))

        assertEquals(StreamingStatus.Streaming, nextStatus())
        assertEquals(StreamingStatus.Waiting, nextStatus())
        assertEquals(StreamingStatus.Streaming, nextStatus())
        awaitUntil { engine.starts.get() == 2 }
    }

    @Test
    fun retryDoesNotTrustAStaleDeviceAfterAMissedEvent() = runBlocking {
        val engine = FakeEngine(StreamResult.Failed)
        val devices = FakeDevices(7)
        devices.actualSafeDeviceId = null // the unplug event never reached the controller

        launchController(engine, devices)

        assertEquals(StreamingStatus.Streaming, nextStatus())
        assertEquals(StreamingStatus.Error, nextStatus())
        assertEquals(StreamingStatus.Waiting, nextStatus())
        delay(100.milliseconds)
        assertEquals(1, engine.starts.get())
    }

    @Test
    fun retryResumesWhenTheDeviceReturnsWithoutAnEvent() = runBlocking {
        val engine = FakeEngine(StreamResult.Failed)
        val devices = FakeDevices(7)
        devices.actualSafeDeviceId = null
        launchController(engine, devices)
        assertEquals(StreamingStatus.Streaming, nextStatus())
        assertEquals(StreamingStatus.Error, nextStatus())
        assertEquals(StreamingStatus.Waiting, nextStatus())

        devices.actualSafeDeviceId = 7

        assertEquals(StreamingStatus.Streaming, nextStatus())
        awaitUntil { engine.starts.get() == 2 }
    }
}
