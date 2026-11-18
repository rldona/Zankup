package com.zankup.android.routes

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EffortsTest {
    @Test
    fun theFastestIsTheLowestTime() {
        assertEquals(1500.0, fastest(listOf(1620.0, 1500.0, 1710.0))!!, 0.0)
        assertNull(fastest(emptyList()))
    }

    @Test
    fun theGapNeverGoesBelowZero() {
        assertEquals(30.0, behind(1530.0, 1500.0), 0.0)
        assertEquals(0.0, behind(1500.0, 1500.0), 0.0)
    }

    @Test
    fun theNewestComesFirstAndTheMostDoneRouteLeads() {
        assertEquals(listOf(3, 2, 1), newestFirst(listOf(1, 2, 3)))
        assertEquals(
            listOf("b", "c", "a"),
            mostDone(listOf("a" to 2, "b" to 9, "c" to 5)) {
                it.second
            }.map { it.first }
        )
    }

    @Test
    fun aSegmentTrackIsReadAtPrecisionSix() {
        // Two points of the Google sample, encoded at precision 5, are 10x the precision-6 reading.
        val five = trackOf("_p~iF~ps|U_ulLnnqC", precision = 5)!!
        val six = trackOf("_p~iF~ps|U_ulLnnqC", precision = 6)!!
        assertEquals(38.5, five.lat[0], 1e-6)
        assertEquals(3.85, six.lat[0], 1e-6)
        assertNull(trackOf(null))
        assertNotNull(shortDay("2026-11-17", Locale.UK))
        assertEquals("nope", shortDay("nope", Locale.UK))
    }
}
