package com.analytics.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.AdOverlayInfo
import androidx.media3.common.AdViewProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.AdsConfiguration
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsInterstitialsAdsLoader
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.util.EventLogger
import kotlinx.coroutines.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import androidx.core.net.toUri

@OptIn(UnstableApi::class)
class SGAIAdTracker(private val context: Context) {

    private val dataSourceFactory = DefaultDataSource.Factory(context)
    private var playerViewContainer: ViewGroup? = null
    private var adExtractor: SGAIAdTrackingUrlsExtractor? = null

    private val adTrackingUrlsMap: MutableMap<String, Map<String, List<String>>> = mutableMapOf()
    private val sentTrackingEvents: MutableMap<String, MutableSet<String>> = mutableMapOf()

    private var wasPlayingBeforePause = false
    private var isCurrentlyPaused = false
    private val activePods: MutableSet<String> = mutableSetOf()
    private val impressionSender = SGAIAdImpressionSender()

    // Utility: normalize event names
    private fun normalizeEventName(eventName: String): String {
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

    // Optionally expand this to cover more event mapping logic
    private fun mapEventName(eventType: String): String {
        return when (eventType.lowercase()) {
            "impression" -> "start"    // Fallback: map impression to start
            "firstquartile" -> "start"
            "thirdquartile" -> "complete"
            else -> eventType
        }
    }


    /**
     * Store tracking URLs map for a specific ad.
     */
    fun setAdTrackingUrls(adKey: String, trackingMap: Map<String, List<String>>) {
        adTrackingUrlsMap[adKey] = trackingMap
        sentTrackingEvents[adKey] = mutableSetOf()
        Log.d("****** TrackingMap", "Stored tracking URLs for ad $adKey with ${trackingMap.size} event types")

    }

    /**
     * Robust tracking event logic using fallback and normalization.
     */
    private fun sendTrackingEvent(adKey: String, eventType: SGAIAdTrackingEvent) {
        val sentEvents = sentTrackingEvents.getOrPut(adKey) { mutableSetOf() }
        val eventTypeName = eventType.eventName

        // All possible keys to check, normalized and mapped
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
            Log.d("TrackingEvent", "Sending $eventType event for ad $adKey with ${urls.size} URLs [keys tried: $allPossibleKeys]")
            impressionSender.sendMultipleImpressions(urls, eventType, adKey)
            if (eventType != SGAIAdTrackingEvent.PAUSE && eventType != SGAIAdTrackingEvent.RESUME) {
                sentEvents.add(eventTypeName)
            }
        } else {
            Log.d("TrackingEvent", "No tracking URLs found for event $eventTypeName on ad $adKey [keys tried: $allPossibleKeys]")
            Log.d("TrackingEvent", "Available keys: ${adTrackingUrlsMap[adKey]?.keys}")
        }
    }

