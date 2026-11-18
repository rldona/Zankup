package com.zankup.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffortTest {
    @Test
    fun rangeIsTwoThirdsToFourThirdsOfTheThreeWeeksBefore() {
        // Strava's 171-341.
        assertEquals(EffortRange(171, 341), weeklyRange(listOf(500.0, 250.0, 250.0, 268.0)))
    }

    @Test
    fun rangeMakesDoWithFewerWeeks() {
        assertEquals(EffortRange(200, 400), weeklyRange(listOf(300.0)))
        assertNull(weeklyRange(emptyList()))
        assertNull(weeklyRange(listOf(0.0, 0.0, 0.0)))
    }

    @Test
    fun statusAgainstTheRange() {
        val range = EffortRange(171, 341)
        assertEquals(EffortStatus.IN, effortStatus(243.0, range))
        assertEquals(EffortStatus.BELOW, effortStatus(22.0, range))
        assertEquals(EffortStatus.ABOVE, effortStatus(400.0, range))
        assertEquals(EffortStatus.FAR_ABOVE, effortStatus(927.0, range))
        assertEquals(EffortStatus.IN, effortStatus(171.0, range))
        assertEquals(EffortStatus.IN, effortStatus(341.0, range))
    }

    @Test
    fun inRangeWithNothingToCompareWith() {
        assertEquals(EffortStatus.IN, effortStatus(100.0, null))
    }

    @Test
    fun cumulativeUpToToday() {
        assertEquals(
            listOf(60.0, 140.0, 140.0, 200.0, 243.0, 243.0),
            cumulative(listOf(60.0, 80.0, 0.0, 60.0, 43.0, 0.0, 0.0), upTo = 5)
        )
        assertEquals(listOf(1.0, 3.0, 6.0), cumulative(listOf(1.0, 2.0, 3.0)))
        assertEquals(emptyList<Double>(), cumulative(emptyList()))
    }

    @Test
    fun bandsByHowHard() {
        assertEquals(EffortBand.LIGHT, effortBand(14.0))
        assertEquals(EffortBand.MODERATE, effortBand(96.0))
        assertEquals(EffortBand.HARD, effortBand(182.0))
        assertEquals(EffortBand.MODERATE, effortBand(50.0))
        assertEquals(EffortBand.HARD, effortBand(150.0))
    }
}
