package com.analytics.sampleplayer

import androidx.media3.exoplayer.ExoPlayer
import com.analytics.sdk.VideoAnalyticsTracker

class AnalyticsManager(
    private val player: ExoPlayer,
    private val eventSinkUrl: String
) {
    private val analyticsTracker: VideoAnalyticsTracker = VideoAnalyticsTracker.Builder(player)
        .setEventSinkUrl(eventSinkUrl)
        .setContentTitle("SGAI Live Stream with Ads")
        .setIsLive(true)
        .setDeviceType("Android")
        .setHeartbeatInterval(30_000L)
        .build()

    fun startTracking() {
        analyticsTracker.startTracking()
    }

    fun stopTracking(reason: String) {
        analyticsTracker.stopTracking(reason)
    }

    fun release() {
        analyticsTracker.release()
    }
}