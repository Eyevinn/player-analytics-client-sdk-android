package com.analytics.sdk

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Responsible for sending analytics events to a configured endpoint
 */
class AnalyticsEventSender(private val eventSinkUrl: String = DEFAULT_EVENT_SINK_URL) {
    private val sessionId: String = UUID.randomUUID().toString()
    private val TAG = "AnalyticsEventSender"

    companion object {
        const val DEFAULT_EVENT_SINK_URL: String = "https://default.url"
    }

    /**
     * Send a generic event with provided parameters
     */
    fun sendEvent(
        eventType: AnalyticsEventType,
        timestamp: Long,
        playhead: Long,
        duration: Long,
        payload: JSONObject? = null
    ) {
        try {
            val eventJson = JSONObject().apply {
                put("event", eventType.value)
                put("sessionId", sessionId)
                put("timestamp", timestamp)
                put("playhead", playhead)
                put("duration", duration)
                payload?.let { put("payload", it) }
            }
            Log.d(TAG, "Sending event: $eventJson")
            sendToEventSink(eventJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending event", e)
        }
    }

    /**
     * Send initialization event
     */
    fun sendInitEvent(expectedStartTime: Long) {
        sendEvent(AnalyticsEventType.INIT, System.currentTimeMillis(), expectedStartTime, -1)
    }

    /**
     * Send metadata about the content being played
     */
    @Throws(JSONException::class)
    fun sendMetadataEvent(isLive: Boolean, contentTitle: String?, deviceType: String) {
        val payload = JSONObject().apply {
            put("live", isLive)
            put("contentTitle", contentTitle)
            put("deviceType", deviceType)
        }
        sendEvent(AnalyticsEventType.METADATA, System.currentTimeMillis(), 0, 0, payload)
    }

    /**
     * Send heartbeat event indicating ongoing playback
     */
    fun sendHeartbeatEvent(playhead: Long, duration: Long) {
        sendEvent(AnalyticsEventType.HEARTBEAT, System.currentTimeMillis(), playhead, duration)
    }

    /**
     * Send event when content begins loading
     */
    fun sendLoadingEvent() {
        sendEvent(AnalyticsEventType.LOADING, System.currentTimeMillis(), 0, 0)
    }

    /**
     * Send event when content has loaded
     */
    fun sendLoadedEvent() {
        sendEvent(AnalyticsEventType.LOADED, System.currentTimeMillis(), 0, 0)
    }

    /**
     * Send event when content begins playing
     */
    fun sendPlayingEvent(playhead: Long, duration: Long) {
        sendEvent(AnalyticsEventType.PLAYING, System.currentTimeMillis(), playhead, duration)
    }

    /**
     * Send event when content is paused
     */
    fun sendPausedEvent(playhead: Long, duration: Long) {
        sendEvent(AnalyticsEventType.PAUSED, System.currentTimeMillis(), playhead, duration)
    }

    /**
     * Send event when content begins buffering
     */
    fun sendBufferingEvent(playhead: Long, duration: Long) {
        sendEvent(AnalyticsEventType.BUFFERING, System.currentTimeMillis(), playhead, duration)
    }

    /**
     * Send event when content finishes buffering
     */
    fun sendBufferedEvent(playhead: Long, duration: Long) {
        sendEvent(AnalyticsEventType.BUFFERED, System.currentTimeMillis(), playhead, duration)
    }

    /**
     * Send event when user begins seeking
     */
    fun sendSeekingEvent(playhead: Long, duration: Long) {
        sendEvent(AnalyticsEventType.SEEKING, System.currentTimeMillis(), playhead, duration)
    }

    /**
     * Send event when seeking is completed
     */
    fun sendSeekedEvent(playhead: Long, duration: Long) {
        sendEvent(AnalyticsEventType.SEEKED, System.currentTimeMillis(), playhead, duration)
    }

    /**
     * Send event when video bitrate changes
     */
    @Throws(JSONException::class)
    fun sendBitrateChangedEvent(
        playhead: Long,
        duration: Long,
        bitrate: Int,
        width: Int? = null,
        height: Int? = null,
        videoBitrate: Int? = null,
        audioBitrate: Int? = null
    ) {
        val payload = JSONObject().apply {
            put("bitrate", bitrate)
            width?.let { put("width", it) }
            height?.let { put("height", it) }
            videoBitrate?.let { put("videoBitrate", it) }
            audioBitrate?.let { put("audioBitrate", it) }
        }
        sendEvent(AnalyticsEventType.BITRATE_CHANGED, System.currentTimeMillis(), playhead, duration, payload)
    }

    /**
     * Send event when playback stops
     */
    @Throws(JSONException::class)
    fun sendStoppedEvent(playhead: Long, duration: Long, reason: String?) {
        val payload = JSONObject().apply {
            put("reason", reason)
        }
        sendEvent(AnalyticsEventType.STOPPED, System.currentTimeMillis(), playhead, duration, payload)
    }

    /**
     * Send event when an error occurs
     */
    @Throws(JSONException::class)
    fun sendErrorEvent(
        playhead: Long,
        duration: Long,
        category: String?,
        code: String?,
        message: String?
    ) {
        val payload = JSONObject().apply {
            put("category", category)
            put("code", code)
            put("message", message)
        }
        sendEvent(AnalyticsEventType.ERROR, System.currentTimeMillis(), playhead, duration, payload)
    }

    private fun sendToEventSink(eventJson: JSONObject) {
        Thread {
            try {
                (URL(eventSinkUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    outputStream.use { os ->
                        os.write(eventJson.toString().toByteArray(Charsets.UTF_8))
                    }
                    val responseCode = this.responseCode
                    if (responseCode !in 200..299) {
                        Log.w(TAG, "Event sink returned error code: $responseCode")
                    }
                    disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send event to sink", e)
            }
        }.start()
    }
}