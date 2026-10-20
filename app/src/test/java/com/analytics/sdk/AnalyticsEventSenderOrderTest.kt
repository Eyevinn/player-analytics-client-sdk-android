package com.analytics.sdk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AnalyticsEventSenderOrderTest {

    @Test
    fun `events reach the sink in the order they were sent even when one request is slow`() {
        val received = Collections.synchronizedList(mutableListOf<String>())
        val done = CountDownLatch(4)
        val sender = AnalyticsEventSender("https://sink.test/") { _, body ->
            val event = JSONObject(body).getString("event")
            if (event == "init") Thread.sleep(300) // a slow init must still arrive first
            received += event
            done.countDown()
        }

        sender.sendInitEvent(0L)
        sender.sendLoadingEvent()
        sender.sendLoadedEvent(0L, 63_040L)
        sender.sendPlayingEvent(0L, 63_040L)

        assertEquals(true, done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("init", "loading", "loaded", "playing"), received)
    }
}
