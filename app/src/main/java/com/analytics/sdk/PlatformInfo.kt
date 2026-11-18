package com.analytics.sdk

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Resolves the `platform` value sent in the `metadata` payload: [PLATFORM_ANDROID] for a regular
 * device, [PLATFORM_ANDROID_TV] when running on a TV.
 *
 * The TV check matches the one issue #33 uses: a [UiModeManager] reporting
 * [Configuration.UI_MODE_TYPE_TELEVISION], or the `android.software.leanback`
 * ([PackageManager.FEATURE_LEANBACK]) system feature being present. The detection
 * ([isTelevision]) and the pure string mapping ([platformName]) are kept separate so the mapping
 * can be unit-tested without a real device.
 */
object PlatformInfo {
    const val PLATFORM_ANDROID: String = "Android"
    const val PLATFORM_ANDROID_TV: String = "Android TV"

    /** Pure mapping from "is this a TV?" to the EPAS `platform` value. Device-independent. */
    fun platformName(isTelevision: Boolean): String =
        if (isTelevision) PLATFORM_ANDROID_TV else PLATFORM_ANDROID

    /**
     * True when the device is a TV, using the same signals as issue #33: the system UI mode is
     * [Configuration.UI_MODE_TYPE_TELEVISION], or the leanback feature is present.
     */
    fun isTelevision(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    /** Convenience: the `platform` value for the given [context] (`Android` or `Android TV`). */
    fun platform(context: Context): String = platformName(isTelevision(context))
}
