package com.analytics.sdk

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.NonCancellable.isActive

/**
 * Interface for player to communicate playback state to SDK and receive ad playback instructions.
 */


/**
 * Playback states to align with common player implementations (e.g., ExoPlayer).
 */
object PlaybackState {
    const val STATE_IDLE = 1
    const val STATE_BUFFERING = 2
    const val STATE_READY = 3
    const val STATE_ENDED = 4
}

/**
 * Main SDK class for monitoring manifests and tracking ad impressions.
 */
class AdTrackingSDK(
    private val context: Context,
    private val mainStreamUrl: String,
    private val playerCallback: PlayerCallback
) {
    private val TAG = "SGAI_AdTrackingSDK"
    private val sessionId = UUID.randomUUID().toString()
    private var isPlayingAd = false
    private var currentAdId: String? = null
    private var currentAdDuration: Long = 0L
    private val manifestRefreshInterval = 15_000L // Poll every 15 seconds
    private var monitoringJob: Job? = null
    private val adTrackingEvents = mutableMapOf<String, Boolean>()
    private val sentProgressOffsets = mutableSetOf<Long>()
    private var currentStandardTracking: Map<String, List<String>> = emptyMap()
    private val retrofit: Retrofit

    init {
        retrofit = initializeRetrofit()
        Log.i(TAG, "AdTrackingSDK initialized with session ID: $sessionId")
    }

    /**
     * Initialize Retrofit for ad asset fetching.
     */
    private fun initializeRetrofit(): Retrofit {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("http://10.0.2.2:3333/") // Base URL for ad assets
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Start monitoring the manifest and ad playback.
     */
    fun startMonitoring() {
        monitoringJob = CoroutineScope(Dispatchers.Main).launch {
            monitorLiveStream(mainStreamUrl)
        }
        Log.d(TAG, "Started manifest monitoring for: $mainStreamUrl")
    }

    /**
     * Stop monitoring the manifest.
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        Log.d(TAG, "Stopped manifest monitoring")
    }

    /**
     * Poll the manifest periodically and process ad cues.
     */
    private suspend fun monitorLiveStream(streamUrl: String) {
        while (isActive) {
            if (!isPlayingAd) {
                val manifestContent = withContext(Dispatchers.IO) {
                    fetchManifestContent(streamUrl)
                }
                if (!manifestContent.isNullOrEmpty()) {
                    if (manifestContent.contains("#EXT-X-DATERANGE") || manifestContent.contains("#EXT-X-STREAM-INF")) {
                        val variantUrl = extractVariantUrl(manifestContent, streamUrl)
                        if (variantUrl != null) {
                            val mediaPlaylistContent = withContext(Dispatchers.IO) {
                                fetchManifestContent(variantUrl)
                            }
                            if (!mediaPlaylistContent.isNullOrEmpty()) {
                                parseAdCues(mediaPlaylistContent)
                            }
                        }
                    } else {
                        parseAdCues(manifestContent)
                    }
                }
            }
            delay(manifestRefreshInterval)
        }
    }

    /**
     * Fetch manifest content from the provided URL.
     */
    private fun fetchManifestContent(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val inputStream = connection.inputStream
            val content = inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            content
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch manifest: ${e.message}")
            null
        }
    }

    /**
     * Extract variant URL from master playlist.
     */
    private fun extractVariantUrl(manifestContent: String, baseUrl: String): String? {
        val lines = manifestContent.split("\n")
        for (i in lines.indices) {
            if (lines[i].startsWith("#EXT-X-DATERANGE") || lines[i].startsWith("#EXT-X-STREAM-INF")) {
                return lines.getOrNull(i + 1)?.let { resolveUrl(baseUrl, it) }
            }
        }
        return null
    }

    /**
     * Resolve relative URLs against the base URL.
     */
    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return if (relativeUrl.startsWith("http")) {
            relativeUrl
        } else {
            val baseUri = Uri.parse(baseUrl)
            val basePath = baseUri.path?.substringBeforeLast('/') ?: ""
            baseUri.buildUpon()
                .path("$basePath/$relativeUrl")
                .build()
                .toString()
        }
    }

    /**
     * Parse ad cues from the manifest and process ad breaks.
     */
    private suspend fun parseAdCues(manifestContent: String) {
        val lines = manifestContent.split("\n")
        var adBreakId: String? = null
        for (line in lines) {
            if (line.startsWith("#EXT-X-DATERANGE")) {
                adBreakId = extractAdBreakId(line)
                val assetListUrl = extractAssetListUrl(line)?.replace("localhost", "10.0.2.2")
                if (assetListUrl != null) {
                    fetchAndPlayAds(assetListUrl, adBreakId)
                }
            }
        }
    }

    /**
     * Extract ad break ID from DATERANGE tag.
     */
    private fun extractAdBreakId(line: String): String {
        val attributes = line.split(",")
        for (attribute in attributes) {
            if (attribute.contains("ID=")) {
                return attribute.substringAfter("ID=\"").substringBefore("\"")
            }
        }
        return UUID.randomUUID().toString()
    }

    /**
     * Extract asset list URL from DATERANGE tag.
     */
    private fun extractAssetListUrl(line: String): String? {
        val attributes = line.split(",")
        for (attribute in attributes) {
            if (attribute.startsWith("X-ASSET-LIST=")) {
                return attribute.substringAfter("X-ASSET-LIST=\"").substringBefore("\"")
            }
        }
        return null
    }

    /**
     * Fetch ad assets and play them.
     */
    private suspend fun fetchAndPlayAds(assetListUrl: String, adBreakId: String) {
        try {
            val adResponse = retrofit.create(AdService::class.java).getAdAssets(assetListUrl)
            isPlayingAd = true
            Log.d(TAG, "Ad break started: $adBreakId with ${adResponse.assets.size} ads")

            // Send podStart tracking events
            adResponse.podSignaling?.payload?.tracking?.filter { it.type == "podStart" }
                ?.flatMap { it.urls }?.forEach { sendTrackingPixel(it) }

            // Play each ad in sequence
            val sortedAds = adResponse.assets.sortedBy { it.signaling?.payload?.start ?: 0L }
            for (ad in sortedAds) {
                currentAdId = ad.uri.hashCode().toString()
                currentAdDuration = ad.duration
                val adUrl = fetchAdMediaUrl(ad.uri)
                if (adUrl != null) {
                    playAd(adUrl, ad.duration, ad.signaling?.payload?.tracking ?: emptyList())
                } else {
                    Log.e(TAG, "Failed to fetch ad media URL for ${ad.uri}")
                }
            }

            // Send podEnd tracking events
            adResponse.podSignaling?.payload?.tracking?.filter { it.type == "podEnd" }
                ?.flatMap { it.urls }?.forEach { sendTrackingPixel(it) }

            isPlayingAd = false
            currentAdId = null
            currentAdDuration = 0L
            playerCallback.resumeMainContent()
            Log.d(TAG, "Ad break completed: $adBreakId")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing ad break: ${e.message}")
            isPlayingAd = false
            playerCallback.resumeMainContent()
        }
    }

    /**
     * Fetch the actual media URL for an ad.
     */
    private suspend fun fetchAdMediaUrl(adUri: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val manifestContent = fetchManifestContent(adUri)
                manifestContent?.let { extractMediaSegmentUrl(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch ad media: ${e.message}")
                null
            }
        }
    }

    /**
     * Extract media segment URL from ad manifest.
     */
    private fun extractMediaSegmentUrl(manifestContent: String): String? {
        return manifestContent.split("\n").firstOrNull { it.startsWith("http") }?.trim()
    }

    /**
     * Play an ad and track its progress.
     */
    private suspend fun playAd(adUrl: String, duration: Long, trackingEvents: List<AdTrackingEvent>) {
        adTrackingEvents.clear()
        sentProgressOffsets.clear()
        currentStandardTracking = trackingEvents.groupBy { it.type }.mapValues { it.value.flatMap { it.urls } }

        playerCallback.playAd(adUrl, duration)

        // Monitor ad progress
        val progressJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && playerCallback.getPlaybackState() != PlaybackState.STATE_ENDED) {
                val currentPosition = playerCallback.getCurrentPosition()
                val totalDuration = duration * 1000L
                val progressPercentage = if (totalDuration > 0) {
                    (currentPosition.toFloat() / totalDuration.toFloat()) * 100
                } else 0f

                // Log progress for debugging
                Log.d(TAG, "Ad progress: $progressPercentage% ($currentPosition/${totalDuration}ms)")

                // Handle playback state changes
                when (playerCallback.getPlaybackState()) {
                    PlaybackState.STATE_READY -> {
                        if (!adTrackingEvents.getOrDefault("loaded", false)) {
                            sendStandardTrackingEvent("loaded")
                        }
                        if (currentPosition == 0L && !adTrackingEvents.getOrDefault("impression", false)) {
                            sendStandardTrackingEvent("impression")
                            sendStandardTrackingEvent("start")
                        }
                    }
                    PlaybackState.STATE_ENDED -> {
                        if (!adTrackingEvents.getOrDefault("complete", false)) {
                            sendStandardTrackingEvent("complete")
                        }
                    }
                }

                // Track quartile events
                when {
                    progressPercentage >= 25 && !adTrackingEvents.getOrDefault("firstQuartile", false) -> {
                        sendStandardTrackingEvent("firstQuartile")
                    }
                    progressPercentage >= 50 && !adTrackingEvents.getOrDefault("midpoint", false) -> {
                        sendStandardTrackingEvent("midpoint")
                    }
                    progressPercentage >= 75 && !adTrackingEvents.getOrDefault("thirdQuartile", false) -> {
                        sendStandardTrackingEvent("thirdQuartile")
                    }
                    progressPercentage >= 95 && !adTrackingEvents.getOrDefault("complete", false) -> {
                        sendStandardTrackingEvent("complete")
                    }
                }

                delay(250)
            }
        }

        // Wait for ad to complete
        while (playerCallback.getPlaybackState() != PlaybackState.STATE_ENDED) {
            delay(100)
        }

        progressJob.cancel()
        if (!adTrackingEvents.getOrDefault("complete", false)) {
            sendStandardTrackingEvent("complete")
        }
    }

    /**
     * Send a standard tracking event.
     */
    private fun sendStandardTrackingEvent(eventType: String) {
        currentStandardTracking[eventType]?.let { urls ->
            if (!adTrackingEvents.getOrDefault(eventType, false)) {
                Log.d(TAG, "Sending $eventType event with ${urls.size} URLs")
                urls.forEach { sendTrackingPixel(it) }
                adTrackingEvents[eventType] = true
            }
        }
    }

    /**
     * Send a tracking pixel request.
     */
    private fun sendTrackingPixel(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val decodedUrl = java.net.URLDecoder.decode(url, "UTF-8")
                val connection = URL(decodedUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "SGAI-AdTrackingSDK/1.0")
                connection.connect()
                val responseCode = connection.responseCode
                Log.d(TAG, "Tracking pixel sent to $decodedUrl: $responseCode")
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send tracking pixel to $url: ${e.message}")
            }
        }
    }

    /**
     * Handle mute/unmute events.
     */
    fun onAdMuted(isMuted: Boolean) {
        if (isPlayingAd) {
            sendStandardTrackingEvent(if (isMuted) "mute" else "unmute")
        }
    }

    /**
     * Release resources.
     */
    fun release() {
        stopMonitoring()
        Log.d(TAG, "AdTrackingSDK released")
    }

    /**
     * Retrofit service interface for fetching ad assets.
     */
    interface AdService {
        @GET
        suspend fun getAdAssets(@Url url: String): AdResponse
    }

    /**
     * Data classes for parsing ad responses.
     */
    data class AdResponse(
        @SerializedName("ASSETS") val assets: List<AdAsset>,
        @SerializedName("X-AD-CREATIVE-SIGNALING") val podSignaling: PodSignaling? = null
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
        @SerializedName("URI") val uri: String,
        @SerializedName("DURATION") val duration: Long,
        @SerializedName("X-AD-CREATIVE-SIGNALING") val signaling: AdCreativeSignaling? = null
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
        val type: String,
        val urls: List<String>,
        val offset: String? = null
    )
}