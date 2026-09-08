package com.analytics.sdk

/**
 * Maps ExoPlayer duration values onto the EPAS wire contract.
 *
 * The specification (player-analytics-specification/specification/README.md) defines
 * `duration` as: "The duration of the content in milliseconds. VOD = length of stream,
 * Live = live edge in UTC. -1 if unknown".
 *
 * ExoPlayer's `Player.getDuration()` returns `C.TIME_UNSET` (`Long.MIN_VALUE + 1`) when the
 * duration is unknown — the normal state for a live stream. Forwarding that raw sentinel on
 * the wire is neither the VOD-zero convention nor the spec's `-1`-unknown convention, so it
 * must be normalized before it enters an event payload.
 *
 * Kept as a pure function (no ExoPlayer types) so it is unit-testable with plain JUnit.
 */
object DurationNormalizer {

    /**
     * ExoPlayer's `androidx.media3.common.C.TIME_UNSET`, inlined here as its documented literal
     * value so this helper carries no dependency on the media3 library and stays trivially
     * unit-testable.
     */
    const val TIME_UNSET: Long = Long.MIN_VALUE + 1

    /** The spec's "unknown duration" sentinel. */
    const val UNKNOWN: Long = -1L

    /**
     * Normalizes a raw ExoPlayer duration to the spec's wire value.
     *
     * Any non-positive or unknown duration (`C.TIME_UNSET`, `0`, or any negative value) maps to
     * the spec's `-1`; a genuine positive duration passes through unchanged.
     */
    fun normalizeDuration(raw: Long): Long =
        if (raw == TIME_UNSET || raw <= 0) UNKNOWN else raw
}
