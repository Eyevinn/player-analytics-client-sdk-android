package eyevinn.com.client.sdk.android

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView
import eyevinn.com.client.sdk.android.analytics.AnalyticsEventSender
import eyevinn.com.client.sdk.android.ui.theme.MyApplicationTheme

private const val MEDIA_URL =
    "https://eyevinnlab-devguide.minio-minio.auto.prod.osaas.io/devguide/VINN/52e124b8-ebe8-4dfe-9b59-8d33abb359ca/index.m3u8"

class MainActivity : ComponentActivity() {

    private val analyticsSender = AnalyticsEventSender()
    private lateinit var player: ExoPlayer
    private var loadedEventSent = false
    private var bufferingEventOngoing = false
    private var seekedEventOngoing = false
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

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        analyticsSender.sendInitEvent(0L)
        analyticsSender.sendMetadataEvent(
            isLive = false,
            contentTitle = "Eyevinn Event-sink Android App",
            deviceType = "Android player"
        )

        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            analyticsSender.sendBufferingEvent(currentPosition, duration)
                            bufferingEventOngoing = true
                        }
                        Player.STATE_READY -> {
                            if (bufferingEventOngoing) {
                                analyticsSender.sendBufferedEvent(currentPosition, duration)
                                bufferingEventOngoing = false
                            }
                            // Send "loaded" event once, when first ready
                            if (!loadedEventSent) {
                                analyticsSender.sendLoadedEvent()
                                loadedEventSent = true
                            }
                            if (playWhenReady) {
                                analyticsSender.sendPlayingEvent(currentPosition, duration)
                            }
                            if (!seekedEventOngoing) {
                                analyticsSender.sendLoadedEvent()
                                loadedEventSent = true
                            }
                        }
                        Player.STATE_ENDED -> {
                            analyticsSender.sendStoppedEvent(
                                currentPosition,
                                duration,
                                "Playback ended"
                            )
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        analyticsSender.sendPlayingEvent(currentPosition, duration)
                    } else {
                        analyticsSender.sendPausedEvent(currentPosition, duration)
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        analyticsSender.sendSeekingEvent(currentPosition, duration)
                    }
                }

                 fun onSeekProcessed() {
                    analyticsSender.sendSeekedEvent(currentPosition, duration)
                }
            })

            addAnalyticsListener(object : AnalyticsListener {
                override fun onVideoInputFormatChanged(
                    eventTime: AnalyticsListener.EventTime,
                    format: Format,
                    decoderReuseEvaluation: DecoderReuseEvaluation?
                ) {
                    val bitrateKbps = format.bitrate / 1000
                    val width = format.width
                    val height = format.height

                    analyticsSender.sendBitrateChangedEvent(
                        currentPosition,
                        duration,
                        bitrateKbps,
                        width,
                        height
                    )
                }
            })
        }

        analyticsSender.sendLoadingEvent()
        player.setMediaItem(MediaItem.fromUri(MEDIA_URL))
        player.prepare()

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
