package com.analytics.sdk

/**
 * Enum class representing different types of ad tracking events for SGAI Ad Impression Tracking.
 * These events correspond to standard VAST/VPAID ad tracking events and custom events.
 */
enum class SGAIAdTrackingEvent(val eventName: String, val description: String) {

    // Core impression and playback events
    LOADED("loaded", "Ad creative has been loaded and is ready to play"),
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

    companion object {
        /**
         * Get tracking event by name
         */
        fun fromEventName(eventName: String): SGAIAdTrackingEvent? {
            return values().find { it.eventName.equals(eventName, ignoreCase = true) }
        }

        /**
         * Get all quartile events
         */
        fun getQuartileEvents(): List<SGAIAdTrackingEvent> {
            return listOf(FIRST_QUARTILE, MIDPOINT, THIRD_QUARTILE)
        }

        /**
         * Get all core playback events
         */
        fun getCoreEvents(): List<SGAIAdTrackingEvent> {
            return listOf(LOADED, IMPRESSION, START, COMPLETE)
        }
    }
}