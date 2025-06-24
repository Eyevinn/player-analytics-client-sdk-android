package com.analytics.sampleplayer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.analytics.sdk.VideoAnalyticsTracker

class SimpleSeparatedPlayerActivity : ComponentActivity() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var videoAnalyticsTracker: VideoAnalyticsTracker
    private lateinit var playerView: PlayerView

    // Customize these as needed
    private val assetUrl = "https://eyevinnlab-devguide.minio-minio.auto.prod.osaas.io/devguide/VINN/52e124b8-ebe8-4dfe-9b59-8d33abb359ca/index.m3u8"
    private val eventSinkUrl = "https://eyevinnlab-epasdev.eyevinn-player-analytics-eventsink.auto.prod.osaas.io"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create ExoPlayer instance
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            playWhenReady = false
        }

        // Create VideoAnalyticsTracker with SGAI tracking disabled (default)
        videoAnalyticsTracker = VideoAnalyticsTracker.Builder(this, exoPlayer)
            .setEventSinkUrl(eventSinkUrl)
            .setContentTitle("Sample Video")
            .setDeviceType("Android Sample App")
            .setHeartbeatInterval(30_000L)
            .build()

        // Set the main media item (simple video, no ads)
        videoAnalyticsTracker.setMainMediaItem(assetUrl)

        // Create PlayerView
        playerView = PlayerView(this).apply {
            player = exoPlayer
        }

        setContent {
            MaterialTheme {
                VideoPlayerScreen(playerView)
            }
        }

        Log.i("SimplePlayer", "Video URL: $assetUrl")
        Log.i("SimplePlayer", "Event Sink URL: $eventSinkUrl")
    }

    override fun onStart() {
        super.onStart()

        // Start video analytics tracking
        videoAnalyticsTracker.startTracking()

        // Start playback
        exoPlayer.play()

        Log.i("SimplePlayer", "Started video analytics tracking and playback")
    }

    override fun onStop() {
        super.onStop()

        // Pause playback
        exoPlayer.pause()

        // Stop tracking with reason
        videoAnalyticsTracker.stopTracking("User left the app")

        Log.i("SimplePlayer", "Stopped tracking and playback")
    }

    override fun onDestroy() {
        super.onDestroy()

        // Release all resources
        videoAnalyticsTracker.release()
        exoPlayer.release()

        Log.i("SimplePlayer", "Released all resources")
    }

    /**
     * Optional: Send custom analytics event for simple video playback
     */
    private fun sendCustomVideoEvent(eventType: String) {
        try {
            videoAnalyticsTracker.sendCustomEvent(eventType)
            Log.d("SimplePlayer", "Sent custom video event: $eventType")
        } catch (e: Exception) {
            Log.e("SimplePlayer", "Failed to send custom event: $eventType", e)
        }
    }

    /**
     * Optional: Manual event sending for debugging
     */
    private fun debugVideoAnalytics() {
        val position = exoPlayer.currentPosition
        val duration = exoPlayer.duration
        Log.d("SimplePlayer", "Current playback position: ${position}ms, duration: ${duration}ms")

        // Example of sending a custom event with current playback state
        sendCustomVideoEvent("DEBUG_CHECKPOINT")
    }
}

@Composable
fun VideoPlayerScreen(playerView: PlayerView) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )
    }
}