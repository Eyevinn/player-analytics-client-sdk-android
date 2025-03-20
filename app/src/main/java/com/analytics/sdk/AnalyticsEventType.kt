package com.analytics.sdk

/**
 * Enumeration of all possible video analytics event types
 */
enum class AnalyticsEventType(val value: String) {
    INIT("init"),
    METADATA("metadata"),
    HEARTBEAT("heartbeat"),
    LOADING("loading"),
    LOADED("loaded"),
    PLAYING("playing"),
    PAUSED("paused"),
    BUFFERING("buffering"),
    BUFFERED("buffered"),
    SEEKING("seeking"),
    SEEKED("seeked"),
    BITRATE_CHANGED("bitrate_changed"),
    STOPPED("stopped"),
    ERROR("error")
}