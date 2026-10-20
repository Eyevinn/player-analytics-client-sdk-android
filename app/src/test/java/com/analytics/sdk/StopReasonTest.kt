package com.analytics.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopReasonTest {

    @Test
    fun `values match the EPAS stopped reasons`() {
        assertEquals(listOf("ended", "aborted", "error", "unknown"), StopReason.values().map { it.value })
    }

    @Test
    fun `legacy free text maps to aborted`() {
        assertEquals(StopReason.ABORTED, StopReason.fromLegacyText("User left the app"))
        assertEquals(StopReason.ABORTED, StopReason.fromLegacyText("Stopped by user"))
        assertEquals(StopReason.ABORTED, StopReason.fromLegacyText(null))
        assertEquals(StopReason.ABORTED, StopReason.fromLegacyText(""))
    }

    @Test
    fun `legacy text that already is a spec value is kept`() {
        assertEquals(StopReason.ENDED, StopReason.fromLegacyText("ended"))
        assertEquals(StopReason.ERROR, StopReason.fromLegacyText(" Error "))
        assertEquals(StopReason.UNKNOWN, StopReason.fromLegacyText("UNKNOWN"))
    }

    @Test
    fun `only the first stopped of a session goes through`() {
        val once = StoppedOnce()
        assertFalse(once.isSent)
        assertTrue(once.tryMark())   // end of playback
        assertFalse(once.tryMark())  // leaving the app afterwards
        assertFalse(once.tryMark())  // release()
        assertTrue(once.isSent)
    }
}
