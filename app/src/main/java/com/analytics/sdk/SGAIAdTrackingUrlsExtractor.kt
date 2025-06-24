package com.analytics.sdk

import android.net.Uri
import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.UUID
import kotlinx.coroutines.NonCancellable.isActive

private const val STREAM_BASE_URL = "http://10.0.2.2:3333/"

class SGAIAdTrackingUrlsExtractor(
    private val streamUrl: String,
    private val onAdBreakFound: ((adUri: String) -> Unit)? = null
) {
    private val TAG = "SGAIAdTrackingUrlsExtractor"

    private val adTrackingUrlsMap: MutableMap<String, Map<String, List<String>>> = mutableMapOf()
    private var currentAdsId: String = "ad-session-1"

    // Track pod events
    private val podTrackingCallbacks: MutableList<(String, String) -> Unit> = mutableListOf()

    private fun extractAssetUri(line: String): String? {
        val attributes = line.split(",")
        for (attribute in attributes) {
            if (attribute.startsWith("X-ASSET-URI=")) {
                return attribute.substringAfter("X-ASSET-URI=\"").substringBefore("\"")
            }
        }
        return null
    }

    fun setAdsId(adsId: String) {
        currentAdsId = adsId
        Log.d(TAG, "Set ads session ID to: $adsId")
    }

    fun addPodTrackingCallback(callback: (eventType: String, podId: String) -> Unit) {
        podTrackingCallbacks.add(callback)
    }

    suspend fun processManifest() {
        Log.d(TAG, "Processing manifest for: $streamUrl")

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

    private fun extractVariantUrl(manifestContent: String, baseUrl: String): String? {
        val lines = manifestContent.split("\n")
        for (i in lines.indices) {
            if (lines[i].startsWith("#EXT-X-DATERANGE") || lines[i].startsWith("#EXT-X-STREAM-INF")) {
                return lines.getOrNull(i + 1)?.let { resolveUrl(baseUrl, it) }
            }
        }
        return null
    }

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

    private suspend fun parseAdCues(manifestContent: String) {
        val lines = manifestContent.split("\n")
        var adBreakId: String? = null

        for (line in lines) {
            if (line.startsWith("#EXT-X-DATERANGE")) {
                adBreakId = extractAdBreakId(line)

                // Check for X-ASSET-LIST (live/future SGAI)
                val assetListUrl = extractAssetListUrl(line)?.replace("localhost", "10.0.2.2")
                if (assetListUrl != null) {
                    Log.d(TAG, "Found ad break: $adBreakId with asset list: $assetListUrl")
                    extractTrackingUrlsFromAssetList(assetListUrl, adBreakId)
                }

                // Check for X-ASSET-URI (VoD SGAI, supported by ExoPlayer today)
                val assetUri = extractAssetUri(line)
                if (assetUri != null) {
                    Log.d(TAG, "Found ad break: $adBreakId with asset URI: $assetUri")
                    extractTrackingUrlsFromAssetList(assetUri, adBreakId)


                    println("###### AdBreak found: $adBreakId with asset URI: $assetUri")
                    // ----- CALL THE CALLBACK! -----
                    onAdBreakFound?.invoke(assetUri)
                }
            }
        }
    }


    private fun extractAdBreakId(line: String): String {
        val attributes = line.split(",")
        for (attribute in attributes) {
            if (attribute.contains("ID=")) {
                return attribute.substringAfter("ID=\"").substringBefore("\"")
            }
        }
        return UUID.randomUUID().toString()
    }

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
     * Extract tracking URLs from asset list URL and store in map
     */
    private suspend fun extractTrackingUrlsFromAssetList(assetListUrl: String, adBreakId: String) {
        try {
            val adResponse = fetchAdAssets(assetListUrl)
            processAdResponse(adResponse, adBreakId)
            Log.d("TrackingDebug", "Parsed adTrackingUrlsMap: $adTrackingUrlsMap")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract tracking URLs from $assetListUrl: ${e.message}")
        }
    }

    /**
     * Fetch ad assets from URL
     */
    private suspend fun fetchAdAssets(url: String): AdResponse {
        val retrofit = Retrofit.Builder()
            .baseUrl(STREAM_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(AdService::class.java).getAdAssets(url)
    }

    /**
     * Process ad response and create ad keys that match PlayerManager format
     */
    private fun processAdResponse(adResponse: AdResponse, adBreakId: String) {
        // Process pod-level tracking
        adResponse.podSignaling?.payload?.tracking?.let { podTracking ->
            val podTrackingMap = groupTrackingByType(podTracking)
            if (podTrackingMap.isNotEmpty()) {
                val podKey = "pod_${adBreakId}"
                adTrackingUrlsMap[podKey] = podTrackingMap
                Log.d(TAG, "Pod tracking URLs extracted for $podKey: ${podTrackingMap.keys}")
                triggerPodEvent(SGAIAdTrackingEvent.POD_START.eventName, podKey)
            }
        }

        // Process individual ad tracking with PlayerManager-compatible keys
        adResponse.assets.forEachIndexed { index, ad ->
            val adKey = "${currentAdsId}_0_${index}"

            ad.signaling?.payload?.tracking?.let { adTracking ->
                val adTrackingMap = groupTrackingByType(adTracking)
                if (adTrackingMap.isNotEmpty()) {
                    adTrackingUrlsMap[adKey] = adTrackingMap
                    Log.d(TAG, "Ad $adKey tracking URLs extracted: ${adTrackingMap.keys}")
                }
            }
        }

    }

    private fun triggerPodEvent(eventType: String, podId: String) {
        podTrackingCallbacks.forEach { callback ->
            callback(eventType, podId)
        }
    }

    private fun groupTrackingByType(trackingEvents: List<AdTrackingEvent>): Map<String, List<String>> {
        return trackingEvents.groupBy { it.type }
            .mapValues { entry -> entry.value.flatMap { it.urls } }
            .filterValues { it.isNotEmpty() }
    }

    fun getTrackingUrlsForAd(adId: String): Map<String, List<String>>? {
        return adTrackingUrlsMap[adId]
    }

    fun getAllTrackingUrls(): Map<String, Map<String, List<String>>> {
        return adTrackingUrlsMap.toMap()
    }

    fun sendPodEndTracking(podId: String) {
        adTrackingUrlsMap[podId]?.get(SGAIAdTrackingEvent.POD_END.eventName)?.forEach { url ->
            sendTrackingPixel(url, SGAIAdTrackingEvent.POD_END.eventName)
        }
        triggerPodEvent(SGAIAdTrackingEvent.POD_END.eventName, podId)
    }

    private fun sendTrackingPixel(url: String, eventType: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val decodedUrl = URLDecoder.decode(url, "UTF-8")
                val connection = URL(decodedUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "AdTracker/1.0")
                connection.connect()
                val responseCode = connection.responseCode
                Log.d(TAG, "[$eventType] Tracking sent: $responseCode")
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send [$eventType] tracking: ${e.message}")
            }
        }
    }

    fun release() {
        podTrackingCallbacks.clear()
        Log.d(TAG, "AdExtractor released")
    }

    interface AdService {
        @GET
        suspend fun getAdAssets(@Url url: String): AdResponse
    }

    data class AdResponse(
        @SerializedName("ASSETS") val assets: List<AdAsset>,
        @SerializedName("X-AD-CREATIVE-SIGNALING") val podSignaling: PodSignaling? = null
    )

    data class PodSignaling(
        val payload: PodPayload
    )

    data class PodPayload(
        val tracking: List<AdTrackingEvent>? = null
    )

    data class AdAsset(
        @SerializedName("URI") val uri: String,
        @SerializedName("X-AD-CREATIVE-SIGNALING") val signaling: AdCreativeSignaling? = null
    )

    data class AdCreativeSignaling(
        val payload: AdPayload
    )

    data class AdPayload(
        val tracking: List<AdTrackingEvent>
    )

    data class AdTrackingEvent(
        val type: String,
        val urls: List<String>
    )
}