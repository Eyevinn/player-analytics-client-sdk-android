package com.analytics.sampleplayer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.material3.MaterialTheme
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.analytics.sampleplayer.sgai.ui.SGAIVideoPlayerScreen
import com.analytics.sdk.VideoAnalyticsTracker

@UnstableApi
class SGAIPlayerActivity : ComponentActivity() {

    private lateinit var videoAnalyticsTracker: VideoAnalyticsTracker
    private lateinit var playerView: PlayerView

   // private val sgaiStreamUrl = "http://10.0.2.2:3333/test/blender.m3u8" live
    private val sgaiStreamUrl = "http://10.0.2.2:3333/x36xhzz/x36xhzz.m3u8"
    private val eventSinkUrl = "https://eyevinnlab-epasdev.eyevinn-player-analytics-eventsink.auto.prod.osaas.io"

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val player = ExoPlayer.Builder(this).build()

        // Create VideoAnalyticsTracker with SGAI ad tracking enabled
        videoAnalyticsTracker = VideoAnalyticsTracker.Builder(this, player)
            .setEventSinkUrl(eventSinkUrl)
            .setContentTitle("SGAI Stream")
            .setDeviceType("Android SGAI Player")
            .setHeartbeatInterval(30_000L)
            .enableSGAIAdTracking(true)  // Enable SGAI ad tracking
            .build()

        // Create PlayerView and set the player
        playerView = PlayerView(this).apply {
            this.player = player
        }

        // Set the player view container for ad rendering (required for SGAI)
        videoAnalyticsTracker.setPlayerViewContainer(playerView)
        videoAnalyticsTracker.setMainMediaItem(sgaiStreamUrl)

        setContent {
            MaterialTheme {
                SGAIVideoPlayerScreen(playerView)
            }
        }

        Log.i("SGAI_Analytics", "Stream URL: $sgaiStreamUrl")
        Log.i("SGAI_Analytics", "Event Sink URL: $eventSinkUrl")
    }

    @OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()

        videoAnalyticsTracker.startTracking()

        // Start playback
        playerView.player?.let { player ->
            player.playWhenReady = true
            player.play()
        }

        Log.i("SGAI_Analytics", "Started tracking and playback")
    }

    @OptIn(UnstableApi::class)
    override fun onStop() {
        super.onStop()

        playerView.player?.pause()
        videoAnalyticsTracker.stopTracking("User left the app")

        Log.i("SGAI_Analytics", "Stopped tracking and playback")
    }

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        super.onDestroy()

        // Release all resources
        videoAnalyticsTracker.release()
        playerView.player?.release()

        Log.i("SGAI_Analytics", "Released all resources")
    }
}