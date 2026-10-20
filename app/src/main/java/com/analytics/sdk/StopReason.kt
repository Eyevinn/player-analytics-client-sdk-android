package com.analytics.sdk

/**
 * Why a session ended, sent as `stopped.payload.reason`.
 *
 * EPAS (`typescript/event/stopped.d.ts`): `"ended" | "aborted" | "error" | "unknown"`. Using the spec
 * values (instead of free text) lets stop reasons be grouped across the web, iOS and Android SDKs.
 */
enum class StopReason(val value: String) {
    /** The item played to the end. */
    ENDED("ended"),

    /** Playback was stopped or torn down before the end (the viewer or the integrator stopped it). */
    ABORTED("aborted"),

    /** A fatal playback error ended the session. */
    ERROR("error"),

    /** The reason is not known. */
    UNKNOWN("unknown");

    companion object {
        /**
         * Maps a free-text reason from the deprecated `stopTracking(String)` API onto a spec value.
         * A spec value (any case) is kept; anything else is an integrator stopping playback, so
         * [ABORTED].
         */
        fun fromLegacyText(text: String?): StopReason =
            values().firstOrNull { it.value.equals(text?.trim(), ignoreCase = true) } ?: ABORTED
    }
}

/**
 * Lets exactly one `stopped` event through per session. EPAS: after `stopped` the session is over, so a
 * second `stopped` (e.g. end of playback followed by leaving the app) is noise.
 */
class StoppedOnce {
    @Volatile
    private var sent = false

    /** Returns true the first time it is called, false afterwards. */
    @Synchronized
    fun tryMark(): Boolean {
        if (sent) return false
        sent = true
        return true
    }

    val isSent: Boolean get() = sent
}
