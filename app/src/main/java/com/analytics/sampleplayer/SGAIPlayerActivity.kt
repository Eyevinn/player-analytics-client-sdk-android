package com.analytics.sampleplayer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.analytics.sampleplayer.sgai.AnalyticsManager
import com.analytics.sampleplayer.sgai.PlayerManager
import com.analytics.sampleplayer.sgai.ui.SGAIVideoPlayerScreen
import com.analytics.sdk.AdTrackingSDK
import com.analytics.sdk.PlaybackState
import com.analytics.sdk.PlayerCallback

class SGAIPlayerActivity : ComponentActivity() {

    private lateinit var playerManager: PlayerManager
    private lateinit var analyticsManager: AnalyticsManager
    private lateinit var adTrackingSDK: AdTrackingSDK

    private val sgaiStreamUrl = "http://10.0.2.2:3333/loop/master.m3u8"
    private val eventSinkUrl =
        "https://eyevinnlab-epasdev.eyevinn-player-analytics-eventsink.auto.prod.osaas.io"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playerManager = PlayerManager(this)
        analyticsManager = AnalyticsManager(playerManager.player, eventSinkUrl)
        adTrackingSDK = initSGAIAdTracker()

        playerManager.setMainMediaItem(sgaiStreamUrl)
        val playerView = PlayerView(this).apply {
            player = playerManager.player
        }
        setContent {
            MaterialTheme {
                SGAIVideoPlayerScreen(playerView)
            }
        }

        Log.i("SGAI_AdTracking", "Stream URL: $sgaiStreamUrl")
    }

    private fun initSGAIAdTracker(): AdTrackingSDK {
        return AdTrackingSDK(this, sgaiStreamUrl, object : PlayerCallback {
            override fun playAd(adUrl: String, duration: Long) {
                playerManager.setAdMediaItem(adUrl)
                playerManager.play()
            }

            override fun resumeMainContent() {
                playerManager.setMainMediaItem(sgaiStreamUrl)
                playerManager.play()
            }

            override fun getCurrentPosition(): Long {
                return playerManager.player.currentPosition
            }

            override fun getPlaybackState(): Int {
                return when (playerManager.player.playbackState) {
                    Player.STATE_IDLE -> PlaybackState.STATE_IDLE
                    Player.STATE_BUFFERING -> PlaybackState.STATE_BUFFERING
                    Player.STATE_READY -> PlaybackState.STATE_READY
                    Player.STATE_ENDED -> PlaybackState.STATE_ENDED
                    else -> PlaybackState.STATE_IDLE
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        analyticsManager.startTracking()
        playerManager.play()
        adTrackingSDK.startMonitoring()

    }

    override fun onStop() {
        super.onStop()
        analyticsManager.stopTracking("User left the app")
        playerManager.pause()
        adTrackingSDK.stopMonitoring()

    }

    override fun onDestroy() {
        super.onDestroy()
        analyticsManager.release()
        playerManager.release()
        adTrackingSDK.release()
    }
}

