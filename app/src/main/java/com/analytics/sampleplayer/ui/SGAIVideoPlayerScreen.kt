package com.analytics.sampleplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView

@Composable
fun SGAIVideoPlayerScreen(playerView: PlayerView) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )
    }
}