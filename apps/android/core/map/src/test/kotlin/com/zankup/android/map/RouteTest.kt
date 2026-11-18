package com.zankup.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {
    private val nan = Double.NaN
    private val route =
        Route(
            doubleArrayOf(40.0, 40.001, nan, nan, 40.004, 40.005),
            doubleArrayOf(-3.7, -3.7, nan, nan, -3.7, -3.7),
            floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f)
        )

    @Test
    fun theDotNeverFallsInAGap() {
        assertEquals(1, route.positionAt(2))
        assertEquals(4, route.positionAt(3))
        assertEquals(0, route.positionAt(0))
        assertNull(route.positionAt(-1))
    }

    @Test
    fun findsTheSampleAtATime() {
        assertEquals(3, route.indexAtTime(3.5f))
        assertEquals(5, route.indexAtTime(99f))
    }

    @Test
    fun progressRunsFromZeroToOne() {
        val progress = route.progress()
        assertEquals(0.0, progress.first(), 0.0)
        assertEquals(1.0, progress.last(), 1e-9)
        assertTrue(progress.toList().zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun coloursTheRouteOverTheRamp() {
        val stops = colourStops(route, floatArrayOf(1f, 2f, 0f, 0f, 3f, 4f), inverted = false)!!
        assertEquals(RAMP.first(), stops.first().second)
        assertEquals(RAMP.last(), stops.last().second)
        assertTrue(stops.zipWithNext().all { (a, b) -> b.first > a.first })
        assertNull(colourStops(route, FloatArray(6) { Float.NaN }, inverted = false))
    }

    @Test
    fun rampEnds() {
        assertEquals(RAMP.first(), rampColour(-1f))
        assertEquals(RAMP.last(), rampColour(2f))
    }
}
