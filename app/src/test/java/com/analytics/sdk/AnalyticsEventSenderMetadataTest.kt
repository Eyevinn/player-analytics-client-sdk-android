package com.analytics.sdk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AnalyticsEventSenderMetadataTest {

    private fun captureMetadataPayload(send: (AnalyticsEventSender) -> Unit): JSONObject {
        val done = CountDownLatch(1)
        val bodies = mutableListOf<String>()
        val sender = AnalyticsEventSender("https://sink.test/") { _, body ->
            synchronized(bodies) { bodies += body }
            done.countDown()
        }
        send(sender)
        assertEquals(true, done.await(5, TimeUnit.SECONDS))
        return JSONObject(synchronized(bodies) { bodies.first() }).getJSONObject("payload")
    }

    @Test
    fun `metadata carries the device time zone by default`() {
        val payload = captureMetadataPayload { sender ->
            sender.sendMetadataEvent(
                isLive = false,
                contentTitle = "Big Buck Bunny",
                deviceType = "Android player"
            )
        }

        assertEquals(true, payload.has("timeZone"))
        assertEquals(TimeZone.getDefault().id, payload.getString("timeZone"))
    }

    @Test
    fun `metadata sends an overridden time zone unchanged`() {
        val payload = captureMetadataPayload { sender ->
            sender.sendMetadataEvent(
                isLive = false,
                contentTitle = "Big Buck Bunny",
                deviceType = "Android player",
                timeZone = "America/New_York"
            )
        }

        assertEquals("America/New_York", payload.getString("timeZone"))
    }
}
