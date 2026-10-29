package com.analytics.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the EPAS seek reporting from issue #31: `seeking` carries the pre-seek position, `seeked`
 * is emitted on STATE_READY, buffering is suppressed while seeking, and scrubbing collapses to one
 * `seeking`/`seeked` pair. Acceptance criteria 1-3.
 */
class SeekEventTrackerTest {

    @Test
    fun `no seek in progress initially`() {
        val t = SeekEventTracker()
        assertFalse(t.isSeeking)
        // STATE_READY without a seek is handled normally (no `seeked`).
        assertFalse(t.onReady())
    }

    // AC#1: a seek during playback -> `seeking` (pre-seek position), then `seeked` (new position),
    // with buffering suppressed in between.
    @Test
    fun `seek during playback emits seeking with old position then seeked, buffering suppressed`() {
        val t = SeekEventTracker()
        val oldPosition = 13_523L

        // `seeking` is sent with the position *before* the seek, not the target.
        assertEquals(oldPosition, t.onSeek(oldPosition))

        // Between `seeking` and `seeked` the player buffers; that buffering is part of the seek.
        assertTrue("buffering must be suppressed while seeking", t.isSeeking)

        // STATE_READY ends the seek -> caller sends `seeked` with the new position.
        assertTrue(t.onReady())
        assertFalse(t.isSeeking)
    }

    // AC#2: a seek while paused still produces `seeking`/`seeked`; the state machine is identical,
    // the absence of `playing` is the caller's (no onIsPlayingChanged(true) fires while paused).
    @Test
    fun `seek while paused still emits seeking and seeked`() {
        val t = SeekEventTracker()
        assertEquals(35_049L, t.onSeek(35_049L))
        assertTrue(t.isSeeking)
        assertTrue(t.onReady())
        assertFalse(t.isSeeking)
    }

    // AC#3: repeated seeks before ready (scrubbing) -> one `seeking` (first old position), one
    // `seeked` (final position).
    @Test
    fun `scrubbing collapses to a single seeking and seeked pair`() {
        val t = SeekEventTracker()
        val firstOldPosition = 10_000L

        // First seek emits `seeking` with the first old position.
        assertEquals(firstOldPosition, t.onSeek(firstOldPosition))

        // Further seeks before the player is ready emit no second `seeking`.
        assertNull(t.onSeek(20_000L))
        assertNull(t.onSeek(30_000L))
        assertTrue(t.isSeeking)

        // One STATE_READY closes the whole scrub with a single `seeked`.
        assertTrue(t.onReady())
        assertFalse(t.isSeeking)
    }

    @Test
    fun `ready after the seek has completed does not emit a second seeked`() {
        val t = SeekEventTracker()
        t.onSeek(5_000L)
        assertTrue(t.onReady())
        // A later STATE_READY (e.g. a rebuffer) is not a seek completion.
        assertFalse(t.onReady())
    }

    @Test
    fun `a second independent seek emits its own pair`() {
        val t = SeekEventTracker()
        assertEquals(1_000L, t.onSeek(1_000L))
        assertTrue(t.onReady())

        // New seek, new pair.
        assertEquals(2_000L, t.onSeek(2_000L))
        assertTrue(t.isSeeking)
        assertTrue(t.onReady())
    }
}
