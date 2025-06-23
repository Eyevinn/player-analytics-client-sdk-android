package com.analytics.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AdOverlayInfo
import androidx.media3.common.AdPlaybackState
import androidx.media3.common.AdViewProvider
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.AdsConfiguration
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsInterstitialsAdsLoader
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import org.json.JSONObject
import java.io.IOException

/**
 * Unified tracker that monitors an ExoPlayer instance for both video analytics
 * and SGAI ad tracking events
 */
@OptIn(UnstableApi::class)
class VideoAnalyticsTracker private constructor(
    private val context: Context,
    private val player: ExoPlayer,
    private val eventSender: AnalyticsEventSender,
    private val config: Configuration
) {
    private val TAG = "VideoAnalyticsTracker"

    // Video analytics state
    private var loadedEventSent = false
    private var bufferingEventOngoing = false

    // SGAI ad tracking state
    private var playerViewContainer: ViewGroup? = null
    private var adExtractor: SGAIAdTrackingUrlsExtractor? = null
    private val adTrackingUrlsMap: MutableMap<String, Map<String, List<String>>> = mutableMapOf()
    private val sentTrackingEvents: MutableMap<String, MutableSet<String>> = mutableMapOf()
    private val activePods: MutableSet<String> = mutableSetOf()
    private val impressionSender = SGAIAdImpressionSender()

    // Handlers
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val adProgressHandler = Handler(Looper.getMainLooper())

    // SGAI ad components
    private val adsLoader: HlsInterstitialsAdsLoader = HlsInterstitialsAdsLoader(context)

    // Runnables
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            eventSender.sendHeartbeatEvent(player.currentPosition, player.duration)
            heartbeatHandler.postDelayed(this, config.heartbeatIntervalMs)
        }
    }

    private val adProgressRunnable = object : Runnable {
        override fun run() {
            adProgressHandler.postDelayed(this, 250)
        }
    }

    /**
     * Configuration options for the tracker
     */
    data class Configuration(
        val contentTitle: String? = null,
        val isLive: Boolean = false,
        val deviceType: String = "Android player",
        val heartbeatIntervalMs: Long = 30_000L,
        val enableSGAITracking: Boolean = false
    )

    init {
        setupPlayerListeners()
        if (config.enableSGAITracking) {
            setupSGAIAdTracking()
        }
        initializeTracking()
    }

    /**
     * Builder class for creating VideoAnalyticsTracker instances
     */
    class Builder(private val context: Context, private val player: ExoPlayer) {
        private var eventSinkUrl: String = AnalyticsEventSender.DEFAULT_EVENT_SINK_URL
        private var contentTitle: String? = null
        private var isLive: Boolean = false
        private var deviceType: String = "Android player"
        private var heartbeatIntervalMs: Long = 30_000L
        private var enableSGAITracking: Boolean = false

        fun setEventSinkUrl(url: String) = apply { this.eventSinkUrl = url }
        fun setContentTitle(title: String?) = apply { this.contentTitle = title }
        fun setIsLive(isLive: Boolean) = apply { this.isLive = isLive }
        fun setDeviceType(deviceType: String) = apply { this.deviceType = deviceType }
        fun setHeartbeatInterval(intervalMs: Long) = apply { this.heartbeatIntervalMs = intervalMs }
        fun enableSGAIAdTracking(enable: Boolean) = apply { this.enableSGAITracking = enable }

        fun build(): VideoAnalyticsTracker {
            val eventSender = AnalyticsEventSender(eventSinkUrl)
            val config = Configuration(contentTitle, isLive, deviceType, heartbeatIntervalMs, enableSGAITracking)
            return VideoAnalyticsTracker(context, player, eventSender, config)
        }
    }

    private fun setupPlayerListeners() {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, "Playback state changed: $playbackState")
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        if (!config.enableSGAITracking || !player.isPlayingAd) {
                            eventSender.sendBufferingEvent(player.currentPosition, player.duration)
                            bufferingEventOngoing = true
                        }
                    }
                    Player.STATE_READY -> {
                        if (bufferingEventOngoing && (!config.enableSGAITracking || !player.isPlayingAd)) {
                            eventSender.sendBufferedEvent(player.currentPosition, player.duration)
                            bufferingEventOngoing = false
                        }

                        if (!loadedEventSent && (!config.enableSGAITracking || !player.isPlayingAd)) {
                            eventSender.sendLoadedEvent()
                            loadedEventSent = true
                        }

                        // Handle regular video playback
                        if (!config.enableSGAITracking || !player.isPlayingAd) {
                            if (player.playWhenReady) {
                                eventSender.sendPlayingEvent(player.currentPosition, player.duration)
                            }
                        }
                    }
                    Player.STATE_ENDED -> {
                        if (!config.enableSGAITracking || !player.isPlayingAd) {
                            eventSender.sendStoppedEvent(
                                player.currentPosition,
                                player.duration,
                                "Playback ended"
                            )
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "Playing changed: $isPlaying")
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                Log.d(TAG, "Position discontinuity: $oldPosition, $newPosition, reason: $reason")
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    return
                }

                // NOTICE: This is called in both Live and VoD streams when ad playback starts/stops
                Log.d(TAG, "is during ad break: ${player.isPlayingAd}")
                if (player.isPlayingAd) {
                    // When ad content starts playing
                    // TODO: start tracking ad progress
                    // TODO: send ad START tracking event
                    val adGroupIndex = player.currentAdGroupIndex
                    val adIndexInAdGroup = player.currentAdIndexInAdGroup

                    Log.d(TAG, "AD STARTED - Group: $adGroupIndex, Index: $adIndexInAdGroup")
                } else {
                    // Main content is resumed
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                val manifest = player.currentManifest
                if (manifest is HlsManifest) {
                    // Do something with the manifest.
                    val isLive = manifest.mediaPlaylist.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_EVENT
                    val isVod = manifest.mediaPlaylist.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_VOD
                    Log.d(TAG, "HLS Manifest is Live? $isLive or VoD $isVod")

                    // TODO Mark this stream as Live or VoD
                    // config.isLive = isLive

                    for (ad in manifest.mediaPlaylist.interstitials) {
                        // TODO print URLs if needed
                        // Log.d(TAG, "Found interstitial ad: ${ad.id}")
                        // You can extract ad tracking URLs here if needed
                        // Log.d(TAG, "Ad tracking URLs: ${ad.assetUri} or ${ad.assetListUri}")

                        // TODO fetch ad tracking URLs and extract the json response, save them based on IDs
                        // NOTICE: This is called in both Live and VoD streams when the timeline changes
                        // DO NOT request for the same interstitial multiple times
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
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
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                if (!config.enableSGAITracking || !player.isPlayingAd) {
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
            }
        })
    }

    private fun setupSGAIAdTracking() {
        adsLoader.addListener(object : HlsInterstitialsAdsLoader.Listener {

            override fun onStart(mediaItem: MediaItem, adsId: Any, adViewProvider: AdViewProvider) {
                // Do something when HLS media item with interstitials starts playing. It happens once per playback session.
                Log.d(TAG, "Ad loader started - $adsId")
            }

            override fun onStop(mediaItem: MediaItem, adsId: Any, adPlaybackState: AdPlaybackState) {
                // Do something when we stop playing HLS media item with interstitials (e.g., entering background).
                Log.d(TAG, "Ad loader stopped - $adsId")
            }

            override fun onContentTimelineChanged(
                mediaItem: MediaItem,
                adsId: Any,
                hlsContentTimeline: Timeline
            ) {
                Log.d(TAG, "Content timeline changed for $adsId: $hlsContentTimeline")
            }

            override fun onAdCompleted(
                mediaItem: MediaItem,
                adsId: Any,
                adGroupIndex: Int,
                adIndexInAdGroup: Int
            ) {
                Log.d(TAG, "AD COMPLETED for $adsId at group $adGroupIndex, index $adIndexInAdGroup")
                // TODO send ad COMPLETE event
                // NOTICE: This is called in both VoD and Live Playback

                // TODO check if there is more ads in the group
                // TODO send pod COMPLETE event if this is the last ad in the group
            }

            override fun onPrepareCompleted(
                mediaItem: MediaItem,
                adsId: Any,
                adGroupIndex: Int,
                adIndexInAdGroup: Int
            ) {
                Log.d(TAG, "Ad prepared for $adsId at group $adGroupIndex, index $adIndexInAdGroup")
            }

            override fun onMetadata(
                mediaItem: MediaItem,
                adsId: Any,
                adGroupIndex: Int,
                adIndexInAdGroup: Int,
                metadata: Metadata
            ) {
                Log.d(TAG, "Ad metadata received for $adsId at group $adGroupIndex, index $adIndexInAdGroup: $metadata")
            }

            override fun onAssetListLoadStarted(
                mediaItem: MediaItem,
                adsId: Any,
                adGroupIndex: Int,
                adIndexInAdGroup: Int
            ) {
                Log.d(TAG, "Loading ad asset list for $adsId at group $adGroupIndex, index $adIndexInAdGroup")
            }

            override fun onAssetListLoadFailed(
                mediaItem: MediaItem,
                adsId: Any,
                adGroupIndex: Int,
                adIndexInAdGroup: Int,
                ioException: IOException?,
                cancelled: Boolean
            ) {
                val adKey = "${adsId}_${adGroupIndex}_${adIndexInAdGroup}"
                Log.e(TAG, "Failed to load ad asset list for $adKey", ioException)
                // TODO send event on failure (e.g., unable to access localhost from emulator)
            }

            override fun onAssetListLoadCompleted(
                mediaItem: MediaItem,
                adsId: Any,
                adGroupIndex: Int,
                adIndexInAdGroup: Int,
                assetList: HlsInterstitialsAdsLoader.AssetList
            ) {
                val adKey = "${adsId}_${adGroupIndex}_${adIndexInAdGroup}"
                Log.d(TAG, "Ad asset list loaded for ad $adKey - ${assetList.stringAttributes}")
                // TODO fetch the asset list and extract tracking URLs
            }
        })

        adsLoader.setPlayer(player)
    }

    private fun initializeTracking() {
        // Send initial video analytics events
        eventSender.sendInitEvent(0L)
        eventSender.sendMetadataEvent(
            isLive = config.isLive,
            contentTitle = config.contentTitle,
            deviceType = config.deviceType
        )
        eventSender.sendLoadingEvent()
    }

    /**
     * Set the container for SGAI ad rendering (required for SGAI ad tracking)
     */
    fun setPlayerViewContainer(container: ViewGroup) {
        playerViewContainer = container
    }

    /**
     * Set main media item with optional SGAI ad support
     */
    fun setMainMediaItem(streamUrl: String) {
        if (config.enableSGAITracking) {
            val adViewProvider = object : AdViewProvider {
                override fun getAdViewGroup(): ViewGroup? = playerViewContainer
                override fun getAdOverlayInfos(): List<AdOverlayInfo> {
                    return playerViewContainer?.let {
                        listOf(AdOverlayInfo(it, AdOverlayInfo.PURPOSE_CONTROLS))
                    } ?: emptyList()
                }
            }

            val hlsMediaSourceFactory =
                HlsInterstitialsAdsLoader.AdsMediaSourceFactory(adsLoader, adViewProvider, context)

            // Create an media source from an HLS media item with ads configuration.
            val mediaSource =
                hlsMediaSourceFactory.createMediaSource(
                    MediaItem.Builder()
                        .setUri(streamUrl.toUri())
                        .setAdsConfiguration(
                            AdsConfiguration.Builder("hls://interstitials".toUri())
                                .setAdsId("playback-session-0")
                                .build()
                        )
                        .build()
                )

            player.setMediaSource(mediaSource)
        } else {
            val mediaItem = MediaItem.fromUri(streamUrl)
            player.setMediaItem(mediaItem)
        }

        player.prepare()
    }

    private fun sendTrackingEvent(adKey: String, eventType: SGAIAdTrackingEvent) {
        // Sync URLs before sending
        syncTrackingUrls()

        val sentEvents = sentTrackingEvents.getOrPut(adKey) { mutableSetOf() }
        val eventTypeName = eventType.eventName

        val allPossibleKeys = listOf(
            eventTypeName,
            normalizeEventName(eventTypeName),
            mapEventName(eventTypeName),
            normalizeEventName(mapEventName(eventTypeName))
        ).distinct()

        var urls: List<String> = emptyList()
        for (key in allPossibleKeys) {
            urls = adTrackingUrlsMap[adKey]?.get(key)
                ?: adExtractor?.getTrackingUrlsForAd(adKey)?.get(key)
                        ?: emptyList()
            if (urls.isNotEmpty()) break
        }

        if (urls.isNotEmpty()) {
            Log.d(TAG, "Sending $eventType event for ad $adKey with ${urls.size} URLs")
            Log.d(TAG, "Event URLs: $urls")
            impressionSender.sendMultipleImpressions(urls, eventType, adKey)
            if (eventType != SGAIAdTrackingEvent.PAUSE && eventType != SGAIAdTrackingEvent.RESUME) {
                sentEvents.add(eventTypeName)
            }
        }
    }

    private fun sendPodTrackingEvent(podKey: String, eventType: SGAIAdTrackingEvent) {
        syncTrackingUrls()

        val allPossibleKeys = listOf(
            eventType.eventName,
            normalizeEventName(eventType.eventName),
            mapEventName(eventType.eventName),
            normalizeEventName(mapEventName(eventType.eventName))
        ).distinct()

        var urls: List<String> = emptyList()
        for (key in allPossibleKeys) {
            urls = adTrackingUrlsMap[podKey]?.get(key)
                ?: adExtractor?.getTrackingUrlsForAd(podKey)?.get(key)
                        ?: emptyList()
            if (urls.isNotEmpty()) break
        }

        if (urls.isNotEmpty()) {
            Log.d(TAG, "Sending ${eventType.eventName} event for pod $podKey with ${urls.size} URLs")
            Log.d(TAG, "Pod Event URLs: $urls")
            impressionSender.sendMultipleImpressions(urls, eventType, podKey)
        } else {
            Log.d(TAG, "No tracking URLs found for event ${eventType.eventName} on pod $podKey")
            Log.d(TAG, "Available pod keys in adTrackingUrlsMap: ${adTrackingUrlsMap[podKey]?.keys}")
            Log.d(TAG, "Available pod keys in extractor: ${adExtractor?.getTrackingUrlsForAd(podKey)?.keys}")
        }
    }

    private fun checkAndCompletePod(adGroupIndex: Int) {
        val podKey = "pod_ad-session-1_${adGroupIndex}"
        if (activePods.contains(podKey)) {
            activePods.remove(podKey)
            sendPodTrackingEvent(podKey, SGAIAdTrackingEvent.POD_END)
            adExtractor?.sendPodEndTracking(podKey)
        }
    }

    private fun startAdProgressTracking() {
        adProgressHandler.post(adProgressRunnable)
    }

    /**
     * Sync tracking URLs from extractor to main tracking map
     */
    private fun syncTrackingUrls() {
        adExtractor?.getAllTrackingUrls()?.forEach { (key, urls) ->
            if (!adTrackingUrlsMap.containsKey(key)) {
                adTrackingUrlsMap[key] = urls
                sentTrackingEvents[key] = mutableSetOf()
                Log.d(TAG, "Synced tracking URLs for $key: ${urls.keys}")
            }
        }
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
        if (config.enableSGAITracking) {
            adExtractor?.release()
            adsLoader.release()
        }
    }

    /**
     * Manually send a custom event if needed
     */
    fun sendCustomEvent(
        eventType: String,
        payload: JSONObject? = null
    ) {
        eventSender.sendEvent(
            AnalyticsEventType.valueOf(eventType.uppercase()),
            System.currentTimeMillis(),
            player.currentPosition,
            player.duration,
            payload
        )
    }
}