package com.analytics.sdk

import android.content.Context

/**
 * Resolves the `platform` value sent in the `metadata` payload: [PLATFORM_ANDROID] for a regular
 * device, [PLATFORM_ANDROID_TV] when running on a TV.
 *
 * The TV check is the shared [DeviceClass.isTelevision] (issue #33) — this object no longer carries
 * its own copy of the detection. The pure string mapping ([platformName]) is kept separate so it
 * can be unit-tested without a real device.
 */
object PlatformInfo {
    const val PLATFORM_ANDROID: String = "Android"
    const val PLATFORM_ANDROID_TV: String = "Android TV"

    /** Pure mapping from "is this a TV?" to the EPAS `platform` value. Device-independent. */
    fun platformName(isTelevision: Boolean): String =
        if (isTelevision) PLATFORM_ANDROID_TV else PLATFORM_ANDROID

    /** Convenience: the `platform` value for the given [context] (`Android` or `Android TV`). */
    fun platform(context: Context): String =
        platformName(DeviceClass.isTelevision(context))
}
