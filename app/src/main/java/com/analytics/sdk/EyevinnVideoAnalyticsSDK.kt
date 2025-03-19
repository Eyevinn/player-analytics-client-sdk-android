package com.analytics.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView
import org.json.JSONObject

/**
 * Comprehensive Eyevinnvideo player SDK with built-in analytics tracking
 * Encapsulates ExoPlayer to simplify implementation for end users
 */
class EyevinnVideoAnalyticsSDK private constructor(
    private val context: Context,
    private val eventSender: AnalyticsEventSender,
    private val config: Configuration
) {
    private val TAG = "EyevinnVideoAnalyticsSDK"
    private var loadedEventSent = false
    private var bufferingEventOngoing = false
    private var seekingEventOngoing = false
    private val heartbeatHandler = Handler(Looper.getMainLooper())

    /**
     * Built-in ExoPlayer instance
     */
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    /**
     * PlayerView for easy integration into layouts
     */
    val playerView: PlayerView by lazy {
        PlayerView(context).apply {
            this.player = this@EyevinnVideoAnalyticsSDK.player
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            eventSender.sendHeartbeatEvent(player.currentPosition, player.duration)
            heartbeatHandler.postDelayed(this, config.heartbeatIntervalMs)
        }
    }

    /**
     * Configuration options for the SDK
     */
    data class Configuration(
        val contentTitle: String? = null,
        val isLive: Boolean = false,
        val deviceType: String = "Android player",
        val heartbeatIntervalMs: Long = 30_000L,
        val eventSinkUrl: String = AnalyticsEventSender.DEFAULT_EVENT_SINK_URL
    )

    init {
        setupPlayerListeners()
    }

    /**
     * Builder class for creating VideoAnalyticsSDK instances
     */
    class Builder(private val context: Context) {
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

        fun build(): EyevinnVideoAnalyticsSDK {
            val eventSender = AnalyticsEventSender(eventSinkUrl)
            val config =
                Configuration(contentTitle, isLive, deviceType, heartbeatIntervalMs, eventSinkUrl)
            return EyevinnVideoAnalyticsSDK(context, eventSender, config)
        }
    }

    /**
     * Load media from URL and start playback
     * @param mediaUrl URL of the media to play
     * @param autoPlay Whether to start playing automatically
     */
    fun loadMedia(mediaUrl: String, autoPlay: Boolean = true) {
        // Reset tracking state
        loadedEventSent = false

        // Set media item
        player.setMediaItem(MediaItem.fromUri(mediaUrl))

        // Prepare player
        player.prepare()

        // Set playback state
        player.playWhenReady = autoPlay

        // Initialize analytics tracking
        initializeTracking()
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
                    player.playbackState != Player.STATE_ENDED
                ) {
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
     * Play the media
     */
    fun play() {
        player.play()
    }

    /**
     * Pause the media
     */
    fun pause() {
        player.pause()
    }

    /**
     * Seek to a specific position
     * @param positionMs Position in milliseconds
     */
    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    /**
     * Clean up resources. Should be called when the player is being released.
     */
    fun release() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        player.release()
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