package com.analytics.sdk

/**
 * Enum class representing different types of ad tracking events for SGAI Ad Impression Tracking.
 * These events correspond to standard VAST/VPAID ad tracking events and custom events.
 */
enum class SGAIAdTrackingEvent(val eventName: String, val description: String) {

    // Core impression and playback events
    IMPRESSION("impression", "Ad creative has been displayed/rendered"),
    START("start", "Ad playback has started"),

    // Quartile events for video ads
    FIRST_QUARTILE("firstQuartile", "25% of the ad duration has been played"),
    MIDPOINT("midpoint", "50% of the ad duration has been played"),
    THIRD_QUARTILE("thirdQuartile", "75% of the ad duration has been played"),
    COMPLETE("complete", "Ad playback has completed successfully"),


    PAUSE("pause", "Ad playback has been paused"),
    RESUME("resume", "Ad playback has been resumed"),

    // Ad pod events
    POD_START("podStart", "Ad pod/break has started"),
    POD_END("podEnd", "Ad pod/break has Ended");
}

// Utility: normalize event names
fun normalizeEventName(eventName: String): String {
    return when (eventName.lowercase()) {
        "podstart", "pod_start" -> "podStart"
        "podend", "pod_end" -> "podEnd"
        "firstquartile", "first_quartile" -> "firstQuartile"
        "midpoint", "mid_point" -> "midpoint"
        "thirdquartile", "third_quartile" -> "thirdQuartile"
        "complete", "end" -> "complete"
        "resume" -> "resume"
        "pause" -> "pause"
        "impression" -> "impression"
        else -> eventName.lowercase()
    }
}

fun mapEventName(eventType: String): String {
    return when (eventType.lowercase()) {
        "impression" -> "start"    // Fallback: map impression to start
        "firstquartile" -> "start"
        "thirdquartile" -> "complete"
        else -> eventType
    }
}