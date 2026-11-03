package com.baccours.ekho.audio

import android.media.AudioDeviceInfo

/** Audio streaming interface for decoupling hardware during tests. */
interface AudioEngine {
    /**
     * Streams mic audio to [preferredOutput] (or default route if null) until canceled, stopped, or failed.
     *
     * When [requireSafeRoute] is true, audio stays muted until confirmed safe, returning [StreamResult.RouteUnsafe]
     * on unsafe routes (or if [preferredOutput] is null).
     *
     * [onStreaming] is invoked on the audio thread once audio becomes audible. Keep execution minimal.
     */
    suspend fun start(
        preferredOutput: AudioDeviceInfo?,
        requireSafeRoute: Boolean,
        onStreaming: () -> Unit
    ): StreamResult

    /** Silences output immediately and unblocks the loop. Thread-safe. */
    fun stop()

    /** Updates band gains (mB) in real time. */
    fun setBandLevels(levels: List<Int>)
}
