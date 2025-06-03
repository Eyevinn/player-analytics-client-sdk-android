package com.analytics.sampleplayer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.media3.ui.PlayerView
import com.analytics.sampleplayer.sgai.AdManager
import com.analytics.sampleplayer.sgai.AnalyticsManager
import com.analytics.sampleplayer.sgai.PlayerManager
import com.analytics.sampleplayer.sgai.ui.SGAIVideoPlayerScreen

class SGAIPlayerActivity : ComponentActivity() {

    private lateinit var playerManager: PlayerManager
    private lateinit var analyticsManager: AnalyticsManager
    private lateinit var adManager: AdManager

    private val sgaiStreamUrl = "http://10.0.2.2:3333/loop/master.m3u8"
    private val eventSinkUrl = "https://eyevinnlab-epasdev.eyevinn-player-analytics-eventsink.auto.prod.osaas.io"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playerManager = PlayerManager(this)
        analyticsManager = AnalyticsManager(playerManager.player, eventSinkUrl)
        adManager = AdManager(playerManager, sgaiStreamUrl)

        playerManager.setMainMediaItem(sgaiStreamUrl)

        val playerView = PlayerView(this).apply {
            player = playerManager.player
        }

        setContent {
            MaterialTheme {
                SGAIVideoPlayerScreen(playerView)
            }
        }

        Log.i("SGAI_AdTracking", "Starting SGAI Player with session ID: ${adManager.sessionId}")
        Log.i("SGAI_AdTracking", "Stream URL: $sgaiStreamUrl")
    }

    override fun onStart() {
        super.onStart()
        analyticsManager.startTracking()
        playerManager.play()
        adManager.startMonitoring()
    }

    override fun onStop() {
        super.onStop()
        analyticsManager.stopTracking("User left the app")
        playerManager.pause()
        adManager.stopMonitoring()
    }

    override fun onDestroy() {
        super.onDestroy()
        analyticsManager.release()
        playerManager.release()
    }
}

