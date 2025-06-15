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
import androidx.media3.common.Timeline
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

    /**
     * Store tracking URLs map for a specific ad.
     * @param adKey Unique identifier for the ad (e.g., "adsId_adGroup_adIndex")
     * @param trackingMap Map where key is event type and value is list of tracking URLs
     */
    fun setAdTrackingUrls(adKey: String, trackingMap: Map<String, List<String>>) {
        adTrackingUrlsMap[adKey] = trackingMap
        sentTrackingEvents[adKey] = mutableSetOf()
        Log.d("TrackingMap", "Stored tracking URLs for ad $adKey with ${trackingMap.size} event types")
    }

    /**
     * Send tracking pixel request to URL.
     */
    private fun sendTrackingPixel(url: String, eventType: String, adKey: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val decodedUrl = URLDecoder.decode(url, "UTF-8")
                val connection = URL(decodedUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "ExoPlayer-AdTracker/1.0")
                connection.connect()
                val responseCode = connection.responseCode
                Log.d("TrackingPixel", "[$eventType] Tracking sent for $adKey: $responseCode")
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("TrackingPixel", "Failed to send [$eventType] tracking for $adKey: ${e.message}")
            }
        }
    }

    /**
     * Map PlayerManager event names to AdExtractor event names
     */
    private fun mapEventName(eventType: String): String {
        return when (eventType) {
            "firstQuartile" -> "start"
            "thirdQuartile" -> "complete"
            else -> eventType
        }
    }

    /**
     * Send tracking event for specified ad and event type
     */
    private fun sendTrackingEvent(adKey: String, eventType: String) {
        val sentEvents = sentTrackingEvents.getOrPut(adKey) { mutableSetOf() }
        if (sentEvents.contains(eventType)) {
            return
        }

        var urls = adTrackingUrlsMap[adKey]?.get(eventType) ?: emptyList()

        if (urls.isEmpty()) {
            val mappedEventType = mapEventName(eventType)
            urls = adTrackingUrlsMap[adKey]?.get(mappedEventType) ?: emptyList()
        }

        if (urls.isEmpty()) {
            val mappedEventType = mapEventName(eventType)
            urls = adExtractor?.getTrackingUrlsForAd(adKey)?.get(mappedEventType) ?: emptyList()
        }

        if (urls.isNotEmpty()) {
            Log.d("TrackingEvent", "Sending $eventType event for ad $adKey with ${urls.size} URLs")
            urls.forEach { url ->
                sendTrackingPixel(url, eventType, adKey)
            }
            sentEvents.add(eventType)
        } else {
            Log.d("TrackingEvent", "No tracking URLs found for event $eventType on ad $adKey")
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
                Log.d("AdsLoader", "AssetList loaded for ad $adKey")

                val trackingMap = extractTrackingUrlsFromAssetList(assetList, adKey)
                if (trackingMap.isNotEmpty()) {
                    setAdTrackingUrls(adKey, trackingMap)
                    sendTrackingEvent(adKey, "loaded")
                }
            }

            override fun onStart(mediaItem: MediaItem, adsId: Any, adViewProvider: AdViewProvider) {
                Log.d("AdsLoader", "AdsLoader started for adsId: $adsId")
            }

            override fun onPrepareError(mediaItem: MediaItem, adsId: Any, adGroupIndex: Int, adIndexInAdGroup: Int, exception: IOException) {
                Log.e("AdsLoader", "Ad prepare error for adsId: $adsId, adGroup: $adGroupIndex, adIndex: $adIndexInAdGroup", exception)
            }

            override fun onAssetListLoadFailed(mediaItem: MediaItem, adsId: Any, adGroupIndex: Int, adIndexInAdGroup: Int, ioException: IOException?, cancelled: Boolean) {
                Log.e("AdsLoader", "Asset list load failed for adsId: $adsId: ${ioException?.message ?: "Cancelled: $cancelled"}", ioException)
            }

            override fun onContentTimelineChanged(mediaItem: MediaItem, adsId: Any, hlsContentTimeline: Timeline) {
                Log.d("AdsLoader", "Content timeline changed for adsId: $adsId")
            }

            override fun onAdCompleted(mediaItem: MediaItem, adsId: Any, adGroupIndex: Int, adIndexInAdGroup: Int) {
                val adKey = "${adsId}_${adGroupIndex}_${adIndexInAdGroup}"
                Log.d("AdsLoader", "Ad completed for ad $adKey")
                sendTrackingEvent(adKey, "complete")
            }
        })
    }

    data class VastTracking(
        val impression: List<String>,
        val firstQuartile: List<String>,
        val midpoint: List<String>,
        val thirdQuartile: List<String>,
        val complete: List<String>
    )

    fun parseVastTracking(vastXml: String): VastTracking {
        fun extract(tag: String): List<String> =
            Regex("""<$tag(?:\s+event="([^"]+)")?>\s*<!\[CDATA\[(.*?)\]\]>\s*</$tag>""")
                .findAll(vastXml)
                .map { it.groupValues[2] }
                .toList()

        return VastTracking(
            impression = extract("Impression"),
            firstQuartile = extract("Tracking event=\"firstQuartile\""),
            midpoint = extract("Tracking event=\"midpoint\""),
            thirdQuartile = extract("Tracking event=\"thirdQuartile\""),
            complete = extract("Tracking event=\"complete\"")
        )
    }

    private fun vastTrackingToMap(vastTracking: VastTracking): Map<String, List<String>> {
        return mapOf(
            "impression" to vastTracking.impression,
            "firstQuartile" to vastTracking.firstQuartile,
            "midpoint" to vastTracking.midpoint,
            "thirdQuartile" to vastTracking.thirdQuartile,
            "complete" to vastTracking.complete
        ).filterValues { it.isNotEmpty() }
    }

    private fun extractTrackingUrlsFromAssetList(
        assetList: HlsInterstitialsAdsLoader.AssetList,
        adKey: String
    ): Map<String, List<String>> {
        val trackingMap = mutableMapOf<String, List<String>>()

        for (attribute in assetList.stringAttributes) {
            if (attribute.name.contains("vast", ignoreCase = true) ||
                attribute.value.contains("<VAST", ignoreCase = true)) {

                val trackingInfo = parseVastTracking(attribute.value)
                val vastTrackingMap = vastTrackingToMap(trackingInfo)

                vastTrackingMap.forEach { (eventType, urls) ->
                    val existingUrls = trackingMap[eventType] ?: emptyList()
                    trackingMap[eventType] = existingUrls + urls
                }

                Log.d("VastTracking", "VAST tracking extracted for ad $adKey with ${vastTrackingMap.size} event types")
            }
        }

        return trackingMap
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
                                val adGroupIndex =
                                    this@SGAIAdTracker::player.get().currentAdGroupIndex
                                val adIndexInAdGroup =
                                    this@SGAIAdTracker::player.get().currentAdIndexInAdGroup
                                val currentAdsId = "ad-session-1"
                                val adKey = "${currentAdsId}_${adGroupIndex}_${adIndexInAdGroup}"
                                sendTrackingEvent(adKey, "impression")
                                sendTrackingEvent(adKey, "start")
                            }
                        }
                    }
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
                        1 -> sendTrackingEvent(currentAdKey!!, "firstQuartile")
                        2 -> sendTrackingEvent(currentAdKey!!, "midpoint")
                        3 -> sendTrackingEvent(currentAdKey!!, "thirdQuartile")
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

                        sendTrackingEvent(currentAdKey!!, "impression")
                        sendTrackingEvent(currentAdKey!!, "start")
                        startAdProgressTracking()
                    }
                } else if (lastAdGroupIndex != -1 || lastAdIndex != -1) {
                    if (currentAdKey != null) {
                        sendTrackingEvent(currentAdKey!!, "complete")
                    }
                    lastAdGroupIndex = -1
                    lastAdIndex = -1
                    currentAdKey = null
                    stopAdProgressTracking()
                }
            }
        })
    }

    fun setMainMediaItem(streamUrl: String) {
        SGAIAdTrackingUrlsExtractor(streamUrl).also {
            this.adExtractor = it
            it.setAdsId("ad-session-1")
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
        Log.d("PlayerManager", "Playing media")
        player.playWhenReady = true
        player.play()
    }

    fun pause() {
        Log.d("PlayerManager", "Pausing media")
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