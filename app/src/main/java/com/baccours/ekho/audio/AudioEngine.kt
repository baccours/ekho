package com.baccours.ekho.audio

import android.media.AudioDeviceInfo

/** Audio streaming interface for decoupling hardware during tests. */
interface AudioEngine {
    /**
     * Streams mic audio to [preferredOutput] (or default route) until ended.
     *
     * If [requireSafeRoute] is true, mutes until safe, returning [StreamResult.RouteUnsafe] on unsafe routes or null output.
     * [onStreaming] runs on the audio thread when audible; keep execution minimal.
     * Sessions must not overlap.
     */
    suspend fun start(
        preferredOutput: AudioDeviceInfo?,
        requireSafeRoute: Boolean,
        onStreaming: () -> Unit
    ): StreamResult

    /** Silences output and unblocks the loop. Thread-safe. */
    fun stop()

    /** Updates band gains (mB). */
    fun setBandLevels(levels: List<Int>)
}
