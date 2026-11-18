package com.zankup.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeAdjustedTest {
    @Test
    fun minettisCost() {
        assertEquals(3.6, runningCost(0.0), 1e-9)
        assertTrue(runningCost(0.1) > 3.6)
        assertTrue(runningCost(-0.05) < 3.6)
        // Clamped at ±45 %.
        assertEquals(runningCost(0.45), runningCost(2.0), 1e-9)
        assertEquals(runningCost(-0.45), runningCost(-2.0), 1e-9)
    }

    private val distance = List(50) { it * 3.0 }

    @Test
    fun theFlatIsTheSame() {
        val flat = gradeAdjustedSpeeds(distance.map { 3.0 }, distance, distance.map { 600.0 })
        assertEquals(3.0, flat[25]!!, 1e-9)
    }

    @Test
    fun aClimbCountsFaster() {
        // 8 %.
        val climb = gradeAdjustedSpeeds(distance.map { 3.0 }, distance, distance.map { 600 + it * 0.08 })
        assertTrue(climb[25]!! > 3.5)
        assertEquals(3.0 * runningCost(0.08) / 3.6, climb[25]!!, 1e-9)
    }

    @Test
    fun missingSamplesStayMissing() {
        assertNull(gradeAdjustedSpeeds(listOf(3.0, null), listOf(0.0, 3.0), listOf(600.0, 600.0))[1])
        val noAltitude = gradeAdjustedSpeeds(listOf(3.0, 3.0), listOf(0.0, 3.0), listOf(null, 600.0))
        assertNull(noAltitude[0])
        // Its only neighbour is 3 m away, too close for a gradient: flat.
        assertEquals(3.0, noAltitude[1]!!, 1e-9)
    }

    @Test
    fun samplesFurtherApartThanTheWindowUseTheirNeighbours() {
        // 100 m apart, 10 m up each: 10 %.
        val sparse = gradeAdjustedSpeeds(
            listOf(3.0, 3.0, 3.0),
            listOf(0.0, 100.0, 200.0),
            listOf(0.0, 10.0, 20.0)
        )
        assertEquals(3.0 * runningCost(0.1) / 3.6, sparse[1]!!, 1e-9)
    }
}
