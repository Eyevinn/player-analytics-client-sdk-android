package eyevinn.com.client.sdk.android

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import eyevinn.com.client.sdk.android.analytics.AnalyticsEventSender
import eyevinn.com.client.sdk.android.ui.theme.MyApplicationTheme

private const val MEDIA_URL =
    "https://eyevinnlab-devguide.minio-minio.auto.prod.osaas.io/devguide/VINN/52e124b8-ebe8-4dfe-9b59-8d33abb359ca/index.m3u8"

class MainActivity : ComponentActivity() {

    private val analyticsSender = AnalyticsEventSender()
    private lateinit var player: ExoPlayer

    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatIntervalMs = 30_000L
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (::player.isInitialized) {
                analyticsSender.sendHeartbeatEvent(player.currentPosition, player.duration)
            }
            heartbeatHandler.postDelayed(this, heartbeatIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        analyticsSender.sendInitEvent(0L)
        analyticsSender.sendMetadataEvent(false, "Eyevinn Event-sink Android App")

        player = ExoPlayer.Builder(this).build().apply {
            analyticsSender.sendLoadingEvent()
            setMediaItem(MediaItem.fromUri(MEDIA_URL))
            prepare()
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    analyticsSender.sendPlayingEvent(player.currentPosition, player.duration)
                } else {
                    analyticsSender.sendPausedEvent(player.currentPosition, player.duration)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        analyticsSender.sendBufferingEvent(player.currentPosition, player.duration)
                    }
                    Player.STATE_READY -> {
                        if (player.playWhenReady) {
                            analyticsSender.sendPlayingEvent(player.currentPosition, player.duration)
                        }
                    }
                    Player.STATE_ENDED -> {
                        analyticsSender.sendStoppedEvent(
                            player.currentPosition,
                            player.duration,
                            "Playback ended"
                        )
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    analyticsSender.sendSeekingEvent(player.currentPosition, player.duration)
                }
            }
        })

        setContent {
            MyApplicationTheme {
                VideoPlayer(player)
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        heartbeatHandler.post(heartbeatRunnable)
        player.playWhenReady = true
    }

    @OptIn(UnstableApi::class)
    override fun onStop() {
        super.onStop()
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        player.playWhenReady = false
        analyticsSender.sendStoppedEvent(
            player.currentPosition,
            player.duration,
            "Stopped by user"
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}

@Composable
fun VideoPlayer(player: ExoPlayer) {
    AndroidView(factory = { context ->
        PlayerView(context).apply {
            this.player = player
        }
    })
}
