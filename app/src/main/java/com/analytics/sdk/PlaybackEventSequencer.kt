package com.analytics.sdk

/**
 * Decides which start-up and playback-state events to send, in EPAS order, from ExoPlayer's two
 * callbacks (`onPlaybackStateChanged` and `onIsPlayingChanged`). ExoPlayer can deliver them in either
 * order, so sending `playing` from both produced `playing` twice and once before `loaded`.
 *
 * Rules: `buffered` closes a `buffering`; `loaded` is sent once, before the first `playing`; `playing` is
 * sent only from `onIsPlayingChanged(true)`, once per transition into playback.
 *
 * Pure (no ExoPlayer types) so the ordering can be unit-tested on the JVM.
 */
class PlaybackEventSequencer {

    enum class Event { BUFFERING, BUFFERED, LOADED, PLAYING, PAUSED }

    private var loadedSent = false
    private var buffering = false
    private var playing = false

    /** Player entered STATE_BUFFERING. */
    fun onBuffering(): List<Event> {
        if (buffering) return emptyList()
        buffering = true
        playing = false
        return listOf(Event.BUFFERING)
    }

    /** Player entered STATE_READY. */
    fun onReady(): List<Event> = closeBufferingAndLoad()

    /**
     * `onIsPlayingChanged`. [ended] / [bufferingState] are the player's current state, used to avoid a
     * `paused` when playback stops because the item ended or rebuffers.
     */
    fun onIsPlayingChanged(isPlaying: Boolean, bufferingState: Boolean, ended: Boolean): List<Event> {
        if (isPlaying) {
            if (playing) return emptyList()
            playing = true
            return closeBufferingAndLoad() + Event.PLAYING
        }
        val wasPlaying = playing
        playing = false
        return if (wasPlaying && !bufferingState && !ended) listOf(Event.PAUSED) else emptyList()
    }

    private fun closeBufferingAndLoad(): List<Event> {
        val out = mutableListOf<Event>()
        if (buffering) {
            out += Event.BUFFERED
            buffering = false
        }
        if (!loadedSent) {
            out += Event.LOADED
            loadedSent = true
        }
        return out
    }
}
