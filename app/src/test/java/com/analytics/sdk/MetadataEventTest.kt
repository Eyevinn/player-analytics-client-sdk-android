package com.analytics.sdk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MetadataEventTest {

    private fun capturePayload(send: (AnalyticsEventSender) -> Unit): JSONObject {
        val done = CountDownLatch(1)
        var captured: JSONObject? = null
        val sender = AnalyticsEventSender("https://sink.test/") { _, body ->
            captured = JSONObject(body)
            done.countDown()
        }
        send(sender)
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return captured!!
    }

    @Test
    fun `metadata event carries the device class and device model`() {
        val event = capturePayload {
            it.sendMetadataEvent(
                isLive = false,
                contentTitle = "Sample Video",
                deviceType = "tablet",
                deviceModel = "Pixel Tablet"
            )
        }

        assertEquals("metadata", event.getString("event"))
        val payload = event.getJSONObject("payload")
        assertEquals("tablet", payload.getString("deviceType"))
        assertEquals("Pixel Tablet", payload.getString("deviceModel"))
    }

    @Test
    fun `device model is omitted when not provided`() {
        val event = capturePayload {
            it.sendMetadataEvent(isLive = true, contentTitle = null, deviceType = "mobile")
        }

        val payload = event.getJSONObject("payload")
        assertEquals("mobile", payload.getString("deviceType"))
        assertFalse(payload.has("deviceModel"))
    }
}
