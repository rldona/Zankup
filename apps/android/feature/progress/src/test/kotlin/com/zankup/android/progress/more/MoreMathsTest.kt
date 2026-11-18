package com.zankup.android.progress.more

import com.zankup.android.api.model.BestOut
import com.zankup.android.api.model.CalendarActivity
import com.zankup.android.api.model.CalendarDay
import com.zankup.android.api.model.Totals
import com.zankup.android.domain.Units
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalMathsTest {
    private fun goal(period: String, target: Double, current: Double, start: String, end: String) =
        GoalFigures(period, "distance", target, current, LocalDate.parse(start), LocalDate.parse(end))

    @Test
    fun ringFillsUpToOne() {
        assertEquals(0.5f, fraction(100.0, 50.0))
        assertEquals(1f, fraction(100.0, 150.0))
        assertEquals(0f, fraction(0.0, 10.0))
    }

    @Test
    fun leftIsNegativeWhenOver() {
        assertEquals(-20.0, left(goal("week", 100.0, 120.0, "2026-11-16", "2026-11-23")), 1e-9)
    }

    @Test
    fun behindAnEvenPace() {
        // November has 30 days: on the 15th an even pace has done half of 300 km.
        val g = goal("month", 300_000.0, 100_000.0, "2026-11-01", "2026-12-01")
        assertEquals(50_000.0, behind(g, LocalDate.parse("2026-11-15")), 1e-6)
        val ahead = g.copy(current = 200_000.0)
        assertEquals(-50_000.0, behind(ahead, LocalDate.parse("2026-11-15")), 1e-6)
        // After the end it counts the whole period.
        assertEquals(200_000.0, behind(g, LocalDate.parse("2026-12-20")), 1e-6)
    }

    @Test
    fun chartPeriodsByGoal() {
        val today = LocalDate.parse("2026-11-18")
        assertEquals(
            ChartSpan(LocalDate.parse("2026-08-31"), LocalDate.parse("2026-11-19"), "week"),
            chartPeriods("week", today)
        )
        assertEquals(
            ChartSpan(LocalDate.parse("2025-12-01"), LocalDate.parse("2026-11-19"), "month"),
            chartPeriods("month", today)
        )
        assertEquals(
            ChartSpan(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-11-19"), "month"),
            chartPeriods("year", today)
        )
    }

    @Test
    fun runningTotals() {
        assertEquals(listOf(1.0, 3.0, 6.0), runningTotal(listOf(1.0, 2.0, 3.0)))
    }

    @Test
    fun targetsRoundTrip() {
        assertEquals(TargetInput("5", "30"), toInput("time", 5.5 * 3600, false))
        assertEquals(5.5 * 3600, fromInput("time", "5", "30", false), 1e-9)
        assertEquals(TargetInput("12.5"), toInput("distance", 12_500.0, false))
        assertEquals(TargetInput("100"), toInput("distance", 100_000.0, false))
        assertEquals(1609.344 * 10, fromInput("distance", "10", "", true), 1e-6)
        assertEquals(12_500.0, fromInput("distance", "12,5", "", false), 1e-9)
        assertEquals(TargetInput("3281"), toInput("ascent", 1000.0, true))
        assertEquals(4.0, fromInput("count", "3.6", "", false), 1e-9)
        assertEquals(0.0, fromInput("distance", "abc", "", false), 1e-9)
    }

    @Test
    fun takenKindsLeaveOutTheEditedGoal() {
        val goals =
            listOf(
                GoalSlot(1, "week", "distance", null),
                GoalSlot(2, "week", "time", null),
                GoalSlot(3, "week", "count", "run"),
                GoalSlot(4, "month", "ascent", null)
            )
        assertEquals(setOf("distance", "time"), takenKinds(goals, null, "week", null))
        assertEquals(setOf("time"), takenKinds(goals, 1, "week", null))
        assertEquals(setOf("count"), takenKinds(goals, null, "week", "run"))
    }

    @Test
    fun metricsOfABucket() {
        val t = Totals(count = 3, distanceM = 12_000.0, movingS = 3600.0, ascentM = 150.0)
        assertEquals(3.0, metricOf(t, "count"), 0.0)
        assertEquals(3600.0, metricOf(t, "time"), 0.0)
        assertEquals(12_000.0, metricOf(t, "distance"), 0.0)
        assertEquals(150.0, metricOf(t, "ascent"), 0.0)
        assertEquals(0.0, metricOf(null, "time"), 0.0)
    }

    @Test
    fun volumeValues() {
        assertEquals("4h 17min", hoursMinutes(4 * 3600 + 17 * 60.0))
        assertEquals("45min", hoursMinutes(45 * 60.0))
        assertEquals("0h 0min", hoursMinutes(0.0))
        assertEquals("28.97 km", volumeValue("distance", 28_970.0, false, Locale.ENGLISH))
        assertEquals("0 km", volumeValue("distance", 0.0, false, Locale.ENGLISH))
        assertEquals("1421 m", volumeValue("ascent", 1421.0, false, Locale.forLanguageTag("es")))
    }
}

class ZoneMathsTest {
    @Test
    fun kindsBySport() {
        assertEquals(listOf(ZoneKind.HR, ZoneKind.PACE), zoneKinds(com.zankup.android.domain.Sport.RUN))
        assertEquals(listOf(ZoneKind.HR, ZoneKind.POWER), zoneKinds(com.zankup.android.domain.Sport.RIDE))
        assertEquals(listOf(ZoneKind.HR), zoneKinds(null))
        assertEquals(ZoneKind.PACE, defaultZoneKind(com.zankup.android.domain.Sport.RUN))
    }

    @Test
    fun topZoneAndItsChange() {
        val now = listOf(10.0, 50.0, 30.0, 10.0, 0.0)
        assertEquals(TopZone(1, 50), topZone(now))
        assertNull(topZone(listOf(0.0, 0.0)))
        val before = listOf(25.0, 25.0, 25.0, 25.0, 0.0)
        assertEquals(25, shareChange(now, before, 1))
        assertNull(shareChange(now, emptyList(), 1))
    }

    @Test
    fun rangesAsStravaWrites() {
        val bounds = listOf(0.0, 111.0, 131.0, 151.0, 171.0)
        assertEquals("<110", zoneRange(bounds, 0))
        assertEquals("112 - 131", zoneRange(bounds, 1))
        assertEquals(">171", zoneRange(bounds, 4))
        val speeds = listOf(0.0, 2.5, 3.0)
        assertEquals("> 6:40", paceRange(speeds, 0, 1000.0))
        assertEquals("5:33 - 6:40", paceRange(speeds, 1, 1000.0))
        assertEquals("< 5:33", paceRange(speeds, 2, 1000.0))
    }

    @Test
    fun glanceFindsTheBiggestMove() {
        val g = glance(listOf(10.0, 10.0, 10.0, 40.0, 10.0), listOf(10.0, 10.0, 10.0, 20.0, 10.0))
        assertEquals(Glance(3, 100, true, Band.HARD), g)
        assertNull(glance(listOf(1.0), listOf(0.0)))
        assertEquals(Band.EASY, glance(listOf(5.0, 10.0), listOf(10.0, 10.0))?.band)
    }

    @Test
    fun changeTexts() {
        assertEquals("27s", changeText(27.0))
        assertEquals("3min 46s", changeText(226.0))
        assertEquals("1h 2min", changeText(3720.0))
    }

    @Test
    fun rangeSpans() {
        val today = LocalDate.parse("2026-11-18")
        assertEquals(
            DaySpan(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-11-19")),
            rangeSpan(ProgressRange.M1, today)
        )
        assertEquals(
            DaySpan(LocalDate.parse("2026-11-12"), LocalDate.parse("2026-11-19")),
            rangeSpan(ProgressRange.D7, today)
        )
        assertEquals(
            DaySpan(LocalDate.parse("2026-08-31"), LocalDate.parse("2026-11-19")),
            rangeSpan(ProgressRange.M3, today)
        )
    }

    @Test
    fun scaleColoursSpread() {
        assertEquals(5, zoneScale(ZoneKind.HR, 5).size)
        assertEquals(zoneScale(ZoneKind.POWER, 7).last(), zoneScale(ZoneKind.POWER, 3).last())
    }
}

class BestMathsTest {
    private fun best(id: Int, day: String, value: Double) = BestOut(id, null, day, value)

    private val runs =
        listOf(
            best(1, "2025-03-01", 3000.0),
            best(2, "2025-06-01", 2900.0),
            best(3, "2026-01-10", 2950.0),
            best(4, "2026-05-10", 2850.0),
            best(5, "2026-09-10", 2950.0)
        )

    @Test
    fun rankingAndPlaces() {
        assertEquals(listOf(4, 2, 3, 5, 1), ranked("10k", runs).map { it.activityId })
        assertEquals(3, placeOf("10k", runs, runs[2]))
        assertEquals(3, placeOf("10k", runs, runs[4]))
        // Power: more is better.
        assertEquals(listOf(1, 3, 5, 2, 4), ranked("power_60", runs).map { it.activityId })
    }

    @Test
    fun yearMedalsAndYears() {
        assertEquals(1, yearMedal("10k", runs, runs[3]))
        assertEquals(2, yearMedal("10k", runs, runs[2]))
        assertEquals(listOf("2026", "2025"), yearsOf(runs))
    }

    @Test
    fun valuesAndPaces() {
        assertEquals("47:30", bestValue("10k", 2850.0, Locale.ENGLISH, false))
        assertEquals("1:02:05", bestValue("half_marathon", 3725.0, Locale.ENGLISH, false))
        assertEquals("551 W", bestValue("power_5", 550.6, Locale.ENGLISH, false))
        assertEquals("102,2 km", bestValue("longest", 102_200.0, Locale.forLanguageTag("es"), false))
        assertEquals("4:45", racePace(2850.0, 10_000.0, false))
    }

    @Test
    fun chartOffsets() {
        assertEquals(-7f..-7f, offsetRange(5))
        assertEquals(0f..8f, offsetRange(20))
        assertEquals(19, rightIndex(8f, 20))
        assertEquals(4, rightIndex(-7f, 5))
    }

    @Test
    fun scaleAndTrend() {
        assertEquals(2850.0 to 2950.0, chartScale("10k", runs))
        val two = listOf(best(1, "2026-01-01", 100.0), best(2, "2026-01-20", 200.0), best(3, "2026-06-01", 300.0))
        assertEquals(listOf(150.0, 150.0, 300.0), trend(two))
    }

    @Test
    fun mileDistancesOnlyInMiles() {
        assertFalse(effortShown("1mile", Units.METRIC))
        assertTrue(effortShown("1mile", Units.IMPERIAL))
        assertTrue(effortShown("5k", Units.METRIC))
    }
}

class SkillMonthMathsTest {
    @Test
    fun levelsFillSegments() {
        assertEquals(listOf(1f, 1f, 0.5f, 0f, 0f, 0f, 0f, 0f, 0f), segments(2.5))
        assertEquals(1, levelName(0.3))
        assertEquals(9, levelName(9.0))
        assertEquals(0f, radialShare(0.0))
        assertEquals(1f, radialShare(9.0), 1e-6f)
        assertEquals(3f / 10f, radialShare(1.0), 1e-6f)
        assertEquals(com.zankup.android.domain.Skill.ATTACK, skillOf(3))
        assertEquals(com.zankup.android.domain.Skill.CLIMB, skillOf(11))
        assertEquals("45 min", durationLabel(2700))
    }

    private fun day(day: String, vararg activities: Pair<String, Double>) = CalendarDay(
        day,
        null,
        activities.mapIndexed { i, (sport, s) ->
            CalendarActivity(i, sport, null, null, 1000.0, s, "${day}T08:00:00Z", 10.0)
        }
    )

    @Test
    fun monthsAddUpByDay() {
        val today = LocalDate.parse("2026-11-03")
        val calendar =
            listOf(
                day("2026-10-05", "run" to 1800.0),
                day("2026-11-01", "ride" to 3600.0),
                day("2026-11-02", "run" to 1200.0, "ride" to 600.0)
            )
        val pair = months(calendar, today)
        assertEquals(listOf(3600.0, 5400.0, 5400.0), pair.current.cumulative)
        assertEquals(3, pair.current.totals.count)
        assertEquals(31, pair.previous.cumulative.size)
        assertEquals(1800.0, pair.previous.totals.time, 0.0)
        assertEquals(1200.0, months(calendar, today, "run").current.totals.time, 0.0)
        assertEquals(listOf("ride", "run"), monthSports(calendar))
        assertEquals(DaySpan(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-01")), monthPairRange(today))
    }
}
