package com.analytics.sdk

import android.util.Log
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.UUID

/**
 * Responsible for sending SGAI ad impression tracking pixels
 */
class SGAIAdImpressionSender {
    private val sessionId: String = UUID.randomUUID().toString()
    private val TAG = "SGAIAdImpressionSender"

    /**
     * Send impression tracking pixel for specific ad and event type
     */
    fun sendImpressionPixel(url: String, eventType: SGAIAdTrackingEvent, adKey: String) {
        sendTrackingPixel(url, eventType.eventName, adKey)
    }

    /**
     * Send impression tracking for loaded event
     */
    fun sendLoadedImpression(url: String, adKey: String) {
        sendTrackingPixel(url, SGAIAdTrackingEvent.LOADED.eventName, adKey)
    }

    /**
     * Send impression tracking for impression event
     */
    fun sendImpressionImpression(url: String, adKey: String) {
        sendTrackingPixel(url, SGAIAdTrackingEvent.IMPRESSION.eventName, adKey)
    }

    /**
     * Send impression tracking for start event
     */
    fun sendStartImpression(url: String, adKey: String) {
        sendTrackingPixel(url, SGAIAdTrackingEvent.START.eventName, adKey)
    }

    /**
     * Send impression tracking for first quartile event
     */
    fun sendFirstQuartileImpression(url: String, adKey: String) {
        sendTrackingPixel(url, SGAIAdTrackingEvent.FIRST_QUARTILE.eventName, adKey)
    }

    /**
     * Send impression tracking for midpoint event
     */
    fun sendMidpointImpression(url: String, adKey: String) {
        sendTrackingPixel(url, SGAIAdTrackingEvent.MIDPOINT.eventName, adKey)
    }

    /**
     * Send impression tracking for third quartile event
     */
    fun sendThirdQuartileImpression(url: String, adKey: String) {
        sendTrackingPixel(url, SGAIAdTrackingEvent.THIRD_QUARTILE.eventName, adKey)
    }

    /**
     * Send impression tracking for complete event
     */
    fun sendCompleteImpression(url: String, adKey: String) {
        sendTrackingPixel(url, SGAIAdTrackingEvent.COMPLETE.eventName, adKey)
    }

    /**
     * Send impression tracking for pause event
     */
    fun sendPauseImpression(url: String, adKey: String) {
        sendTrackingPixel(url, SGAIAdTrackingEvent.PAUSE.eventName, adKey)
    }

    /**
     * Send impression tracking for resume event
     */
    fun sendResumeImpression(url: String, adKey: String) {
        sendTrackingPixel(url, SGAIAdTrackingEvent.RESUME.eventName, adKey)
    }

    /**
     * Send impression tracking for pod start event
     */
    fun sendPodStartImpression(url: String, podKey: String) {
        sendTrackingPixel(url, SGAIAdTrackingEvent.POD_START.eventName, podKey)
    }

    /**
     * Send impression tracking for pod complete event
     */
    fun sendPodEndImpression(url: String, podKey: String) {
        sendTrackingPixel(url, SGAIAdTrackingEvent.POD_END.eventName, podKey)
    }

    /**
     * Send multiple impression pixels for the same event
     */
    fun sendMultipleImpressions(urls: List<String>, eventType: SGAIAdTrackingEvent, adKey: String) {
        urls.forEach { url ->
            sendTrackingPixel(url, eventType.eventName, adKey)
        }
    }

    /**
     * Send impression tracking for specific ad with list of URLs
     */
    fun sendImpressionTracking(adKey: String, trackingUrls: Map<String, List<String>>) {
        trackingUrls.forEach { (eventType, urls) ->
            SGAIAdTrackingEvent.fromEventName(eventType)?.let { event ->
                urls.forEach { url ->
                    sendTrackingPixel(url, eventType, adKey)
                }
            }
        }
    }

    /**
     * Core method to send tracking pixel
     */
    private fun sendTrackingPixel(url: String, eventType: String, adKey: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val decodedUrl = URLDecoder.decode(url, "UTF-8")
                val connection = URL(decodedUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "SGAI-AdTracker/1.0")
                connection.setRequestProperty("X-Session-ID", sessionId)
                connection.setRequestProperty("X-Ad-Key", adKey)
                connection.connect()
                val responseCode = connection.responseCode
                Log.d(TAG, "[$eventType] Tracking sent for $adKey: $responseCode")
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send [$eventType] tracking for $adKey: ${e.message}")
            }
        }
    }
}