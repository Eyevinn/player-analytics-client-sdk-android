package com.analytics.sdk

/**
 * Tracks an in-progress seek so the EPAS `seeking`/`seeked` pair is reported correctly and the
 * buffering a seek causes is not mis-reported.
 *
 * EPAS (`player-analytics-specification` `specification/README.md`):
 * - `seeking` MUST carry the playhead *before* the seek (the old position), not the target.
 * - `seeked` is sent once the player has finished seeking and is ready again, carrying the new
 *   (post-seek) position; `playing` must not trigger until after `seeked`.
 * - buffering that happens because of a seek is ignored (no `buffering`/`buffered`).
 *
 * Repeated seeks before the player becomes ready (scrubbing) collapse to a single
 * `seeking`/`seeked` pair: the first old position and the final new position.
 *
 * Pure (no ExoPlayer types) so the behaviour can be unit-tested on the JVM, like
 * [PlaybackEventSequencer].
 */
class SeekEventTracker {

    private var seeking = false

    /**
     * True between a seek discontinuity and the following STATE_READY. While true the caller
     * suppresses `buffering`/`buffered`, because that buffering belongs to the seek.
     */
    val isSeeking: Boolean get() = seeking

    /**
     * A seek discontinuity arrived; [oldPositionMs] is the playhead *before* the seek. Returns the
     * playhead to send `seeking` with, or `null` if a seek is already in progress (scrubbing: keep
     * the first old position and emit no second `seeking`).
     */
    fun onSeek(oldPositionMs: Long): Long? {
        if (seeking) return null
        seeking = true
        return oldPositionMs
    }

    /**
     * The player reached STATE_READY. Returns `true` if a seek was in progress — the caller should
     * send `seeked` with the current (new) position — and clears the in-progress flag. Returns
     * `false` when no seek is in progress, in which case STATE_READY is handled normally.
     */
    fun onReady(): Boolean {
        if (!seeking) return false
        seeking = false
        return true
    }
}
