package com.baccours.ekho.audio

import com.baccours.ekho.audio.RouteGuard.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteGuardTest {

    private val guard = RouteGuard(unknownTimeoutMs = 1_000, startupGraceMs = 1_500)

    @Test
    fun safeRouteUnmutes() {
        guard.reset(nowMs = 0)
        assertEquals(Action.Unmute, guard.onRoute(RouteState.Safe, nowMs = 0))
    }

    @Test
    fun unsafeRouteDuringStartupGraceIsIgnored() {
        guard.reset(nowMs = 0)
        assertEquals(Action.None, guard.onRoute(RouteState.Unsafe, nowMs = 500))
    }

    @Test
    fun unsafeRouteAfterStartupGraceIsCut() {
        guard.reset(nowMs = 0)
        assertEquals(Action.Cut, guard.onRoute(RouteState.Unsafe, nowMs = 2_000))
    }

    @Test
    fun unsafeRouteAfterConfirmedSafeIsCutEvenDuringStartupGraceWindow() {
        guard.reset(nowMs = 0)
        assertEquals(Action.Unmute, guard.onRoute(RouteState.Safe, nowMs = 100))
        assertEquals(Action.Cut, guard.onRoute(RouteState.Unsafe, nowMs = 200))
    }

    @Test
    fun unknownRouteStaysMutedUntilTheTimeout() {
        guard.reset(nowMs = 0)
        assertEquals(Action.None, guard.onRoute(RouteState.Unknown, nowMs = 100))
        assertEquals(Action.None, guard.onRoute(RouteState.Unknown, nowMs = 1_100))
        assertEquals(Action.Unmute, guard.onRoute(RouteState.Unknown, nowMs = 1_101))
    }

    @Test
    fun aSafeObservationRestartsTheUnknownTimeout() {
        guard.reset(nowMs = 0)
        guard.onRoute(RouteState.Unknown, nowMs = 0)
        guard.onRoute(RouteState.Safe, nowMs = 500)
        assertEquals(Action.None, guard.onRoute(RouteState.Unknown, nowMs = 1_600))
    }

    @Test
    fun resetForgetsEarlierObservations() {
        guard.reset(nowMs = 0)
        guard.onRoute(RouteState.Unknown, nowMs = 0)
        guard.onRoute(RouteState.Safe, nowMs = 10)
        guard.reset(nowMs = 5_000)
        guard.onRoute(RouteState.Unknown, nowMs = 5_000)
        assertEquals(Action.None, guard.onRoute(RouteState.Unknown, nowMs = 5_500))
    }
}
