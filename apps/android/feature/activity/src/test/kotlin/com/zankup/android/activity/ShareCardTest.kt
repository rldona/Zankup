package com.zankup.android.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCardTest {
    @Test
    fun titlesWrapToTwoLinesWithAnEllipsis() {
        val measure = { s: String -> s.length * 10f }
        assertEquals(listOf("Morning run"), lines("Morning run", 200f, 2) { measure(it) })
        val long = lines("one two three four five six seven", 100f, 2) { measure(it) }
        assertEquals(2, long.size)
        assertTrue(long.last().endsWith("…"))
    }

    @Test
    fun theRouteFitsTheAreaKeepingItsShape() {
        val at = projectRoute(listOf(listOf(40.0 to -3.70, 40.01 to -3.69, 40.02 to -3.70)))
        val points = listOf(40.0 to -3.70, 40.01 to -3.69, 40.02 to -3.70).map(at)
        points.forEach { (x, y) ->
            assertTrue(x in 80f..1000f)
            assertTrue(y in 360f..920f)
        }
        // North is up: the northernmost point is the highest.
        assertTrue(points[2].second < points[0].second)
    }
}
