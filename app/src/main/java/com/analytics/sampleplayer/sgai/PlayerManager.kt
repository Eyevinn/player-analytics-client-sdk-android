package com.analytics.sampleplayer.sgai

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.util.EventLogger

class PlayerManager(private val context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(createMediaSourceFactory())
        .build().apply {
            playWhenReady = false
            addAnalyticsListener(EventLogger())
        }

    @OptIn(UnstableApi::class)
    private fun createMediaSourceFactory(): DefaultMediaSourceFactory {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
        return DefaultMediaSourceFactory(dataSourceFactory)
    }

    fun setMainMediaItem(streamUrl: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
    }

    fun setAdMediaItem(adUrl: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(adUrl))
            .setMimeType(MimeTypes.VIDEO_MP4)
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
    }

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun release() {
        player.release()
    }
}