package com.analytics.sdk

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [DurationNormalizer].
 *
 * These run as plain JVM JUnit tests without an Android/ExoPlayer runtime because the helper is
 * pure. [DurationNormalizer.TIME_UNSET] mirrors ExoPlayer's `androidx.media3.common.C.TIME_UNSET`
 * (`Long.MIN_VALUE + 1`), the value a live-window `ExoPlayer` reports for an unknown duration.
 */
class DurationNormalizerTest {

    @Test
    fun `time unset sentinel maps to spec unknown minus one`() {
        // Live-window fixture: ExoPlayer.getDuration() == C.TIME_UNSET for unknown duration.
        val raw = DurationNormalizer.TIME_UNSET
        assertEquals(Long.MIN_VALUE + 1, raw)

        val sent = DurationNormalizer.normalizeDuration(raw)

        assertEquals(-1L, sent)
        // Regression guard: must NOT forward the raw sentinel on the wire.
        assertEquals(false, sent == raw)
    }

    @Test
    fun `positive duration passes through unchanged`() {
        assertEquals(1L, DurationNormalizer.normalizeDuration(1L))
        assertEquals(120_000L, DurationNormalizer.normalizeDuration(120_000L))
        assertEquals(Long.MAX_VALUE, DurationNormalizer.normalizeDuration(Long.MAX_VALUE))
    }

    @Test
    fun `non-positive durations map to spec unknown minus one`() {
        assertEquals(-1L, DurationNormalizer.normalizeDuration(0L))
        assertEquals(-1L, DurationNormalizer.normalizeDuration(-1L))
        assertEquals(-1L, DurationNormalizer.normalizeDuration(-42L))
        assertEquals(-1L, DurationNormalizer.normalizeDuration(Long.MIN_VALUE))
    }
}
