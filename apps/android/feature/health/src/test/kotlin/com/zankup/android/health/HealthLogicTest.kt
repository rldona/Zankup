package com.zankup.android.health

import com.zankup.android.api.model.HealthDay
import com.zankup.android.api.model.Stage
import com.zankup.android.health.ui.BarSeries
import com.zankup.android.health.ui.BarsLayer
import com.zankup.android.health.ui.LineLayer
import com.zankup.android.health.ui.chartScales
import com.zankup.android.health.ui.dayTicks
import com.zankup.android.health.ui.domainOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthLogicTest {
    private val today = LocalDate.of(2026, 11, 18)

    private fun day(iso: String) = HealthDay(iso, null, null, null, null, null, null, null, null)

    @Test
    fun periodEndsTomorrowExclusive() {
        val range = healthRange(HealthPeriod.D30, today)
        assertEquals(LocalDate.of(2026, 11, 19), range.end)
        assertEquals(LocalDate.of(2026, 10, 20), range.start)
        val year = healthRange(HealthPeriod.D365, today)
        assertEquals(365L, ChronoUnit.DAYS.between(year.start, year.end))
    }

    @Test
    fun spanHasEveryDayWithGaps() {
        val range = healthRange(HealthPeriod.D30, today)
        val span = healthSpan(listOf(day("2026-11-17"), day("2026-10-20")), range)
        assertEquals(30, span.size)
        assertEquals(range.start, span.first().first)
        assertEquals(today, span.last().first)
        assertEquals("2026-10-20", span.first().second?.day)
        assertEquals("2026-11-17", span[span.size - 2].second?.day)
        assertNull(span.last().second)
    }

    @Test
    fun relativeDays() {
        assertEquals(RelativeDay.TODAY, relativeDay(today, today))
        assertEquals(RelativeDay.YESTERDAY, relativeDay(today.minusDays(1), today))
        assertEquals(RelativeDay.OTHER, relativeDay(today.minusDays(2), today))
    }

    private fun stage(offset: Int, duration: Int, s: Stage.Stage) = Stage(offset, duration, s)

    @Test
    fun hypnogramLanesMergeAndJoin() {
        val blocks = sleepBlocks(
            listOf(
                stage(600, 600, Stage.Stage.DEEP),
                stage(0, 300, Stage.Stage.LIGHT),
                stage(300, 300, Stage.Stage.LIGHT),
                stage(1200, 300, Stage.Stage.AWAKE),
                // A gap of more than a minute: not joined.
                stage(1600, 100, Stage.Stage.REM)
            )
        )
        assertEquals(4, blocks.size)
        // Light 0–600, merged; deep sits below it.
        assertEquals(SleepBlock(0, 600, SleepLane.LIGHT.ordinal, null, Side.BELOW), blocks[0])
        assertEquals(SleepBlock(600, 1200, SleepLane.DEEP.ordinal, Side.ABOVE, Side.ABOVE), blocks[1])
        assertEquals(SleepBlock(1200, 1500, SleepLane.AWAKE.ordinal, Side.BELOW, null), blocks[2])
        assertEquals(SleepBlock(1600, 1700, SleepLane.REM.ordinal, null, null), blocks[3])
    }

    @Test
    fun stageTotalsAndBands() {
        val totals = stageTotals(listOf(stage(0, 300, Stage.Stage.LIGHT), stage(300, 200, Stage.Stage.LIGHT)))
        assertEquals(500, totals[SleepLane.LIGHT])
        assertEquals(0, totals[SleepLane.DEEP])
        assertEquals(SleepBand.EXCELLENT, sleepScoreBand(90))
        assertEquals(SleepBand.GOOD, sleepScoreBand(80))
        assertEquals(SleepBand.FAIR, sleepScoreBand(60))
        assertEquals(SleepBand.POOR, sleepScoreBand(59))
        assertEquals(SleepGoal.MET, sleepDurationBand(7 * 3600))
        assertEquals(SleepGoal.ALMOST, sleepDurationBand(6 * 3600))
        assertEquals(SleepGoal.SHORT, sleepDurationBand(6 * 3600 - 1))
    }

    @Test
    fun nightClockUsesTheOffset() {
        assertEquals(LocalTime.of(0, 30), nightClock("2026-11-17T23:30:00Z", 3600))
        assertEquals(LocalTime.of(1, 30), nightClock("2026-11-17T23:30:00", 3600, 3600))
        assertNull(nightClock("nonsense", 0))
    }

    @Test
    fun chartDomainsAndScales() {
        assertEquals(-3.0 to 5.0, domainOf(BarsLayer(listOf(BarSeries("a", Color, listOf(5.0, -3.0, null))))))
        assertEquals(0.0 to 100.0, domainOf(LineLayer("s", Color, listOf(1.0), domain = 0.0 to 100.0)))
        val (lo, hi) = domainOf(LineLayer("l", Color, listOf(50.0, 60.0)))
        assertEquals(49.0, lo, 1e-9)
        assertEquals(61.0, hi, 1e-9)
        val (of, axes) = chartScales(
            listOf(
                BarsLayer(listOf(BarSeries("a", Color, listOf(1.0)))),
                LineLayer("b", Color, listOf(1.0), domain = 0.0 to 100.0),
                LineLayer("c", Color, listOf(1.0), domain = 0.0 to 100.0)
            )
        )
        assertEquals(listOf(0, 1, 1), of)
        assertEquals(2, axes.size)
        assertEquals(listOf(0, 7, 15, 22, 29), dayTicks(30))
        assertEquals(listOf(0, 1, 2), dayTicks(3))
    }

    private companion object {
        val Color = androidx.compose.ui.graphics.Color.Red
    }
}
