package com.baccours.ekho.audio

/**
 * Decides what to do with a muted stream as route observations arrive. Pure logic: not
 * thread-safe, so the caller serializes calls.
 */
class RouteGuard(
    private val unknownTimeoutMs: Long = UNKNOWN_ROUTE_TIMEOUT_MILLIS,
    private val startupGraceMs: Long = ROUTE_STARTUP_GRACE_MILLIS
) {

    enum class Action { None, Unmute, Cut }

    companion object {
        // If the platform never reports a route, stop waiting for confirmation and rely on the
        // device pinning and the device callbacks instead of staying silent forever.
        const val UNKNOWN_ROUTE_TIMEOUT_MILLIS = 1_500L

        // While a stream is starting, the platform may briefly report a stale route (such as the
        // loudspeaker) before the pinned device takes over. The output is muted meanwhile, so an
        // unsafe reading is only acted on once this has passed or the route was confirmed safe.
        const val ROUTE_STARTUP_GRACE_MILLIS = 1_500L
    }

    /** True while the last observation was a safe route. Readable from any thread. */
    @Volatile
    var isSafe = false
        private set

    private var sessionStartedAt: Long = 0L
    private var routeConfirmedSafe = false
    private var unknownSince: Long? = null

    fun reset(nowMs: Long = 0L) {
        isSafe = false
        sessionStartedAt = nowMs
        routeConfirmedSafe = false
        unknownSince = null
    }

    fun onRoute(state: RouteState, nowMs: Long): Action {
        return when (state) {
            RouteState.Safe -> {
                isSafe = true
                routeConfirmedSafe = true
                unknownSince = null
                Action.Unmute
            }

            RouteState.Unsafe -> {
                isSafe = false
                val startingUp = !routeConfirmedSafe && (nowMs - sessionStartedAt < startupGraceMs)
                if (startingUp) {
                    Action.None
                } else {
                    Action.Cut
                }
            }

            RouteState.Unknown -> {
                isSafe = false
                val since = unknownSince ?: nowMs.also { unknownSince = it }
                if (nowMs - since > unknownTimeoutMs) Action.Unmute else Action.None
            }
        }
    }
}
