package com.baccours.ekho.audio

/** Route evaluation state machine for muted streams. Not thread-safe. */
class RouteGuard(
    private val unknownTimeoutMs: Long = UNKNOWN_ROUTE_TIMEOUT_MILLIS,
    private val startupGraceMs: Long = ROUTE_STARTUP_GRACE_MILLIS
) {

    enum class Action { None, Unmute, Cut }

    companion object {
        // Timeout to unmute if route stays unknown.
        const val UNKNOWN_ROUTE_TIMEOUT_MILLIS = 1_500L

        // Startup grace period for pinned device takeover before cutting unsafe routes.
        const val ROUTE_STARTUP_GRACE_MILLIS = 1_500L
    }

    private var sessionStartedAt: Long = 0L
    private var routeConfirmedSafe = false
    private var unknownSince: Long? = null

    fun reset(nowMs: Long = 0L) {
        sessionStartedAt = nowMs
        routeConfirmedSafe = false
        unknownSince = null
    }

    fun onRoute(state: RouteState, nowMs: Long): Action {
        return when (state) {
            RouteState.Safe -> {
                routeConfirmedSafe = true
                unknownSince = null
                Action.Unmute
            }

            RouteState.Unsafe -> {
                val startingUp = !routeConfirmedSafe && (nowMs - sessionStartedAt < startupGraceMs)
                if (startingUp) {
                    Action.None
                } else {
                    Action.Cut
                }
            }

            RouteState.Unknown -> {
                val since = unknownSince ?: nowMs.also { unknownSince = it }
                if (nowMs - since > unknownTimeoutMs) Action.Unmute else Action.None
            }
        }
    }
}
