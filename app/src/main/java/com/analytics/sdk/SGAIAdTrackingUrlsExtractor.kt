package com.analytics.sdk

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

private const val STREAM_BASE_URL = ""

class SGAIAdTrackingUrlsExtractor(
    private val onAdBreakFound: ((adUri: String) -> Unit)? = null
) {
    private val TAG = "SGAIAdTrackingUrlsExtractor"

    private val adTrackingUrlsMap: MutableMap<String, Map<String, List<String>>> = mutableMapOf()
    var currentAdsId: String = "ad-session-1"

    // Track pod events
    private val podTrackingCallbacks: MutableList<(String, String) -> Unit> = mutableListOf()

    /**
     * Public method to directly extract tracking URLs from asset list URL
     * This is what you should call when you have the assetListUri from ExoPlayer
     *
     * @param assetListUrl The URL to fetch ad assets from
     * @param adBreakId The ID of the ad break
     * @return Success status of the extraction
     */
    suspend fun extractTrackingUrls(assetListUrl: String, adBreakId: String): Boolean {
        return try {
            extractTrackingUrlsFromAssetList(assetListUrl, adBreakId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract tracking URLs from $assetListUrl: ${e.message}")
            false
        }
    }

    /**
     * Extract tracking URLs from asset list URL and store in map
     */
    private suspend fun extractTrackingUrlsFromAssetList(assetListUrl: String, adBreakId: String) {
        val adResponse = fetchAdAssets(assetListUrl)
        processAdResponse(adResponse, adBreakId)
        Log.d("TrackingDebug", "Parsed adTrackingUrlsMap: $adTrackingUrlsMap")
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

    private fun processAdResponse(adResponse: AdResponse, adBreakId: String) {
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

        // Invoke callback for ad break found if available
        adResponse.assets.firstOrNull()?.uri?.let { assetUri ->
            onAdBreakFound?.invoke(assetUri)
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

    /**
     * Get tracking URLs for a specific ad ID
     */
    fun getTrackingUrlsForAd(adId: String): Map<String, List<String>>? {
        return adTrackingUrlsMap[adId]
    }

    /**
     * Get tracking URLs for an ad by its index in the current session
     */
    fun getTrackingUrlsForAdIndex(adIndex: Int): Map<String, List<String>>? {
        val adKey = "${currentAdsId}_0_${adIndex}"
        return adTrackingUrlsMap[adKey]
    }

    /**
     * Get all tracking URLs
     */
    fun getAllTrackingUrls(): Map<String, Map<String, List<String>>> {
        return adTrackingUrlsMap.toMap()
    }

    /**
     * Send pod end tracking
     */
    fun sendPodEndTracking(podId: String) {
        adTrackingUrlsMap[podId]?.get(SGAIAdTrackingEvent.POD_END.eventName)?.forEach { url ->
            sendTrackingPixel(url, SGAIAdTrackingEvent.POD_END.eventName)
        }
        triggerPodEvent(SGAIAdTrackingEvent.POD_END.eventName, podId)
    }

    /**
     * Send tracking pixel for ad events
     */
    fun sendAdTracking(adKey: String, eventType: String) {
        val trackingUrls = getTrackingUrlsForAd(adKey)
        trackingUrls?.get(eventType)?.forEach { url ->
            Log.d(TAG, "Sending $eventType tracking for $adKey: $url")
            sendTrackingPixel(url, eventType)
        }
    }

    /**
     * Send tracking pixel for ad events by index
     */
    fun sendAdTrackingByIndex(adIndex: Int, eventType: String) {
        val adKey = "${currentAdsId}_0_${adIndex}"
        sendAdTracking(adKey, eventType)
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
        adTrackingUrlsMap.clear()
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
        @SerializedName("DURATION") val duration: Int? = null,
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
