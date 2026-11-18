package com.zankup.android.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CursorTest {
    @Test
    fun theSplitUnderAPoint() {
        val distances = listOf(1000.0, 1000.0, 500.0)
        assertEquals(0, splitAt(0.0, distances))
        assertEquals(1, splitAt(0.5, distances))
        assertEquals(2, splitAt(0.9, distances))
        assertEquals(2, splitAt(1.0, distances))
        assertEquals(0, splitAt(0.5, emptyList()))
    }

    @Test
    fun splitSpeedsZeroWithoutTime() {
        val speeds = splitSpeeds(
            listOf(
                Split(1000.0, 330.0),
                Split(1000.0, 300.0),
                Split(1000.0, null),
                Split(1000.0, 0.0)
            )
        )
        assertEquals(1000.0 / 330, speeds[0], 1e-12)
        assertEquals(1000.0 / 300, speeds[1], 1e-12)
        assertEquals(0.0, speeds[2], 0.0)
        assertEquals(0.0, speeds[3], 0.0)
    }

    @Test
    fun theNearestSample() {
        val xs = List(61) { it * 30.0 }
        assertEquals(1, nearestIndex(xs, 44.0))
        assertEquals(2, nearestIndex(xs, 46.0))
        assertEquals(60, nearestIndex(xs, 99999.0))
        assertEquals(0, nearestIndex(xs, -5.0))
        // On a tie, the earlier sample.
        assertEquals(1, nearestIndex(xs, 45.0))
        assertEquals(-1, nearestIndex(emptyList(), 3.0))
    }
}
