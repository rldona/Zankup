package com.zankup.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerLevelsTest {
    @Test
    fun twelveSpokesSprintToClimb() {
        assertEquals(listOf(15, 30, 60, 120, 180, 300, 600, 900, 1200, 1800, 2700, 3600), SKILL_DURATIONS)
    }

    @Test
    fun theScaleAtTheAnchorsAndBetween() {
        assertEquals(4.5..11.5, scaleAt(60))
        assertEquals(1.8..6.4, scaleAt(3600))
        val tenMinutes = scaleAt(600)
        assertTrue(tenMinutes.start > 1.8)
        assertTrue(tenMinutes.start < 2.5)
        assertTrue(tenMinutes.endInclusive < 7.6)
        // Past the anchors it stays at the nearest one.
        assertEquals(1.8..6.4, scaleAt(7200))
        assertEquals(8.0..24.0, scaleAt(1))
    }

    @Test
    fun nineLevelsByWattsPerKilo() {
        assertEquals(0.0, levelAt(0.0, 60, 70.0), 0.0)
        assertEquals(0.0, levelAt(300.0, 60, 0.0), 0.0)
        assertEquals(1.0, levelAt(4.5 * 70, 60, 70.0), 1e-9)
        assertEquals(9.0, levelAt(11.5 * 70, 60, 70.0), 1e-9)
        assertEquals(9.0, levelAt(2000.0, 60, 70.0), 0.0)
        assertEquals(3.0, skillLevel(listOf(2.0, 3.0, 4.0)), 0.0)
        assertEquals(0.0, skillLevel(emptyList()), 0.0)
    }

    @Test
    fun wattsForALevelAreTheInverse() {
        for (duration in listOf(15, 300, 3600)) {
            for (level in listOf(1.0, 4.5, 9.0)) {
                assertEquals(level, levelAt(levelWatts(level, duration, 75.0), duration, 75.0), 1e-6)
            }
        }
        // Under level 1 asks for level 1.
        assertEquals(levelWatts(1.0, 60, 70.0), levelWatts(0.2, 60, 70.0), 0.0)
        assertEquals(4.5 * 70, levelWatts(1.0, 60, 70.0), 1e-9)
    }
}
