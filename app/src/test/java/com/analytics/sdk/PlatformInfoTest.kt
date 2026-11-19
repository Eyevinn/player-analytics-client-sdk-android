package com.analytics.sdk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PlatformInfoTest {

    @Test
    fun `television maps to Android TV`() {
        assertEquals("Android TV", PlatformInfo.platformName(true))
    }

    @Test
    fun `non-television maps to Android`() {
        assertEquals("Android", PlatformInfo.platformName(false))
    }

    @Test
    fun `platform constants match the EPAS values`() {
        assertEquals("Android", PlatformInfo.PLATFORM_ANDROID)
        assertEquals("Android TV", PlatformInfo.PLATFORM_ANDROID_TV)
    }

    @Test
    fun `metadata payload carries the platform value`() {
        val done = CountDownLatch(1)
        var payload: JSONObject? = null
        val sender = AnalyticsEventSender("https://sink.test/") { _, body ->
            payload = JSONObject(body).getJSONObject("payload")
            done.countDown()
        }

        sender.sendMetadataEvent(
            isLive = false,
            contentTitle = "Big Buck Bunny",
            deviceType = "Android player",
            platform = "Android TV"
        )

        assertEquals(true, done.await(5, TimeUnit.SECONDS))
        assertEquals("Android TV", payload?.getString("platform"))
        // Additive: the existing fields are unchanged.
        assertEquals(false, payload?.getBoolean("live"))
        assertEquals("Big Buck Bunny", payload?.getString("contentTitle"))
        assertEquals("Android player", payload?.getString("deviceType"))
    }

    @Test
    fun `metadata platform defaults to Android`() {
        val done = CountDownLatch(1)
        var payload: JSONObject? = null
        val sender = AnalyticsEventSender("https://sink.test/") { _, body ->
            payload = JSONObject(body).getJSONObject("payload")
            done.countDown()
        }

        sender.sendMetadataEvent(
            isLive = true,
            contentTitle = null,
            deviceType = "Android player"
        )

        assertEquals(true, done.await(5, TimeUnit.SECONDS))
        assertEquals("Android", payload?.getString("platform"))
    }
}
