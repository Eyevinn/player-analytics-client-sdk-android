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
     * Send multiple impression pixels for the same event
     */
    fun sendMultipleImpressions(urls: List<String>, eventType: SGAIAdTrackingEvent, adKey: String) {
        urls.forEach { url ->
            sendTrackingPixel(url, eventType.eventName, adKey)
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
                Log.d(TAG, "[$eventType] Tracking sent for $url: $responseCode")
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send [$eventType] tracking for $adKey: ${e.message}")
            }
        }
    }
}