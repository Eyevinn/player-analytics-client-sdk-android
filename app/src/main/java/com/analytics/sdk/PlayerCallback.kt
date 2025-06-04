package com.analytics.sdk

interface PlayerCallback {
    fun playAd(adUrl: String, duration: Long)
    fun resumeMainContent()
    fun getCurrentPosition(): Long
    fun getPlaybackState(): Int
}