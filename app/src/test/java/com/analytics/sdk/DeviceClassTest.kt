package com.analytics.sdk

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceClassTest {

    @Test
    fun `a television is reported as tv`() {
        // A TV at a tablet-sized (or larger) width is still tv, not tablet.
        assertEquals(DeviceClass.TV, DeviceClass.classify(isTelevision = true, smallestScreenWidthDp = 720))
        assertEquals(DeviceClass.TV, DeviceClass.classify(isTelevision = true, smallestScreenWidthDp = 320))
    }

    @Test
    fun `a wide non-tv device is reported as tablet`() {
        assertEquals(DeviceClass.TABLET, DeviceClass.classify(isTelevision = false, smallestScreenWidthDp = 600))
        assertEquals(DeviceClass.TABLET, DeviceClass.classify(isTelevision = false, smallestScreenWidthDp = 800))
    }

    @Test
    fun `a narrow non-tv device is reported as mobile`() {
        assertEquals(DeviceClass.MOBILE, DeviceClass.classify(isTelevision = false, smallestScreenWidthDp = 599))
        assertEquals(DeviceClass.MOBILE, DeviceClass.classify(isTelevision = false, smallestScreenWidthDp = 320))
        assertEquals(DeviceClass.MOBILE, DeviceClass.classify(isTelevision = false, smallestScreenWidthDp = 0))
    }

    @Test
    fun `600dp is the tablet threshold`() {
        assertEquals(600, DeviceClass.TABLET_MIN_SMALLEST_WIDTH_DP)
        assertEquals(
            DeviceClass.MOBILE,
            DeviceClass.classify(isTelevision = false, smallestScreenWidthDp = DeviceClass.TABLET_MIN_SMALLEST_WIDTH_DP - 1)
        )
        assertEquals(
            DeviceClass.TABLET,
            DeviceClass.classify(isTelevision = false, smallestScreenWidthDp = DeviceClass.TABLET_MIN_SMALLEST_WIDTH_DP)
        )
    }

    @Test
    fun `wire values match the cross-platform device classes`() {
        assertEquals("mobile", DeviceClass.MOBILE.value)
        assertEquals("tablet", DeviceClass.TABLET.value)
        assertEquals("tv", DeviceClass.TV.value)
    }
}
