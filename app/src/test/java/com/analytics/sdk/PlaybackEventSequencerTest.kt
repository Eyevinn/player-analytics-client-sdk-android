package com.analytics.sdk

import com.analytics.sdk.PlaybackEventSequencer.Event.BUFFERED
import com.analytics.sdk.PlaybackEventSequencer.Event.BUFFERING
import com.analytics.sdk.PlaybackEventSequencer.Event.LOADED
import com.analytics.sdk.PlaybackEventSequencer.Event.PAUSED
import com.analytics.sdk.PlaybackEventSequencer.Event.PLAYING
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackEventSequencerTest {

    @Test
    fun `start-up sequence when READY arrives before isPlaying`() {
        val s = PlaybackEventSequencer()
        val events = s.onBuffering() + s.onReady() + s.onIsPlayingChanged(true, false, false)
        assertEquals(listOf(BUFFERING, BUFFERED, LOADED, PLAYING), events)
    }

    @Test
    fun `start-up sequence when isPlaying arrives before READY (the 2026-10-12 race)`() {
        val s = PlaybackEventSequencer()
        val events = s.onBuffering() + s.onIsPlayingChanged(true, false, false) + s.onReady()
        assertEquals(listOf(BUFFERING, BUFFERED, LOADED, PLAYING), events)
    }

    @Test
    fun `playing is sent once per transition into playback`() {
        val s = PlaybackEventSequencer()
        s.onReady()
        assertEquals(listOf(PLAYING), s.onIsPlayingChanged(true, false, false))
        assertEquals(emptyList<Any>(), s.onIsPlayingChanged(true, false, false))
    }

    @Test
    fun `pause and resume`() {
        val s = PlaybackEventSequencer()
        s.onReady(); s.onIsPlayingChanged(true, false, false)
        assertEquals(listOf(PAUSED), s.onIsPlayingChanged(false, false, false))
        assertEquals(listOf(PLAYING), s.onIsPlayingChanged(true, false, false))
    }

    @Test
    fun `rebuffer gives buffering, buffered, playing and no paused or second loaded`() {
        val s = PlaybackEventSequencer()
        s.onReady(); s.onIsPlayingChanged(true, false, false)
        val events = s.onIsPlayingChanged(false, true, false) + s.onBuffering() + s.onReady() +
            s.onIsPlayingChanged(true, false, false)
        assertEquals(listOf(BUFFERING, BUFFERED, PLAYING), events)
    }

    @Test
    fun `no paused when the item ends`() {
        val s = PlaybackEventSequencer()
        s.onReady(); s.onIsPlayingChanged(true, false, false)
        assertEquals(emptyList<Any>(), s.onIsPlayingChanged(false, false, true))
    }

    @Test
    fun `loaded is sent once`() {
        val s = PlaybackEventSequencer()
        assertEquals(listOf(LOADED), s.onReady())
        assertEquals(emptyList<Any>(), s.onReady())
    }
}
