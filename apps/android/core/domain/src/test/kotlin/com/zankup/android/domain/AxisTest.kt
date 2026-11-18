package com.zankup.android.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AxisTest {
    @Test
    fun aRoundStepInFourIntervals() {
        assertEquals(Axis(0.0, 100.0, listOf(0.0, 25.0, 50.0, 75.0, 100.0)), niceAxis(0.0, 100.0))
        assertEquals(listOf(0.0, 2.0, 4.0, 6.0, 8.0), niceAxis(0.0, 7.6).ticks)
        assertEquals(listOf(45.0, 50.0, 55.0, 60.0, 65.0), niceAxis(47.3, 61.2).ticks)
        assertEquals(listOf(-20.0, 0.0, 20.0, 40.0, 60.0), niceAxis(-12.0, 30.0).ticks)
    }

    @Test
    fun decimalsWithoutFloatDust() {
        assertEquals(listOf(0.0, 0.1, 0.2, 0.3, 0.4), niceAxis(0.0, 0.37).ticks)
    }

    @Test
    fun aFlatSeriesStillGetsAnAxis() {
        // No span: the value's own size sets the step.
        assertEquals(Axis(5.0, 9.0, listOf(5.0, 6.0, 7.0, 8.0, 9.0)), niceAxis(5.0, 5.0))
        assertEquals(Axis(0.0, 0.4, listOf(0.0, 0.1, 0.2, 0.3, 0.4)), niceAxis(0.0, 0.0))
    }

    @Test
    fun theTightestInFourToSixSteps() {
        val tight = tightAxis(0.0, 5312.0)
        assertEquals(listOf(0.0, 1000.0, 2000.0, 3000.0, 4000.0, 5000.0, 6000.0), tight.ticks)
        assertEquals(0.0, tight.min, 0.0)
        assertEquals(6000.0, tight.max, 0.0)
        assertEquals(8000.0, niceAxis(0.0, 5312.0).ticks.last(), 0.0)
        // A tie keeps the fewer steps.
        assertEquals(listOf(0.0, 25.0, 50.0, 75.0, 100.0), tightAxis(0.0, 100.0).ticks)
    }
}