    private fun sendPodTrackingEvent(podKey: String, eventType: SGAIAdTrackingEvent) {
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
            Log.d("PodTracking", "Sending ${eventType.eventName} event for pod $podKey with ${urls.size} URLs [keys tried: $allPossibleKeys]")
            impressionSender.sendMultipleImpressions(urls, eventType, podKey)
        } else {
            Log.d("PodTracking", "No tracking URLs found for event ${eventType.eventName} on pod $podKey [keys tried: $allPossibleKeys]")
            Log.d("PodTracking", "Available pod keys: ${adTrackingUrlsMap[podKey]?.keys}")
        }
    }

    private fun handleAdPause() {
        currentAdKey?.let { adKey ->
            if (!isCurrentlyPaused) {
                isCurrentlyPaused = true
                sendTrackingEvent(adKey, SGAIAdTrackingEvent.PAUSE)
            }
        }
    }

    private fun handleAdResume() {
        currentAdKey?.let { adKey ->
            if (isCurrentlyPaused) {
                isCurrentlyPaused = false
                sendTrackingEvent(adKey, SGAIAdTrackingEvent.RESUME)
            }
        }
    }

    fun setPlayerViewContainer(container: ViewGroup) {
        playerViewContainer = container
    }

    private val adViewProvider = object : AdViewProvider {
        override fun getAdViewGroup(): android.view.ViewGroup? {
            return playerViewContainer
        }

        override fun getAdOverlayInfos(): List<AdOverlayInfo> {
            return playerViewContainer?.let {
                listOf(AdOverlayInfo(it, AdOverlayInfo.PURPOSE_CONTROLS))
            } ?: emptyList()
        }
    }

    private val adsLoader: HlsInterstitialsAdsLoader = HlsInterstitialsAdsLoader(
        dataSourceFactory
    ).apply {
        addListener(object : HlsInterstitialsAdsLoader.Listener {
            override fun onAssetListLoadCompleted(
                mediaItem: MediaItem,
                adsId: Any,
                adGroupIndex: Int,
                adIndexInAdGroup: Int,
                assetList: HlsInterstitialsAdsLoader.AssetList
            ) {
                val adKey = "${adsId}_${adGroupIndex}_${adIndexInAdGroup}"
                Log.d("AdsLoader", "Ad completed for ad $adKey")
                sendTrackingEvent(adKey, SGAIAdTrackingEvent.COMPLETE)
                checkAndCompletePod(adGroupIndex)
            }
        })
    }

    private fun checkAndCompletePod(adGroupIndex: Int) {
        val podKey = "pod_ad-session-1_${adGroupIndex}"
        if (activePods.contains(podKey)) {
            activePods.remove(podKey)
            sendPodTrackingEvent(podKey, SGAIAdTrackingEvent.POD_END)
            adExtractor?.sendPodEndTracking(podKey)
        }
    }

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .build()
        .apply {
            addAnalyticsListener(EventLogger())
            addAnalyticsListener(object : AnalyticsListener {
                override fun onPlayerStateChanged(
                    eventTime: AnalyticsListener.EventTime,
                    playWhenReady: Boolean,
                    playbackState: Int
                ) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (this@SGAIAdTracker::player.get().isPlayingAd) {
                                val adGroupIndex = this@SGAIAdTracker::player.get().currentAdGroupIndex
                                val adIndexInAdGroup = this@SGAIAdTracker::player.get().currentAdIndexInAdGroup
                                val currentAdsId = "ad-session-1"
                                val adKey = "${currentAdsId}_${adGroupIndex}_${adIndexInAdGroup}"
                                sendTrackingEvent(adKey, SGAIAdTrackingEvent.IMPRESSION)
                                sendTrackingEvent(adKey, SGAIAdTrackingEvent.START)
                            }
                        }
                    }
                    if (this@SGAIAdTracker::player.get().isPlayingAd) {
                        if (!playWhenReady && wasPlayingBeforePause) {
                            handleAdPause()
                        } else if (playWhenReady && isCurrentlyPaused) {
                            handleAdResume()
                        }
                    }
                    wasPlayingBeforePause = playWhenReady
                }
                override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
                    Log.e("ExoPlayer", "Player error: ${error.message}", error)
                }
                override fun onTimelineChanged(eventTime: AnalyticsListener.EventTime, reason: Int) {
                    Log.d("ExoPlayer", "Timeline changed")
                }
                override fun onLoadError(
                    eventTime: AnalyticsListener.EventTime,
                    loadEventInfo: LoadEventInfo,
                    mediaLoadData: MediaLoadData,
                    error: IOException,
                    wasCanceled: Boolean
                ) {
                    Log.e("ExoPlayer", "Load error for ${loadEventInfo.uri}: ${error.message}", error)
                }
            })
        }

    private var lastAdQuartile = 0
    private var currentAdKey: String? = null
    private val adProgressHandler = Handler(Looper.getMainLooper())
    private val adProgressRunnable = object : Runnable {
        override fun run() {
            trackAdQuartiles()
            adProgressHandler.postDelayed(this, 250)
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
        if (player.isPlayingAd && currentAdKey != null) {
            val duration = player.duration
            val position = player.currentPosition
            println("***** progress and duration of ads")
            if (duration > 0) {
                val progress = position.toFloat() / duration
                val quartile = when {
                    progress >= 0.75f -> 3
                    progress >= 0.5f -> 2
                    progress >= 0.25f -> 1
                    else -> 0
                }
                if (quartile > lastAdQuartile) {
                    lastAdQuartile = quartile
                    when (quartile) {
                        1 -> sendTrackingEvent(currentAdKey!!, SGAIAdTrackingEvent.FIRST_QUARTILE)
                        2 -> sendTrackingEvent(currentAdKey!!, SGAIAdTrackingEvent.MIDPOINT)
                        3 -> sendTrackingEvent(currentAdKey!!, SGAIAdTrackingEvent.THIRD_QUARTILE)
                        4 -> sendTrackingEvent(currentAdKey!!, SGAIAdTrackingEvent.COMPLETE)

                    }
                }
            }
        } else {
            lastAdQuartile = 0
        }
    }

    init {
        adsLoader.setPlayer(player)
        Log.d("PlayerManager", "PlayerManager initialized")

        player.addListener(object : Player.Listener {
            private var lastAdIndex = -1
            private var lastAdGroupIndex = -1
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (player.isPlayingAd) {
                    val adGroupIndex = player.currentAdGroupIndex
                    val adIndexInAdGroup = player.currentAdIndexInAdGroup
                    if (adGroupIndex != lastAdGroupIndex || adIndexInAdGroup != lastAdIndex) {
                        lastAdGroupIndex = adGroupIndex
                        lastAdIndex = adIndexInAdGroup
                        lastAdQuartile = 0
                        currentAdKey = "ad-session-1_${adGroupIndex}_${adIndexInAdGroup}"
                        // Handle pod start event for new ad group
                        if (adIndexInAdGroup == 0) { // First ad in the pod
                            val podKey = "pod_ad-session-1_${adGroupIndex}"
                            activePods.add(podKey)
                            sendPodTrackingEvent(podKey, SGAIAdTrackingEvent.POD_START)
                        }
                        sendTrackingEvent(currentAdKey!!, SGAIAdTrackingEvent.IMPRESSION)
                        sendTrackingEvent(currentAdKey!!, SGAIAdTrackingEvent.START)
                        startAdProgressTracking()
                    }
                } else if (lastAdGroupIndex != -1 || lastAdIndex != -1) {
                    if (currentAdKey != null) {
                        sendTrackingEvent(currentAdKey!!, SGAIAdTrackingEvent.COMPLETE)
                    }
                    lastAdGroupIndex = -1
                    lastAdIndex = -1
                    currentAdKey = null
                    isCurrentlyPaused = false
                    stopAdProgressTracking()
                }
            }
        })
    }

    fun setMainMediaItem(streamUrl: String) {
        SGAIAdTrackingUrlsExtractor(streamUrl).also {
            this.adExtractor = it
            it.setAdsId("ad-session-1")
            it.addPodTrackingCallback { eventType, podId ->
                when (eventType) {
                    SGAIAdTrackingEvent.POD_START.eventName -> {
                        Log.d("** PodEvent", "Pod started: $podId")
                        activePods.add(podId)
                    }
                    SGAIAdTrackingEvent.POD_END.eventName -> {
                        Log.d("PodEvent", "Pod End: $podId")
                        activePods.remove(podId)
                    }
                }
            }
        }
        this.adExtractor?.startMonitoring()

        Log.d("PlayerManager", "Setting main media item: $streamUrl")
        try {
            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl.toUri())
                .apply {
                    setAdsConfiguration(
                        AdsConfiguration.Builder("placeholder".toUri())
                            .setAdsId("ad-session-1")
                            .build()
                    )
                }
                .build()
            val adsMediaSourceFactory = HlsInterstitialsAdsLoader.AdsMediaSourceFactory(
                adsLoader,
                adViewProvider,
                context
            )
            val adsMediaSource = adsMediaSourceFactory.createMediaSource(mediaItem)
            player.setMediaSource(adsMediaSource)
            player.prepare()
            Log.d("PlayerManager", "Player prepared with HLS interstitials support")
        } catch (e: Exception) {
            Log.e("PlayerManager", "Error preparing player: ${e.message}", e)
        }
    }

    fun play() {
        Log.d("PlayerManager", "** Playing media")
        player.playWhenReady = true
        player.play()
    }

    fun pause() {
        Log.d("PlayerManager", "** Pausing media")
        player.pause()
    }

    fun release() {
        Log.d("PlayerManager", "Releasing player and ads loader")
        adExtractor?.release()
        adsLoader.release()
        player.release()
    }

    fun setTrackingUrlsForAd(adKey: String, trackingUrls: Map<String, List<String>>) {
        setAdTrackingUrls(adKey, trackingUrls)
    }

    fun getTrackingUrlsForAd(adKey: String): Map<String, List<String>>? {
        return adTrackingUrlsMap[adKey]
    }
}
