package com.analytics.sdk

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import org.json.JSONObject

/**
 * Main tracking class that automatically monitors an ExoPlayer instance
 * and sends appropriate analytics events
 */
class VideoAnalyticsTracker private constructor(
    private val player: ExoPlayer,
    private val eventSender: AnalyticsEventSender,
    private val config: Configuration
) {
    private val TAG = "VideoAnalyticsTracker"
    private var loadedEventSent = false
    private var bufferingEventOngoing = false
    private var seekingEventOngoing = false
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            eventSender.sendHeartbeatEvent(player.currentPosition, player.duration)
            heartbeatHandler.postDelayed(this, config.heartbeatIntervalMs)
        }
    }

    /**
     * Configuration options for the tracker
     */
    data class Configuration(
        val contentTitle: String? = null,
        val isLive: Boolean = false,
        val deviceType: String = "Android player",
        val heartbeatIntervalMs: Long = 30_000L
    )

    init {
        setupPlayerListeners()
        initializeTracking()
    }

    /**
     * Builder class for creating VideoAnalyticsTracker instances
     */
    class Builder(private val player: ExoPlayer) {
        private var eventSinkUrl: String = AnalyticsEventSender.DEFAULT_EVENT_SINK_URL
        private var contentTitle: String? = null
        private var isLive: Boolean = false
        private var deviceType: String = "Android player"
        private var heartbeatIntervalMs: Long = 30_000L

        fun setEventSinkUrl(url: String) = apply { this.eventSinkUrl = url }
        fun setContentTitle(title: String?) = apply { this.contentTitle = title }
        fun setIsLive(isLive: Boolean) = apply { this.isLive = isLive }
        fun setDeviceType(deviceType: String) = apply { this.deviceType = deviceType }
        fun setHeartbeatInterval(intervalMs: Long) = apply { this.heartbeatIntervalMs = intervalMs }

        fun build(): VideoAnalyticsTracker {
            val eventSender = AnalyticsEventSender(eventSinkUrl)
            val config = Configuration(contentTitle, isLive, deviceType, heartbeatIntervalMs)
            return VideoAnalyticsTracker(player, eventSender, config)
        }
    }

    private fun setupPlayerListeners() {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        eventSender.sendBufferingEvent(player.currentPosition, player.duration)
                        bufferingEventOngoing = true
                    }
                    Player.STATE_READY -> {
                        if (bufferingEventOngoing) {
                            eventSender.sendBufferedEvent(player.currentPosition, player.duration)
                            bufferingEventOngoing = false
                        }
                        // Send "loaded" event once, when first ready
                        if (!loadedEventSent) {
                            eventSender.sendLoadedEvent()
                            loadedEventSent = true
                        }
                        if (player.playWhenReady) {
                            eventSender.sendPlayingEvent(player.currentPosition, player.duration)
                        }
                    }
                    Player.STATE_ENDED -> {
                        eventSender.sendStoppedEvent(
                            player.currentPosition,
                            player.duration,
                            "Playback ended"
                        )
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    eventSender.sendPlayingEvent(player.currentPosition, player.duration)
                } else if (player.playbackState != Player.STATE_BUFFERING &&
                    player.playbackState != Player.STATE_ENDED) {
                    eventSender.sendPausedEvent(player.currentPosition, player.duration)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    eventSender.sendSeekingEvent(player.currentPosition, player.duration)
                    seekingEventOngoing = true
                }
            }

            @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
             fun onSeekProcessed() {
                if (seekingEventOngoing) {
                    eventSender.sendSeekedEvent(player.currentPosition, player.duration)
                    seekingEventOngoing = false
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                eventSender.sendErrorEvent(
                    player.currentPosition,
                    player.duration,
                    "playback",
                    error.errorCode.toString(),
                    error.message
                )
            }
        })

        player.addAnalyticsListener(object : AnalyticsListener {
            @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                val bitrateKbps = format.bitrate / 1000
                val width = format.width
                val height = format.height

                eventSender.sendBitrateChangedEvent(
                    player.currentPosition,
                    player.duration,
                    bitrateKbps,
                    width,
                    height
                )
            }
        })
    }

    private fun initializeTracking() {
        // Send initial events
        eventSender.sendInitEvent(0L)
        eventSender.sendMetadataEvent(
            isLive = config.isLive,
            contentTitle = config.contentTitle,
            deviceType = config.deviceType
        )
        eventSender.sendLoadingEvent()
    }

    /**
     * Start heartbeat monitoring
     */
    fun startTracking() {
        heartbeatHandler.post(heartbeatRunnable)
    }

    /**
     * Stop heartbeat monitoring and send stopped event
     */
    fun stopTracking(reason: String = "Stopped by user") {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        eventSender.sendStoppedEvent(
            player.currentPosition,
            player.duration,
            reason
        )
    }

    /**
     * Clean up resources. Should be called when the player is being released.
     */
    fun release() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
    }

    /**
     * Manually send a custom event if needed
     */
    fun sendCustomEvent(
        eventType: String,
        playhead: Long = player.currentPosition,
        duration: Long = player.duration,
        payload: JSONObject? = null
    ) {
        eventSender.sendEvent(
            AnalyticsEventType.valueOf(eventType.uppercase()),
            System.currentTimeMillis(),
            playhead,
            duration,
            payload
        )
    }
}