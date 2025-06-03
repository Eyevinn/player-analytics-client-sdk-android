package com.analytics.sampleplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.analytics.sdk.VideoAnalyticsTracker

/**
 * A simple Activity that:
 * 1) Creates its own ExoPlayer
 * 2) Uses VideoAnalyticsTracker to send analytics events
 * 3) Embeds the PlayerView in a Jetpack Compose layout
 */
class SimpleSeparatedPlayerActivity : ComponentActivity() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var analyticsTracker: VideoAnalyticsTracker
    private lateinit var playerView: PlayerView

    // Customize these as needed
    private val assetUrl = "https://eyevinnlab-devguide.minio-minio.auto.prod.osaas.io/devguide/VINN/52e124b8-ebe8-4dfe-9b59-8d33abb359ca/index.m3u8"
    private val eventSinkUrl = "https://eyevinnlab-epasdev.eyevinn-player-analytics-eventsink.auto.prod.osaas.io"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exoPlayer = ExoPlayer.Builder(this).build().apply {
            playWhenReady = false
        }

        analyticsTracker = VideoAnalyticsTracker.Builder(exoPlayer)
            .setEventSinkUrl(eventSinkUrl)
            .setContentTitle("Sample Video")
            .setIsLive(false)
            .setDeviceType("Android Sample App")
            .setHeartbeatInterval(30_000L)
            .build()

        exoPlayer.setMediaItem(MediaItem.fromUri(assetUrl))

        exoPlayer.prepare()

        playerView = PlayerView(this).apply {
            player = exoPlayer
        }

        setContent {
            MaterialTheme {
                VideoPlayerScreen(playerView)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Start heartbeat tracking
        analyticsTracker.startTracking()
        exoPlayer.play()
    }

    override fun onStop() {
        super.onStop()
        analyticsTracker.stopTracking("User left the app")
        exoPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        analyticsTracker.release()
        exoPlayer.release()
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
