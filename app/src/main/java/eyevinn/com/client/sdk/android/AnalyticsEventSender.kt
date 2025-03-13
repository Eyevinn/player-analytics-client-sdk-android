package eyevinn.com.client.sdk.android.analytics

import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

class AnalyticsEventSender {
    private val sessionId: String = UUID.randomUUID().toString()

    fun sendEvent(
        eventType: String?,
        timestamp: Long,
        playhead: Long,
        duration: Long,
        payload: JSONObject? = null
    ) {
        try {
            val eventJson = JSONObject().apply {
                put("event", eventType)
                put("sessionId", sessionId)
                put("timestamp", timestamp)
                put("playhead", playhead)
                put("duration", duration)
                payload?.let { put("payload", it) }
            }
            println("******* Sending event: $eventJson")
            sendToEventSink(eventJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendInitEvent(expectedStartTime: Long) {
        sendEvent("init", System.currentTimeMillis(), expectedStartTime, -1)
    }

    @Throws(JSONException::class)
    fun sendMetadataEvent(isLive: Boolean, contentTitle: String?) {
        val payload = JSONObject().apply {
            put("live", isLive)
            put("contentTitle", contentTitle)
        }
        sendEvent("metadata", System.currentTimeMillis(), 0, 0, payload)
    }

    fun sendHeartbeatEvent(playhead: Long, duration: Long) {
        sendEvent("heartbeat", System.currentTimeMillis(), playhead, duration)
    }

    fun sendLoadingEvent() {
        sendEvent("loading", System.currentTimeMillis(), 0, 0)
    }

    fun sendPlayingEvent(playhead: Long, duration: Long) {
        sendEvent("playing", System.currentTimeMillis(), playhead, duration)
    }

    fun sendPausedEvent(playhead: Long, duration: Long) {
        sendEvent("paused", System.currentTimeMillis(), playhead, duration)
    }

    fun sendBufferingEvent(playhead: Long, duration: Long) {
        sendEvent("buffering", System.currentTimeMillis(), playhead, duration)
    }

    fun sendSeekingEvent(playhead: Long, duration: Long) {
        sendEvent("seeking", System.currentTimeMillis(), playhead, duration)
    }

    @Throws(JSONException::class)
    fun sendStoppedEvent(playhead: Long, duration: Long, reason: String?) {
        val payload = JSONObject().apply {
            put("reason", reason)
        }
        sendEvent("stopped", System.currentTimeMillis(), playhead, duration, payload)
    }

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
        sendEvent("error", System.currentTimeMillis(), playhead, duration, payload)
    }

    private fun sendToEventSink(eventJson: JSONObject) {
        println("******* Sending event: $eventJson")
    }
}