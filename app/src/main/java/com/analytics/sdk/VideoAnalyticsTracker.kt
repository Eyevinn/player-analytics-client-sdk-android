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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private var seekingEventOngoing = false

    // SGAI ad tracking state
    private var playerViewContainer: ViewGroup? = null
    private var adExtractor: SGAIAdTrackingUrlsExtractor? = null
    private val adTrackingUrlsMap: MutableMap<String, Map<String, List<String>>> = mutableMapOf()
    private val sentTrackingEvents: MutableMap<String, MutableSet<String>> = mutableMapOf()
    private var wasPlayingBeforePause = false
    private var isCurrentlyPaused = false
    private val activePods: MutableSet<String> = mutableSetOf()
    private val impressionSender = SGAIAdImpressionSender()
    private var currentAdKey: String? = null
    private var lastAdQuartile = 0

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
            trackAdQuartiles()
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
            private var lastAdIndex = -1
            private var lastAdGroupIndex = -1

            override fun onPlaybackStateChanged(playbackState: Int) {
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
                if (config.enableSGAITracking && player.isPlayingAd) {
                    // Handle SGAI ad pause/resume
                    if (!isPlaying && wasPlayingBeforePause) {
                        handleAdPause()
                    } else if (isPlaying && isCurrentlyPaused) {
                        handleAdResume()
                    }
                    wasPlayingBeforePause = isPlaying
                } else if (!config.enableSGAITracking || !player.isPlayingAd) {
                    // Handle regular video playback
                    if (isPlaying) {
                        eventSender.sendPlayingEvent(player.currentPosition, player.duration)
                    } else if (player.playbackState != Player.STATE_BUFFERING &&
                        player.playbackState != Player.STATE_ENDED) {
                        eventSender.sendPausedEvent(player.currentPosition, player.duration)
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (config.enableSGAITracking && player.isPlayingAd) {
                    // Handle SGAI ad transitions (this is the KEY logic from original)
                    val adGroupIndex = player.currentAdGroupIndex
                    val adIndexInAdGroup = player.currentAdIndexInAdGroup

                    if (adGroupIndex != lastAdGroupIndex || adIndexInAdGroup != lastAdIndex) {
                        lastAdGroupIndex = adGroupIndex
                        lastAdIndex = adIndexInAdGroup
                        lastAdQuartile = 0
                        currentAdKey = "ad-session-1_${adGroupIndex}_${adIndexInAdGroup}"
                        isCurrentlyPaused = false

                        // Handle pod start event for new ad group
                        if (adIndexInAdGroup == 0) { // First ad in the pod
                            val podKey = "pod_ad-session-1_${adGroupIndex}"
                            activePods.add(podKey)
                            sendPodTrackingEvent(podKey, SGAIAdTrackingEvent.POD_START)
                        }

                        // Send ad start events
                        sendTrackingEvent(currentAdKey!!, SGAIAdTrackingEvent.IMPRESSION)
                        sendTrackingEvent(currentAdKey!!, SGAIAdTrackingEvent.START)
                        startAdProgressTracking()

                        Log.d(TAG, "Ad started via onPositionDiscontinuity: $currentAdKey")
                    }
                } else if (config.enableSGAITracking && lastAdGroupIndex != -1 || lastAdIndex != -1) {
                    lastAdGroupIndex = -1
                    lastAdIndex = -1
                    isCurrentlyPaused = false
                    stopAdProgressTracking()
                    Log.d(TAG, "Ad completed via onPositionDiscontinuity")
                } else if (!config.enableSGAITracking && reason == Player.DISCONTINUITY_REASON_SEEK) {
                    // Handle regular video seeking
                    eventSender.sendSeekingEvent(player.currentPosition, player.duration)
                    seekingEventOngoing = true
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

            override fun onPlayerStateChanged(
                eventTime: AnalyticsListener.EventTime,
                playWhenReady: Boolean,
                playbackState: Int
            ) {
                if (config.enableSGAITracking) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (player.isPlayingAd) {
                                val adGroupIndex = player.currentAdGroupIndex
                                val adIndexInAdGroup = player.currentAdIndexInAdGroup
                                val adKey = "ad-session-1_${adGroupIndex}_${adIndexInAdGroup}"

                                // Only send if this is a new ad
                                if (currentAdKey != adKey) {
                                    currentAdKey = adKey
                                    sendTrackingEvent(adKey, SGAIAdTrackingEvent.IMPRESSION)
                                    sendTrackingEvent(adKey, SGAIAdTrackingEvent.START)
                                    Log.d(TAG, "Ad started via AnalyticsListener: $adKey")
                                }
                            }
                        }
                    }

                    // Handle ad pause/resume via AnalyticsListener
                    if (player.isPlayingAd) {
                        if (!playWhenReady && wasPlayingBeforePause) {
                            handleAdPause()
                        } else if (playWhenReady && isCurrentlyPaused) {
                            handleAdResume()
                        }
                    }
                    wasPlayingBeforePause = playWhenReady
                }
            }
        })
    }

    private fun setupSGAIAdTracking() {
        adsLoader.addListener(object : HlsInterstitialsAdsLoader.Listener {

            override fun onStart(mediaItem: MediaItem, adsId: Any, adViewProvider: AdViewProvider) {
                Log.d(TAG, "Ad loader started - $adsId")
            }

            override fun onStop(mediaItem: MediaItem, adsId: Any, adPlaybackState: AdPlaybackState) {
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
                val adKey = "${adsId}_${adGroupIndex}_${adIndexInAdGroup}"
                // This is where the original code sent the COMPLETE event
                sendTrackingEvent(adKey, SGAIAdTrackingEvent.COMPLETE)
                checkAndCompletePod(adGroupIndex)
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
            setupSGAIExtractor(streamUrl)
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

    private fun setupSGAIExtractor(streamUrl: String) {
        SGAIAdTrackingUrlsExtractor(streamUrl) { adUri ->
            Log.d(TAG, "Ad break found with URI: $adUri")
        }.also {
            adExtractor = it
            it.setAdsId("ad-session-1")
            it.addPodTrackingCallback { eventType, podId ->
                when (eventType) {
                    SGAIAdTrackingEvent.POD_START.eventName -> {
                        Log.d(TAG, "Pod started: $podId")
                        activePods.add(podId)
                        // Copy pod tracking URLs to main map
                        it.getTrackingUrlsForAd(podId)?.let { podUrls ->
                            adTrackingUrlsMap[podId] = podUrls
                            Log.d(TAG, "Copied pod tracking URLs for $podId: ${podUrls.keys}")
                        }
                    }
                    SGAIAdTrackingEvent.POD_END.eventName -> {
                        Log.d(TAG, "Pod ended: $podId")
                        activePods.remove(podId)
                    }
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                it.processManifest()
            }

            // Periodically sync tracking URLs from extractor to main tracker
            syncTrackingUrls()
        }
    }

    private fun handleAdPause() {
        currentAdKey?.let { adKey ->
            if (!isCurrentlyPaused) {
                isCurrentlyPaused = true
                sendTrackingEvent(adKey, SGAIAdTrackingEvent.PAUSE)
                Log.d(TAG, "Ad paused: $adKey")
            }
        }
    }

    private fun handleAdResume() {
        currentAdKey?.let { adKey ->
            if (isCurrentlyPaused) {
                isCurrentlyPaused = false
                sendTrackingEvent(adKey, SGAIAdTrackingEvent.RESUME)
                Log.d(TAG, "Ad resumed: $adKey")
            }
        }
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

        val mappedAdKey = adTrackingUrlsMap.keys.singleOrNull() ?: adKey.takeIf { adTrackingUrlsMap.containsKey(it) } ?: adKey

        val urls = allPossibleKeys
            .asSequence()
            .map { key ->
                adTrackingUrlsMap[mappedAdKey]?.get(key)
                    ?: adExtractor?.getTrackingUrlsForAd(mappedAdKey)?.get(key)
                    ?: emptyList()
            }
            .firstOrNull { it.isNotEmpty() } ?: emptyList()

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

    private fun stopAdProgressTracking() {
        adProgressHandler.removeCallbacks(adProgressRunnable)
        lastAdQuartile = 0
    }

    private fun trackAdQuartiles() {
        if (!player.isPlayingAd || currentAdKey == null) {
            return
        }

        val duration = player.duration
        val position = player.currentPosition

        if (duration > 0 && position >= 0) {
            val progress = position.toFloat() / duration.toFloat()
            Log.d(TAG, "Ad progress: ${(progress * 100).toInt()}% ($position/$duration)")

            val quartile = when {
                progress >= 0.75f -> 3
                progress >= 0.5f -> 2
                progress >= 0.25f -> 1
                else -> 0
            }

            if (quartile > lastAdQuartile) {
                when (quartile) {
                    1 -> {
                        sendTrackingEvent(currentAdKey!!, SGAIAdTrackingEvent.FIRST_QUARTILE)
                        Log.d(TAG, "First quartile reached for $currentAdKey")
                    }
                    2 -> {
                        sendTrackingEvent(currentAdKey!!, SGAIAdTrackingEvent.MIDPOINT)
                        Log.d(TAG, "Midpoint reached for $currentAdKey")
                    }
                    3 -> {
                        sendTrackingEvent(currentAdKey!!, SGAIAdTrackingEvent.THIRD_QUARTILE)
                        Log.d(TAG, "Third quartile reached for $currentAdKey")
                    }
                }
                lastAdQuartile = quartile
            }
        }
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
        stopAdProgressTracking()
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
        stopAdProgressTracking()
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