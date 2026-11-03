package com.baccours.ekho.audio

import android.media.AudioDeviceInfo

/** What the streaming logic needs from the audio layer; lets it be tested without hardware. */
interface AudioEngine {
    /**
     * Streams microphone audio to [preferredOutput] (or the default route when null) until the
     * calling coroutine is canceled, [stop] is called, or audio fails.
     *
     * With [requireSafeRoute] the output stays muted until the stream is confirmed to be routed to
     * a safe device, and the stream ends with [StreamResult.RouteUnsafe] if it is ever routed
     * elsewhere (e.g. the loudspeaker after an unplug). A null [preferredOutput] with
     * [requireSafeRoute] ends immediately with [StreamResult.RouteUnsafe].
     *
     * [onStreaming] is called once per call, from the audio thread, when audio becomes audible
     * (after route confirmation if [requireSafeRoute]). Keep it cheap.
     */
    suspend fun start(
        preferredOutput: AudioDeviceInfo?,
        requireSafeRoute: Boolean,
        onStreaming: () -> Unit
    ): StreamResult

    /** Silences the output immediately and unblocks the audio loop. Safe from any thread. */
    fun stop()

    /** Updates the band gains (millibels). Takes effect immediately, also while streaming. */
    fun setBandLevels(levels: List<Int>)
}
