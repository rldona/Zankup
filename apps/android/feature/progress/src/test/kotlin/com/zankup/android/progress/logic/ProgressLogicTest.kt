package com.zankup.android.progress.logic

import com.zankup.android.api.model.Bucket
import com.zankup.android.api.model.CalendarActivity
import com.zankup.android.api.model.CalendarDay
import com.zankup.android.api.model.FitnessDay
import com.zankup.android.api.model.Totals
import com.zankup.android.domain.EffortStatus
import com.zankup.android.domain.Sport
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressLogicTest {
    private val wednesday = LocalDate.of(2026, 11, 18)

    private fun totals(moving: Double = 0.0, distance: Double = 0.0, load: Double = 0.0, count: Int = 1) =
        Totals(count = count, distanceM = distance, movingS = moving, ascentM = 0.0, load = load)

    private fun activity(id: Int, sport: String, moving: Double, distance: Double? = null) =
        CalendarActivity(id, sport, null, null, distance, moving, "2026-11-18T07:00:00Z")

    @Test
    fun weeksStartOnMonday() {
        assertEquals(LocalDate.of(2026, 11, 16), mondayOf(wednesday))
        assertEquals(LocalDate.of(2026, 11, 2), weekAt(2, wednesday))
        assertEquals(7, weekDays(mondayOf(wednesday)).size)
        assertEquals(3, weekCount(LocalDate.of(2026, 11, 4), wednesday))
        assertEquals(1, weekCount(null, wednesday))
        assertEquals(2, indexOfWeek(LocalDate.of(2026, 11, 3), wednesday))
    }

    @Test
    fun monthGridCoversWholeWeeks() {
        val (from, to) = monthGrid(YearMonth.of(2026, 11))
        assertEquals(LocalDate.of(2026, 10, 26), from)
        assertEquals(LocalDate.of(2026, 12, 7), to)
        assertEquals(
            listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)),
            monthsBack(LocalDate.of(2026, 10, 9), wednesday)
        )
    }

    @Test
    fun rangesCoverTheSpanAndTheOneBefore() {
        val week = volumeRange(ProgressRange.D7, wednesday)
        assertEquals(DayRange(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 19)), week.current)
        assertEquals(DayRange(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 12)), week.previous)
        val months = volumeRange(ProgressRange.M3, wednesday)
        assertEquals(LocalDate.of(2026, 8, 31), months.current.from)
        assertEquals(LocalDate.of(2026, 6, 8), months.previous.from)
        val ytd = volumeRange(ProgressRange.YTD, wednesday)
        assertEquals(DayRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 11, 19)), ytd.previous)
    }

    @Test
    fun bucketsAddUpBySportGroup() {
        val bucket = Bucket(
            "2026-11-16",
            totals(moving = 3600.0),
            mapOf(
                "run" to totals(moving = 1800.0),
                "trail" to totals(moving = 600.0),
                "ride" to totals(moving = 1200.0)
            )
        )
        assertEquals(3600.0, bucketValue(bucket, VolumeMetric.TIME, null), 0.0)
        assertEquals(1200.0, bucketValue(bucket, VolumeMetric.TIME, Sport.RIDE), 0.0)
        assertEquals(listOf(Sport.RUN, Sport.RIDE, Sport.OTHER), sportsIn(listOf(bucket)))
    }

    @Test
    fun totalsAndTicks() {
        assertEquals("4 h 17 min", hoursMinutes(4 * 3600 + 17 * 60.0))
        assertEquals("30 min", hoursMinutes(1800.0))
        assertEquals("0 min", hoursMinutes(0.0))
        assertEquals("6 h", hoursMinutes(6 * 3600.0))
        assertEquals(listOf(0.0, 1800.0, 3600.0), axisTicks(3000.0, VolumeMetric.TIME, 2))
        assertEquals(listOf(0.0, 10_000.0, 20_000.0), axisTicks(18_000.0, VolumeMetric.DISTANCE, 2))
        assertEquals(12, percentChange(112.0, 100.0))
        assertNull(percentChange(5.0, 0.0))
    }

    @Test
    fun glanceSaysHowItWent() {
        assertEquals(Glance.None, glance(null, 0.0, 10.0))
        assertEquals(Glance.Up(50), glance(null, 15.0, 10.0))
        assertEquals(Glance.Down(50), glance(null, 5.0, 10.0))
        assertEquals(Glance.CompareDown(50), glance(2, 5.0, 10.0))
        assertEquals(Glance.New, glance(null, 5.0, 0.0))
    }

    @Test
    fun comparableYearsReachTheFirstActivity() {
        val span = volumeRange(ProgressRange.M1, wednesday).current
        assertEquals(listOf(1, 2), comparableYears(span, LocalDate.of(2024, 6, 1)))
        assertEquals(emptyList<Int>(), comparableYears(span, null))
    }

    @Test
    fun bubbles() {
        assertEquals(0, bubbleSize(0.0, LogMetric.TIME))
        assertEquals(54, bubbleSize(9000.0, LogMetric.TIME))
        assertEquals("0:50", bubbleLabel(3000.0, LogMetric.TIME, Locale.ENGLISH, false))
        assertEquals("12,3", bubbleLabel(12_300.0, LogMetric.DISTANCE, Locale.forLanguageTag("es"), false))
    }

    @Test
    fun streakMarks() {
        val monday = mondayOf(wednesday)
        val since = monday.minusWeeks(2)
        assertEquals(WeekMark.CURRENT, weekMark(monday, wednesday, false, since, 3))
        assertEquals(WeekMark.STREAK, weekMark(since, wednesday, true, since, 3))
        assertEquals(WeekMark.MISSED, weekMark(since.minusWeeks(1), wednesday, false, since, 3))
        assertEquals(WeekMark.FUTURE, weekMark(monday.plusWeeks(1), wednesday, false, since, 3))
    }

    @Test
    fun fitnessRunsUntilToday() {
        val days = listOf(FitnessDay("2026-11-16", 50.0, 42.0, 40.0, 2.0))
        val out = untilToday(days, wednesday)
        assertEquals(3, out.size)
        assertEquals("2026-11-18", out.last().day)
        assertEquals(42.0 * (41.0 / 42) * (41.0 / 42), out.last().ctl, 1e-9)
        assertEquals(Change(-23, 24, Direction.DOWN), fitnessChange(96.0, 73.0))
        assertEquals(listOf(0.0, 20.0, 40.0, 60.0, 80.0, 100.0, 120.0), fitnessScale(listOf(106.0)))
        assertEquals(listOf(0, 7, 15, 22, 29), spreadDays(30))
    }

    @Test
    fun effortWeeksAreJudgedByTheThreeBefore() {
        val loads = listOf(100.0, 200.0, 300.0, 500.0)
        val buckets = loads.mapIndexed { i, l ->
            Bucket(weekAt(3 - i, wednesday).toString(), totals(load = l), emptyMap())
        }
        val weeks = effortWeeks(buckets)
        assertEquals(1, weeks.size)
        assertEquals(500, weeks[0].score)
        assertEquals(133, weeks[0].range?.lo)
        assertEquals(267, weeks[0].range?.hi)
        assertEquals(EffortStatus.FAR_ABOVE, weeks[0].status)
    }

    @Test
    fun predictions() {
        assertEquals("24:54", raceTime(1494.0))
        assertEquals("2:01:35", raceTime(7295.0))
        assertEquals("4:59", racePace(1495.0, 5000.0, false))
        assertEquals("3 min 46 s", changeText(-226.0))
        val history = listOf(Point(LocalDate.of(2026, 10, 1), 1500.0), Point(LocalDate.of(2026, 11, 10), 1480.0))
        assertEquals(1500.0, valueOn(history, LocalDate.of(2026, 10, 20))!!, 0.0)
        val line = series(history, LocalDate.of(2026, 10, 19), wednesday)
        assertEquals(listOf(1500.0, 1480.0, 1480.0), line.map { it.value })
        assertEquals(listOf(1440.0, 1470.0, 1500.0, 1530.0), timeTicks(1480.0, 1500.0))
    }

    @Test
    fun cards() {
        assertEquals(TopZone(1, 60), topZone(listOf(10.0, 60.0, 30.0)))
        assertNull(topZone(listOf(0.0, 0.0)))
        assertEquals(10, shareChange(listOf(10.0, 60.0, 30.0), listOf(50.0, 50.0, 0.0), 1))
        assertEquals("15 s", durationLabel(15))
        assertEquals("2 min", durationLabel(120))
        assertEquals("102.2 km", bestValue("longest", 102_200.0, Locale.ENGLISH, false))
        assertEquals(listOf("5k", "10k"), cardMedals(listOf("5k", "1mile", "5k", "10k"), { it }, imperial = false))
        assertEquals(0.5f, goalFraction(50.0, 100.0))
        assertEquals(1.0, radialShare(9.0), 1e-9)
    }

    @Test
    fun monthsAddUpDayByDay() {
        val calendar = listOf(
            CalendarDay("2026-11-02", null, listOf(activity(1, "run", 1800.0, 5000.0))),
            CalendarDay("2026-11-10", null, listOf(activity(2, "ride", 3600.0)))
        )
        val month = monthOf(calendar, YearMonth.of(2026, 11), wednesday)
        assertEquals(2, month.count)
        assertEquals(5400.0, month.time, 0.0)
        assertEquals(18, month.cumulative.size)
        assertEquals(1800.0, month.cumulative[2], 0.0)
        assertEquals(DayRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 1)), monthPairRange(wednesday))
    }
}
