package com.analytics.sampleplayer

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.PlayerView
import com.analytics.sdk.VideoAnalyticsTracker
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class SGAIPlayerActivity : ComponentActivity() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var analyticsTracker: VideoAnalyticsTracker
    private lateinit var playerView: PlayerView

    // Ad tracking related variables
    private val TAG = "SGAI_AdTracking"
    private var adTrackingClient: AdTrackingService? = null
    private var sessionId = UUID.randomUUID().toString()
    private val adTrackingEvents = mutableMapOf<String, Boolean>()
    private var currentAdId: String? = null
    private var adStartTime: Long = 0
    private var playingAd = false
    private var previousManifestContent: String? = null
    private val manifestRefreshInterval = 15000L

    // Customize these as needed - SGAI stream URL
    private val sgaiStreamUrl = "https://eyevinnlab-adtracking.eyevinn-sgai-ad-proxy.auto.prod.osaas.io/loop/master.m3u8"
    private val eventSinkUrl = "https://eyevinnlab-epasdev.eyevinn-player-analytics-eventsink.auto.prod.osaas.io"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializePlayer()
        initializeAnalyticsTracker()
        initializeAdTrackingClient()

        playerView = PlayerView(this).apply {
            player = exoPlayer
        }

        setContent {
            MaterialTheme {
                VideoPlayerScreen(playerView)
            }
        }

        Log.i(TAG, "Starting SGAI Player with session ID: $sessionId")
        Log.i(TAG, "Stream URL: $sgaiStreamUrl")

        // Start monitoring live stream for ad cues
        CoroutineScope(Dispatchers.Main).launch {
            monitorLiveStream(sgaiStreamUrl)
        }
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(createMediaSourceFactory())
            .build().apply {
                playWhenReady = false
            }

        // Build and set the main media item (SGAI live stream)
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(sgaiStreamUrl))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.addAnalyticsListener(EventLogger())

        // Add player listener for tracking ad progress and regular playback
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (playingAd && state == Player.STATE_READY) {
                    sendAdEventToAnalyticsTracker("ad_start")
                }

                if (playingAd && state == Player.STATE_ENDED) {
                    sendAdEventToAnalyticsTracker("ad_complete")
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (playingAd) {
                    if (isPlaying) {
                        if (adTrackingEvents["pause"] == true) {
                            sendAdEventToAnalyticsTracker("ad_resume")
                        }
                    } else if (exoPlayer.playbackState == Player.STATE_READY) {
                        sendAdEventToAnalyticsTracker("ad_pause")
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (playingAd) {
                    sendAdEventToAnalyticsTracker("ad_error")
                }
                Log.e(TAG, "Player Error: ${error.message}")
            }
        })
    }

    private fun initializeAnalyticsTracker() {
        analyticsTracker = VideoAnalyticsTracker.Builder(exoPlayer)
            .setEventSinkUrl(eventSinkUrl)
            .setContentTitle("SGAI Live Stream with Ads")
            .setIsLive(true)
            .setDeviceType("Android SGAI App")
            .setHeartbeatInterval(30_000L)
            .build()
    }

    private fun initializeAdTrackingClient() {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://tracking.eyevinn.technology/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        adTrackingClient = retrofit.create(AdTrackingService::class.java)
        Log.d(TAG, "Ad tracking client initialized with session ID: $sessionId")
    }

    @OptIn(UnstableApi::class)
    private fun createMediaSourceFactory(): DefaultMediaSourceFactory {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
        return DefaultMediaSourceFactory(dataSourceFactory)
    }

    private suspend fun monitorLiveStream(streamUrl: String) {
        while (true) {
            val manifestContent = withContext(Dispatchers.IO) {
                fetchManifestContent(streamUrl)
            }

            if (!manifestContent.isNullOrEmpty() && !playingAd) {
                previousManifestContent = manifestContent

                if (manifestContent.startsWith("#EXT-X-DATERANGE") || manifestContent.contains("#EXT-X-STREAM-INF")) {
                    val variantUrl = extractVariantUrl(manifestContent, streamUrl)

                    Log.d(TAG, "Manifest:\n$manifestContent")

                    if (variantUrl != null) {
                        val mediaPlaylistContent = withContext(Dispatchers.IO) {
                            fetchManifestContent(variantUrl)
                        }

                        if (!mediaPlaylistContent.isNullOrEmpty()) {
                            playingAd = true
                            parseAdCuesFromManifest(mediaPlaylistContent)
                        }
                    }
                } else {
                    parseAdCuesFromManifest(manifestContent)
                }
            }

            delay(manifestRefreshInterval)
        }
    }

    private fun fetchManifestContent(streamUrl: String): String? {
        return try {
            val url = URL(streamUrl)
            val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val inputStream = connection.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            val manifestContent = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                manifestContent.append(line).append("\n")
            }
            reader.close()

            manifestContent.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractVariantUrl(manifestContent: String, baseUrl: String): String? {
        val lines = manifestContent.split("\n")
        for (line in lines) {
            if (line.startsWith("#EXT-X-DATERANGE") || line.startsWith("#EXT-X-STREAM-INF")) {
                val variantUrl = lines.getOrNull(lines.indexOf(line) + 1) ?: return null
                return resolveVariantUrl(baseUrl, variantUrl)
            }
        }
        return null
    }

    private fun resolveVariantUrl(baseUrl: String, variantUrl: String): String {
        return if (variantUrl.startsWith("http")) {
            variantUrl
        } else {
            val baseUri = Uri.parse(baseUrl)
            val basePath = baseUri.path?.substringBeforeLast('/') ?: ""
            baseUri.buildUpon()
                .path("$basePath/$variantUrl")
                .build()
                .toString()
        }
    }

    private suspend fun parseAdCuesFromManifest(manifestContent: String) {
        val lines = manifestContent.split("\n")
        var adBreakId: String? = null

        for (line in lines) {
            if (line.startsWith("#EXT-X-DATERANGE")) {
                adBreakId = extractAdBreakId(line)
                Log.d(TAG, "Found ad break with ID: $adBreakId")
            }

            if (line.startsWith("#EXT-X-DATERANGE") || manifestContent.contains("#EXT-X-STREAM-INF")) {
                val assetListUrl = extractAssetListUrl(line)

                if (assetListUrl != null) {
                    val replacedUrl = assetListUrl.replace("localhost", "10.0.2.2")
                    fetchAndInsertAds(replacedUrl, adBreakId)
                }
            }
        }
    }

    private fun extractAdBreakId(daterangeLine: String): String {
        val attributes = daterangeLine.split(",")
        for (attribute in attributes) {
            if (attribute.contains("ID=")) {
                return attribute.substringAfter("ID=\"").substringBefore("\"")
            }
        }
        return UUID.randomUUID().toString()
    }

    private fun extractAssetListUrl(daterangeLine: String): String? {
        val attributes = daterangeLine.split(",")
        for (attribute in attributes) {
            if (attribute.startsWith("X-ASSET-LIST=")) {
                return attribute.substringAfter("X-ASSET-LIST=\"").substringBefore("\"")
            }
        }
        return null
    }

    private suspend fun fetchAdAssets(assetListUrl: String): AdResponse {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.0.2.2:3333/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(AdProxyService::class.java)
        return service.getAdAssets(assetListUrl)
    }

    private suspend fun fetchAndInsertAds(assetListUrl: String, adBreakId: String? = null) {
        try {
            val actualAdBreakId = adBreakId ?: UUID.randomUUID().toString()
            Log.d(TAG, "Fetching ads from asset list: $assetListUrl with adBreakId: $actualAdBreakId")

            val adResponse = fetchAdAssets(assetListUrl)

            // Send ad break start event to analytics tracker
            sendAdEventToAnalyticsTracker("ad_break_start")
            sendAdBreakTrackingEvent("breakStart", actualAdBreakId)

            Log.d(TAG, "Ad break started: $actualAdBreakId with ${adResponse.ASSETS.size} assets")

            for (adAsset in adResponse.ASSETS) {
                Log.d(TAG, "Inserting ad: ${adAsset.URI} (Duration: ${adAsset.DURATION} seconds)")

                currentAdId = adAsset.URI.hashCode().toString()
                Log.d(TAG, "Processing ad with ID: $currentAdId from URI: ${adAsset.URI}")

                val trackingUrls = adAsset.TRACKING_URLS ?: emptyMap()

                val adManifestContent = fetchAdManifest(adAsset.URI)
                if (adManifestContent != null) {
                    Log.d(TAG, "Successfully fetched ad manifest, proceeding to insert ad")
                    insertAd(adManifestContent, adAsset.DURATION, trackingUrls)
                } else {
                    Log.e(TAG, "Failed to fetch ad manifest for ${adAsset.URI}")
                }
            }

            // Send ad break end event to analytics tracker
            sendAdEventToAnalyticsTracker("ad_break_end")
            sendAdBreakTrackingEvent("breakComplete", actualAdBreakId)

            Log.d(TAG, "Ad break completed: $actualAdBreakId")

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Error fetching or inserting ads: ${e.message}")
        }
    }

    private suspend fun fetchAdManifest(adUri: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(adUri)
                val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                val inputStream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream))
                val adManifestContent = StringBuilder()
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    adManifestContent.append(line).append("\n")
                }
                reader.close()

                Log.d(TAG, "Ad Manifest Content:\n$adManifestContent")
                extractMediaSegmentUrl(adManifestContent.toString())
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun extractMediaSegmentUrl(adManifestContent: String): String? {
        val lines = adManifestContent.split("\n")
        for (line in lines) {
            if (line.startsWith("https://")) {
                return line.trim()
            }
        }
        return null
    }

    private fun insertAd(mediaSegmentUrl: String, adDuration: Long, trackingUrls: Map<String, String> = emptyMap()) {
        Log.d(TAG, "Inserting ad: $mediaSegmentUrl with duration: $adDuration seconds")

        // Reset tracking events for new ad
        adTrackingEvents.clear()
        adStartTime = System.currentTimeMillis()

        val adMediaItem = MediaItem.Builder()
            .setUri(Uri.parse(mediaSegmentUrl))
            .setMimeType(MimeTypes.VIDEO_MP4)
            .build()

        // Replace current content with the ad
        exoPlayer.setMediaItem(adMediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        // Send ad start events
        sendAdEventToAnalyticsTracker("ad_start")
        sendAdTrackingEvent("start")
        adTrackingEvents["start"] = true

        // Fire VAST impression and start tracking pixels immediately
        trackingUrls["impression"]?.let {
            sendTrackingPixel(it)
            Log.d(TAG, "Fired VAST impression pixel")
        }
        trackingUrls["start"]?.let {
            sendTrackingPixel(it)
            Log.d(TAG, "Fired VAST start pixel")
        }

        // Setup progress tracking
        val progressHandler = Handler(Looper.getMainLooper())
        val progressRunnable = object : Runnable {
            override fun run() {
                if (playingAd && exoPlayer.isPlaying) {
                    val currentPosition = exoPlayer.currentPosition
                    val totalDuration = adDuration * 1000
                    val progressPercentage = (currentPosition.toFloat() / totalDuration.toFloat()) * 100

                    Log.d(TAG, "Ad progress: $progressPercentage% ($currentPosition / ${adDuration * 1000}ms)")

                    // Track quartile events and send to both tracking systems
                    when {
                        progressPercentage >= 25 && adTrackingEvents["firstQuartile"] != true -> {
                            Log.d(TAG, "Reached first quartile (25%)")
                            sendAdEventToAnalyticsTracker("ad_first_quartile")
                            sendAdTrackingEvent("firstQuartile")
                            adTrackingEvents["firstQuartile"] = true
                            trackingUrls["firstQuartile"]?.let { sendTrackingPixel(it) }
                        }
                        progressPercentage >= 50 && adTrackingEvents["midpoint"] != true -> {
                            Log.d(TAG, "Reached midpoint (50%)")
                            sendAdEventToAnalyticsTracker("ad_midpoint")
                            sendAdTrackingEvent("midpoint")
                            adTrackingEvents["midpoint"] = true
                            trackingUrls["midpoint"]?.let { sendTrackingPixel(it) }
                        }
                        progressPercentage >= 75 && adTrackingEvents["thirdQuartile"] != true -> {
                            Log.d(TAG, "Reached third quartile (75%)")
                            sendAdEventToAnalyticsTracker("ad_third_quartile")
                            sendAdTrackingEvent("thirdQuartile")
                            adTrackingEvents["thirdQuartile"] = true
                            trackingUrls["thirdQuartile"]?.let { sendTrackingPixel(it) }
                        }
                    }

                    progressHandler.postDelayed(this, 250)
                } else if (playingAd) {
                    progressHandler.postDelayed(this, 250)
                }
            }
        }

        progressHandler.post(progressRunnable)

        // After adDuration seconds, resume main content
        Handler(Looper.getMainLooper()).postDelayed({
            if (!adTrackingEvents.containsKey("complete")) {
                sendAdEventToAnalyticsTracker("ad_complete")
                sendAdTrackingEvent("complete")
                adTrackingEvents["complete"] = true
                trackingUrls["complete"]?.let { sendTrackingPixel(it) }
            }

            Log.d(TAG, "Ad playback completed, resuming main content")
            resumeMainContent()
            progressHandler.removeCallbacks(progressRunnable)
        }, adDuration * 1000)
    }

    private fun resumeMainContent() {
        playingAd = false
        currentAdId = null

        val mainMediaItem = MediaItem.Builder()
            .setUri(Uri.parse(sgaiStreamUrl))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        exoPlayer.setMediaItem(mainMediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Send ad events to the VideoAnalyticsTracker (eventsink)
    private fun sendAdEventToAnalyticsTracker(eventType: String) {
        try {
            // Create custom event data for ad tracking
            val eventData = mapOf(
                "event_type" to eventType,
                "ad_id" to (currentAdId ?: "unknown"),
                "session_id" to sessionId,
                "timestamp" to System.currentTimeMillis(),
                "playback_position" to exoPlayer.currentPosition
            )

            val eventDataMap = JSONObject(eventData)
            // Send custom event through analytics tracker
            analyticsTracker.sendCustomEvent(eventType, eventDataMap)
            Log.d(TAG, "Sent ad event to analytics tracker: $eventType")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send ad event to analytics tracker: ${e.message}")
        }
    }

    private fun sendAdTrackingEvent(eventType: String, additionalData: Map<String, Any> = emptyMap()) {
        if (currentAdId == null) {
            Log.e(TAG, "Cannot send tracking event: currentAdId is null")
            return
        }

        val eventData = AdTrackingEvent(
            sessionId = sessionId,
            eventType = eventType,
            adId = currentAdId!!,
            timestamp = System.currentTimeMillis(),
            playbackPosition = exoPlayer.currentPosition,
            additionalData = additionalData
        )

        Log.d(TAG, "Sending ad tracking event: $eventType for ad $currentAdId")
        adTrackingEvents[eventType] = true

        try {
            adTrackingClient?.trackAdEvent(eventData)?.enqueue(object : retrofit2.Callback<Void> {
                override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "Ad tracking event $eventType sent successfully")
                    } else {
                        Log.e(TAG, "Ad tracking event failed with response code: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Log.e(TAG, "Failed to send ad tracking event: ${t.message}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception while sending tracking event: ${e.message}")
        }
    }

    private fun sendAdBreakTrackingEvent(eventType: String, adBreakId: String?) {
        if (adBreakId == null) return

        val eventData = AdBreakTrackingEvent(
            sessionId = sessionId,
            eventType = eventType,
            adBreakId = adBreakId,
            timestamp = System.currentTimeMillis()
        )

        Log.d(TAG, "Sending ad break tracking event: $eventType for break $adBreakId")

        try {
            adTrackingClient?.trackAdBreakEvent(eventData)?.enqueue(object : retrofit2.Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                        if (response.isSuccessful) {
                            Log.d(TAG, "Ad break tracking event $eventType sent successfully")
                        }
                    }

                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        Log.e(TAG, "Failed to send ad break tracking event: ${t.message}")
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Exception while sending ad break tracking event: ${e.message}")
        }
    }

    private fun sendTrackingPixel(trackingUrl: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Sending VAST tracking pixel to: $trackingUrl")
                val url = URL(trackingUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = true

                // Add User-Agent header for better compatibility
                connection.setRequestProperty("User-Agent", "SGAI-Android-Player/1.0")

                connection.connect()

                val responseCode = connection.responseCode
                Log.d(TAG, "VAST tracking pixel sent successfully, response: $responseCode")
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send VAST tracking pixel to $trackingUrl: ${e.message}")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        analyticsTracker.startTracking()
        exoPlayer.play()
    }

    override fun onStop() {
        super.onStop()
        analyticsTracker.stopTracking("User left the app")
        exoPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        analyticsTracker.release()
        exoPlayer.release()
    }

    // Data classes for ad tracking
    data class AdResponse(
        val ASSETS: List<AdAsset>
    )

    data class AdAsset(
        val URI: String,
        val DURATION: Long,
        val TRACKING_URLS: Map<String, String>? = null,
        val ID: String? = null
    )

    interface AdProxyService {
        @GET
        suspend fun getAdAssets(@Url assetListUrl: String): AdResponse
    }

    data class AdTrackingEvent(
        val sessionId: String,
        val eventType: String,
        val adId: String,
        val timestamp: Long,
        val playbackPosition: Long,
        val additionalData: Map<String, Any> = emptyMap()
    )

    data class AdBreakTrackingEvent(
        val sessionId: String,
        val eventType: String,
        val adBreakId: String,
        val timestamp: Long
    )

    interface AdTrackingService {
        @POST("track/ad")
        fun trackAdEvent(@Body event: AdTrackingEvent): Call<Void>

        @POST("track/adbreak")
        fun trackAdBreakEvent(@Body event: AdBreakTrackingEvent): Call<Void>
    }
}

@Composable
fun SGAIVideoPlayerScreen(playerView: PlayerView) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )
    }
}