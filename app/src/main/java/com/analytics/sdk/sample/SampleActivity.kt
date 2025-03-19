package com.analytics.sdk.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.analytics.sdk.EyevinnVideoAnalyticsSDK

/**
 * Sample usage of the enhanced EyevinnVideoAnalyticsSDK
 */
class SampleActivity : ComponentActivity() {
    private lateinit var videoAnalyticsSDK: EyevinnVideoAnalyticsSDK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the SDK
        videoAnalyticsSDK = EyevinnVideoAnalyticsSDK.Builder(this)
            .setContentTitle("Sample Video")
            .setIsLive(false)
            .setEventSinkUrl("https://eyevinnlab-epasdev.eyevinn-player-analytics-eventsink.auto.prod.osaas.io")
            .setDeviceType("Android Sample App")
            .setHeartbeatInterval(30_000L)
            .build()

        // Set up the UI
        setContent {
            MaterialTheme {
                VideoPlayerScreen(videoAnalyticsSDK)
            }
        }

        // Load the media
        val mediaUrl =
            "https://eyevinnlab-devguide.minio-minio.auto.prod.osaas.io/devguide/VINN/52e124b8-ebe8-4dfe-9b59-8d33abb359ca/index.m3u8"
        videoAnalyticsSDK.loadMedia(mediaUrl)
    }

    override fun onStart() {
        super.onStart()
        videoAnalyticsSDK.startTracking()
    }

    override fun onStop() {
        super.onStop()
        videoAnalyticsSDK.stopTracking("User left the app")
    }

    override fun onDestroy() {
        super.onDestroy()
        videoAnalyticsSDK.release()
    }
}

@Composable
fun VideoPlayerScreen(videoAnalyticsSDK: EyevinnVideoAnalyticsSDK) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Embed the player view from the SDK
        AndroidView(
            factory = { _ -> videoAnalyticsSDK.playerView },
            modifier = Modifier.fillMaxSize()
        )
    }
}