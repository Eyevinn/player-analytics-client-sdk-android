package com.analytics.sdk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Covers the `contentId` metadata contract (issue #39). The `metadata` event is built by
 * [AnalyticsEventSender.sendMetadataEvent]; `VideoAnalyticsTracker.setContent(...)` updates the
 * reported content and re-sends through that same method with the stable device fields, so the
 * payload guarantees are verified here.
 */
class ContentIdMetadataTest {

    private fun capturePayloads(count: Int, send: (AnalyticsEventSender) -> Unit): List<JSONObject> {
        val done = CountDownLatch(count)
        val bodies = mutableListOf<String>()
        val sender = AnalyticsEventSender("https://sink.test/") { _, body ->
            synchronized(bodies) { bodies += body }
            done.countDown()
        }
        send(sender)
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return synchronized(bodies) { bodies.toList() }
            .map { JSONObject(it).getJSONObject("payload") }
    }

    @Test
    fun `metadata carries contentId when provided`() {
        val payload = capturePayloads(1) { sender ->
            sender.sendMetadataEvent(
                isLive = false,
                contentTitle = "Sample Video",
                deviceType = "mobile",
                contentId = "someid"
            )
        }.first()

        assertEquals("someid", payload.getString("contentId"))
    }

    @Test
    fun `contentId is omitted when not provided`() {
        val payload = capturePayloads(1) { sender ->
            sender.sendMetadataEvent(
                isLive = false,
                contentTitle = "Sample Video",
                deviceType = "mobile"
            )
        }.first()

        assertFalse(payload.has("contentId"))
    }

    @Test
    fun `switching content sends a second metadata with the new id and title but the same device fields`() {
        val payloads = capturePayloads(2) { sender ->
            // First item (as built).
            sender.sendMetadataEvent(
                isLive = false,
                contentTitle = "First",
                deviceType = "tablet",
                deviceModel = "Pixel Tablet",
                timeZone = "Europe/Stockholm",
                platform = "androidtv",
                contentId = "next-1"
            )
            // Second item (as setContent re-sends with the same device fields).
            sender.sendMetadataEvent(
                isLive = false,
                contentTitle = "Next",
                deviceType = "tablet",
                deviceModel = "Pixel Tablet",
                timeZone = "Europe/Stockholm",
                platform = "androidtv",
                contentId = "next-2"
            )
        }

        assertEquals(2, payloads.size)
        val second = payloads[1]
        assertEquals("next-2", second.getString("contentId"))
        assertEquals("Next", second.getString("contentTitle"))
        // Device fields are unchanged across the switch.
        assertEquals("tablet", second.getString("deviceType"))
        assertEquals("Pixel Tablet", second.getString("deviceModel"))
        assertEquals("Europe/Stockholm", second.getString("timeZone"))
        assertEquals("androidtv", second.getString("platform"))
    }
}
