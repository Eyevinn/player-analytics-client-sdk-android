package com.analytics.sampleplayer.sgai

import android.net.Uri
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import kotlinx.coroutines.NonCancellable.isActive
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class AdManager(
    private val playerManager: PlayerManager,
    private val mainStreamUrl: String
) {
    private val TAG = "SGAI_AdTracking"
    val sessionId = UUID.randomUUID().toString()
    private var playingAd = false
    private var currentAdId: String? = null
    private val manifestRefreshInterval = 15000L
    private var monitoringJob: Job? = null
    private val adTrackingEvents = mutableMapOf<String, Boolean>()
    private var previousManifestContent: String? = null
    private var currentStandardTracking: Map<String, List<String>> = emptyMap()
    private var currentProgressTracking: List<Pair<AdTrackingEvent, Long>> = emptyList()
    private val sentProgressOffsets: MutableSet<Long> = mutableSetOf()

    private val adPlayerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (playingAd) {
                if (state == Player.STATE_READY && !adTrackingEvents.getOrDefault("loaded", false)) {
                    sendStandardTrackingEvent("loaded")
                }
                if (state == Player.STATE_ENDED && !adTrackingEvents.getOrDefault("complete", false)) {
                    sendStandardTrackingEvent("complete")
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (playingAd) {
                if (isPlaying && playerManager.player.currentPosition == 0L) {
                    sendStandardTrackingEvent("impression")
                    sendStandardTrackingEvent("start")
                } else if (!isPlaying && playerManager.player.playbackState == Player.STATE_READY) {
                    sendStandardTrackingEvent("pause")
                } else if (isPlaying && playerManager.player.currentPosition > 0) {
                    sendStandardTrackingEvent("resume")
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (playingAd) {
                sendStandardTrackingEvent("error")
            }
        }
    }

    init {
        initializeAdTrackingClient()
        Log.i(TAG, "AdManager initialized with session ID: $sessionId")
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

        Log.d(TAG, "Ad tracking client initialized with session ID: $sessionId")
    }

    fun startMonitoring() {
        monitoringJob = CoroutineScope(Dispatchers.Main).launch {
            monitorLiveStream(mainStreamUrl)
        }
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
    }

    private suspend fun monitorLiveStream(streamUrl: String) {
        while (isActive) {
            if (!playingAd) {
                val manifestContent = withContext(Dispatchers.IO) {
                    fetchManifestContent(streamUrl)
                }
                if (!manifestContent.isNullOrEmpty()) {
                    previousManifestContent = manifestContent
                    if (manifestContent.startsWith("#EXT-X-DATERANGE") || manifestContent.contains("#EXT-X-STREAM-INF")) {
                        val variantUrl = extractVariantUrl(manifestContent, streamUrl)
                        if (variantUrl != null) {
                            val mediaPlaylistContent = withContext(Dispatchers.IO) {
                                fetchManifestContent(variantUrl)
                            }
                            if (!mediaPlaylistContent.isNullOrEmpty()) {
                                parseAdCuesFromManifest(mediaPlaylistContent)
                            }
                        }
                    } else {
                        parseAdCuesFromManifest(manifestContent)
                    }
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

    private suspend fun fetchAndInsertAds(assetListUrl: String, adBreakId: String? = null) {
        try {
            val actualAdBreakId = adBreakId ?: UUID.randomUUID().toString()
            Log.d(TAG, "Fetching ads from asset list: $assetListUrl with adBreakId: $actualAdBreakId")

            val adResponse = fetchAdAssets(assetListUrl)
            playingAd = true

            // Send podStart from podSignaling if available
            adResponse.podSignaling?.payload?.tracking?.filter { it.event == "podStart" }?.flatMap { it.urls }?.forEach {
                Log.d(TAG, "Sending podStart tracking pixel to: $it")
                sendTrackingPixel(it)
            }

            Log.d(TAG, "Ad assets fetched: ${adResponse.ASSETS.size} entries")
            Log.d(TAG, "Ad break started: $actualAdBreakId with ${adResponse.ASSETS.size} assets")

            for (adAsset in adResponse.ASSETS) {
                Log.d(TAG, "Inserting ad: ${adAsset.URI} (Duration: ${adAsset.DURATION} seconds)")
                currentAdId = adAsset.URI.hashCode().toString()
                Log.d(TAG, "Processing ad with ID: $currentAdId from URI: ${adAsset.URI}")

                val adManifestContent = getAdMediaUrl(adAsset.URI)
                if (adManifestContent != null) {
                    Log.d(TAG, "Successfully fetched ad manifest, proceeding to insert ad")
                    playAd(adManifestContent, adAsset.DURATION, adAsset)
                } else {
                    Log.e(TAG, "Failed to fetch ad manifest for ${adAsset.URI}")
                }
            }

            // Send podEnd from podSignaling if available
            adResponse.podSignaling?.payload?.tracking?.filter { it.event == "podEnd" }?.flatMap { it.urls }?.forEach {
                Log.d(TAG, "Sending podEnd tracking pixel to: $it")
                sendTrackingPixel(it)
            }

            Log.d(TAG, "** Ad break completed: $actualAdBreakId")
            playingAd = false
            resumeMainContent()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "** Error fetching or inserting ads: ${e.message}")
            playingAd = false
        }
    }

    private suspend fun getAdMediaUrl(adUri: String): String? {
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
                Log.d(TAG, "** Ad Manifest Content:\n$adManifestContent")
                extractMediaSegmentUrl(adManifestContent.toString())
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun extractMediaSegmentUrl(manifestContent: String): String? {
        val lines = manifestContent.split("\n")
        for (line in lines) {
            if (line.startsWith("http")) {
                return line.trim()
            }
        }
        return null
    }

    private suspend fun playAd(mediaSegmentUrl: String, adDuration: Long, adAsset: AdAsset) {
        adTrackingEvents.clear()
        sentProgressOffsets.clear()
        val trackingEvents = adAsset.X_AD_CREATIVE_SIGNALING?.payload?.tracking ?: emptyList()
        val standardTracking = trackingEvents.filter { it.event != "progress" }.groupBy { it.event }.mapValues { it.value.flatMap { it.urls } }
        val progressTracking = trackingEvents.filter { it.event == "progress" }.mapNotNull { event ->
            parseOffset(event.offset)?.let { offset -> Pair(event, offset) }
        }
        currentStandardTracking = standardTracking
        currentProgressTracking = progressTracking

        playerManager.player.addListener(adPlayerListener)
        playerManager.setAdMediaItem(mediaSegmentUrl)
        playerManager.play()

        CoroutineScope(Dispatchers.Main).launch {
            while (isActive && playerManager.player.playbackState != Player.STATE_ENDED) {
                val currentPosition = playerManager.player.currentPosition
                val totalDuration = adDuration * 1000
                val progressPercentage = if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()) * 100 else 0f
                Log.d(TAG, "Ad progress: $progressPercentage% ($currentPosition / ${adDuration * 1000}ms)")

                // Track quartile events
                if (progressPercentage >= 25 && !adTrackingEvents.getOrDefault("firstQuartile", false)) {
                    Log.d(TAG, "** Reached first quartile (25%)")
                    sendStandardTrackingEvent("firstQuartile")
                }
                if (progressPercentage >= 50 && !adTrackingEvents.getOrDefault("midpoint", false)) {
                    Log.d(TAG, "Reached midpoint (50%)")
                    sendStandardTrackingEvent("midpoint")
                }
                if (progressPercentage >= 75 && !adTrackingEvents.getOrDefault("thirdQuartile", false)) {
                    Log.d(TAG, "Reached third quartile (75%)")
                    sendStandardTrackingEvent("thirdQuartile")
                }

                // Track progress events
                currentProgressTracking.forEach { (event, offset) ->
                    if (currentPosition >= offset && offset !in sentProgressOffsets) {
                        event.urls.forEach { sendTrackingPixel(it) }
                        sentProgressOffsets.add(offset)
                        Log.d(TAG, "Sent progress at $offset ms")
                    }
                }

                delay(250)
            }

            // Ensure complete event is sent
            if (!adTrackingEvents.getOrDefault("complete", false)) {
                sendStandardTrackingEvent("complete")
            }
        }

        // Wait for ad to finish
        while (playerManager.player.playbackState != Player.STATE_ENDED) {
            delay(100)
        }
        playerManager.player.removeListener(adPlayerListener)
    }

    private fun parseOffset(offset: String?): Long? {
        if (offset == null) return null
        val parts = offset.split(":")
        if (parts.size == 3) {
            val hours = parts[0].toLongOrNull() ?: 0L
            val minutes = parts[1].toLongOrNull() ?: 0L
            val seconds = parts[2].toDoubleOrNull() ?: 0.0
            return ((hours * 3600 + minutes * 60 + seconds) * 1000).toLong()
        }
        return null
    }

    private fun resumeMainContent() {
        Log.d(TAG, "Resuming main content with URL: $mainStreamUrl")
        playingAd = false
        currentAdId = null
        playerManager.setMainMediaItem(mainStreamUrl)
        playerManager.play()
        Log.d(TAG, "Main content playback initiated")
    }

    fun onAdMuted(isMuted: Boolean) {
        if (playingAd) {
            val eventType = if (isMuted) "mute" else "unmute"
            sendStandardTrackingEvent(eventType)
        }
    }

    private fun sendStandardTrackingEvent(eventType: String) {
        currentStandardTracking[eventType]?.let { urls ->
            if (!adTrackingEvents.getOrDefault(eventType, false)) {
                Log.d(TAG, "Sending $eventType with URLs: $urls")
                urls.forEach { sendTrackingPixel(it) }
                adTrackingEvents[eventType] = true
            }
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

    private suspend fun fetchAdAssets(assetListUrl: String): AdResponse {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.0.2.2:3333/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val service = retrofit.create(AdProxyService::class.java)
        return service.getAdAssets(assetListUrl)
    }

    interface AdProxyService {
        @GET
        suspend fun getAdAssets(@Url assetListUrl: String): AdResponse
    }

    data class AdResponse(
        val ASSETS: List<AdAsset>,
        @SerializedName("X-AD-CREATIVE-SIGNALING")
        val podSignaling: PodSignaling? = null
    )

    data class PodSignaling(
        val version: Int,
        val type: String,
        val payload: PodPayload
    )

    data class PodPayload(
        val duration: Long,
        val tracking: List<AdTrackingEvent>? = null
    )

    data class AdAsset(
        val URI: String,
        val DURATION: Long,
        @SerializedName("X-AD-CREATIVE-SIGNALING")
        val X_AD_CREATIVE_SIGNALING: AdCreativeSignaling? = null
    )

    data class AdCreativeSignaling(
        val version: Int,
        val type: String,
        val payload: AdPayload
    )

    data class AdPayload(
        val type: String,
        val start: Long,
        val duration: Long,
        val identifiers: List<AdIdentifier>,
        val tracking: List<AdTrackingEvent>
    )

    data class AdIdentifier(
        val scheme: String,
        val value: String
    )

    data class AdTrackingEvent(
        val event: String,
        val urls: List<String>,
        val offset: String? = null
    )

    data class AdBreakTrackingEvent(
        val sessionId: String,
        val eventType: String,
        val adBreakId: String,
        val timestamp: Long
    )
}