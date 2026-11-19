package com.analytics.sdk

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * The device class sent as `metadata.payload.deviceType`.
 *
 * The web and iOS/Swift SDKs report a class (`mobile`, `tablet`, `tv`, `desktop`) rather than a
 * free-text label, so the dashboard's Device type breakdown can group devices by class across
 * platforms. Android has no `desktop`, so it reports one of [MOBILE], [TABLET] or [TV].
 *
 * The detection is split into a pure [classify] (unit-testable without a device) and the thin
 * Android lookups in [detect]/[isTelevision] that feed it. [isTelevision] is kept separate so the
 * TV check can be reused on its own (see issue #35).
 */
enum class DeviceClass(val value: String) {
    /** A handset / phone (the default when it is neither a TV nor a tablet). */
    MOBILE("mobile"),

    /** A tablet: smallest screen width at least [TABLET_MIN_SMALLEST_WIDTH_DP] dp. */
    TABLET("tablet"),

    /** An Android TV / leanback device. */
    TV("tv");

    companion object {
        /**
         * Smallest-screen-width threshold (in dp) at or above which a non-TV device is treated as a
         * [TABLET]. 600dp is the conventional Android breakpoint between handset and tablet layouts.
         */
        const val TABLET_MIN_SMALLEST_WIDTH_DP = 600

        /**
         * Pure mapping from the two device signals to a [DeviceClass]. TV wins over tablet so a
         * large-screen TV is never misreported as a tablet.
         *
         * @param isTelevision whether the device is a TV / leanback device (see [isTelevision]).
         * @param smallestScreenWidthDp `Configuration.smallestScreenWidthDp` for the device.
         */
        fun classify(isTelevision: Boolean, smallestScreenWidthDp: Int): DeviceClass = when {
            isTelevision -> TV
            smallestScreenWidthDp >= TABLET_MIN_SMALLEST_WIDTH_DP -> TABLET
            else -> MOBILE
        }

        /**
         * Whether [context] runs on a TV: `UiModeManager` reports `UI_MODE_TYPE_TELEVISION`, or the
         * `android.software.leanback` feature is present. Reusable on its own (issue #35).
         */
        fun isTelevision(context: Context): Boolean {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            val tvUiMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            val leanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            return tvUiMode || leanback
        }

        /** Detect the [DeviceClass] for [context] by reading the TV signal and the screen width. */
        fun detect(context: Context): DeviceClass =
            classify(isTelevision(context), context.resources.configuration.smallestScreenWidthDp)
    }
}
