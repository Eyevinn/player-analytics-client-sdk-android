package com.analytics.sampleplayer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.material3.MaterialTheme
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.analytics.sampleplayer.sgai.AnalyticsManager
import com.analytics.sdk.SGAIAdTracker
import com.analytics.sampleplayer.sgai.ui.SGAIVideoPlayerScreen

@UnstableApi
class SGAIPlayerActivity : ComponentActivity() {

    private lateinit var adTracker: SGAIAdTracker
    private lateinit var analyticsManager: AnalyticsManager

    private val sgaiStreamUrl = "http://10.0.2.2:3333/x36xhzz/x36xhzz.m3u8"
    private val eventSinkUrl =
        "https://eyevinnlab-epasdev.eyevinn-player-analytics-eventsink.auto.prod.osaas.io"

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adTracker = SGAIAdTracker(this)
        analyticsManager = AnalyticsManager(adTracker.player, eventSinkUrl)

        adTracker.setMainMediaItem(sgaiStreamUrl)
        val playerView = PlayerView(this).apply {
            player = adTracker.player
        }
        setContent {
            MaterialTheme {
                SGAIVideoPlayerScreen(playerView)
            }
        }

        Log.i("SGAI_AdTracking", "Stream URL: $sgaiStreamUrl")
    }

    @OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        analyticsManager.startTracking()
        adTracker.play()

    }

    @OptIn(UnstableApi::class)
    override fun onStop() {
        super.onStop()
        analyticsManager.stopTracking("User left the app")
        adTracker.pause()

    }
    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        super.onDestroy()
        analyticsManager.release()
        adTracker.release()
    }
}

