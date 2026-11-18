package com.zankup.android.home

import com.zankup.android.api.model.ActivityPage
import com.zankup.android.api.model.ActivitySummary
import com.zankup.android.api.model.Day
import com.zankup.android.api.model.DayActivity
import com.zankup.android.api.model.EstimateOut
import com.zankup.android.api.model.FitnessDay
import com.zankup.android.api.model.HealthDay
import com.zankup.android.api.model.Value
import com.zankup.android.api.model.Week
import com.zankup.android.api.model.WeekTotals
import com.zankup.android.data.Loadable
import com.zankup.android.domain.Sport
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLogicTest {
    private val utc = ZoneId.of("UTC")

    // Wednesday 18 November 2026.
    private val today = LocalDate.of(2026, 11, 18)

    private fun totals(moving: Double, start: String = "2026-11-16") = WeekTotals(start, 3, 30_000.0, moving, 200.0)

    private fun week(days: List<Day> = emptyList(), moving: Double = 3600.0, previous: List<Double> = emptyList()) =
        Week(
            today = today.toString(),
            days = days,
            thisWeek = totals(moving),
            previous = previous.map { totals(it, "2026-11-09") },
            streakWeeks = 2,
            records = emptyList()
        )

    private fun activity(id: Int, start: String, sport: String = "run", timezone: String? = null) = ActivitySummary(
        id = id,
        name = null,
        sport = sport,
        subSport = null,
        source = "garmin",
        startUtc = start,
        timezone = timezone,
        durationS = 1800.0,
        movingS = 1700.0,
        distanceM = 5000.0,
        ascentM = null,
        avgHr = null,
        avgPower = null,
        hasGps = false,
        route = null
    )

    private fun value(v: Double) = Value(v, Value.Source.GARMIN, emptyList())

    private fun health(day: String, steps: Double? = null, resting: Double? = null, hrv: Double? = null) = HealthDay(
        day = day,
        sleep = null,
        restingHr = resting?.let(::value),
        hrv = hrv?.let(::value),
        spo2 = null,
        respiration = null,
        steps = steps?.let(::value),
        tsb = null,
        readiness = null
    )

    // --- the week's days ------------------------------------------------------------------------

    @Test
    fun weekRunsMondayToSundayWithTodayAndTheDaysToCome() {
        val days = weekDays(week(), today)
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 11, 16), days.first().date)
        assertEquals(LocalDate.of(2026, 11, 22), days.last().date)
        assertEquals(listOf(false, false, true, false, false, false, false), days.map { it.isToday })
        assertEquals(listOf(false, false, false, true, true, true, true), days.map { it.isFuture })
    }

    @Test
    fun aDayTakesItsFirstActivityAndADotForTheSecond() {
        val monday =
            Day(
                "2026-11-16",
                listOf(DayActivity(7, "ride", null), DayActivity(8, "run", null), DayActivity(9, "x", null))
            )
        val days = weekDays(week(listOf(monday, Day("2026-11-17", listOf(DayActivity(3, "yoga", null))))), today)
        assertEquals(Sport.RIDE, days[0].first)
        assertEquals(7L, days[0].firstId)
        assertEquals(Sport.RUN, days[0].second)
        assertEquals(Sport.OTHER, days[1].first)
        assertNull(days[1].second)
        assertNull(days[2].first)
        assertNull(days[2].firstId)
    }

    @Test
    fun onSundayTheWeekStillStartsOnMonday() {
        val days = weekDays(week(), LocalDate.of(2026, 11, 22))
        assertEquals(LocalDate.of(2026, 11, 16), days.first().date)
        assertTrue(days.last().isToday)
        assertTrue(days.none { it.isFuture })
    }

    // --- against a usual week -------------------------------------------------------------------

    @Test
    fun usualWeekIsTheShareOfThePreviousWeeksAverage() {
        val usual = usualWeek(week(moving = 6000.0, previous = listOf(4000.0, 6000.0)))!!
        assertEquals(5000.0, usual.averageS, 0.0)
        assertEquals(120, usual.share)
        assertEquals(0.8f, usual.fill, 1e-6f)
        assertEquals(2f / 3f, usual.mark, 1e-6f)
    }

    @Test
    fun usualWeekBarStopsAtOneAndAHalfUsualWeeks() {
        assertEquals(1f, usualWeek(week(moving = 9000.0, previous = listOf(3000.0)))!!.fill, 0f)
        assertEquals(0f, usualWeek(week(moving = 0.0, previous = listOf(3000.0)))!!.fill, 0f)
    }

    @Test
    fun noUsualWeekWithoutHistory() {
        assertNull(usualWeek(week(previous = emptyList())))
        assertNull(usualWeek(week(previous = listOf(0.0, 0.0))))
    }

    // --- the last day ---------------------------------------------------------------------------

    @Test
    fun lastDayGroupsTheLatestDaysActivitiesNewestFirst() {
        val items =
            listOf(
                activity(1, "2026-11-18T07:00:00Z"),
                activity(2, "2026-11-18T18:00:00Z"),
                activity(3, "2026-11-17T18:00:00Z")
            )
        val last = lastDay(items, utc)!!
        assertEquals(today, last.day)
        assertEquals(listOf(2, 1), last.activities.map { it.id })
    }

    @Test
    fun anActivityCountsOnItsOwnTimeZonesDay() {
        // 23:30 UTC on the 17th is already the 18th in Madrid.
        val a = activity(1, "2026-11-17T23:30:00Z", timezone = "Europe/Madrid")
        assertEquals(today, activityDay(a, utc))
        assertEquals(LocalDate.of(2026, 11, 17), activityDay(a.copy(timezone = null), utc))
        assertEquals(LocalDate.of(2026, 11, 17), activityDay(a.copy(startUtc = "2026-11-17T10:00:00"), utc))
        assertNull(activityDay(a.copy(startUtc = "yesterday"), utc))
    }

    @Test
    fun onlyTodayOrYesterdayIsRecent() {
        assertEquals(DayHeading.TODAY, dayHeading(today, today))
        assertEquals(DayHeading.YESTERDAY, dayHeading(today.minusDays(1), today))
        assertEquals(DayHeading.DATE, dayHeading(today.minusDays(2), today))
        assertNull(recentDay(lastDay(listOf(activity(1, "2026-11-15T10:00:00Z")), utc), today))
        assertNull(recentDay(lastDay(emptyList(), utc), today))
        assertEquals(
            today.minusDays(1),
            recentDay(lastDay(listOf(activity(1, "2026-11-17T10:00:00Z")), utc), today)?.day
        )
    }

    @Test
    fun theDaysHealthIsTheRecentDaysElseTodaysElseYesterdays() {
        val days = listOf(health("2026-11-17", steps = 9000.0), health("2026-11-18", resting = 50.0))
        val yesterday = LastDay(today.minusDays(1), emptyList())
        assertEquals("2026-11-17", dayHealth(yesterday, today, days)?.day)
        assertEquals("2026-11-18", dayHealth(null, today, days)?.day)
        // Today has nothing yet: yesterday's.
        val empty = listOf(health("2026-11-17", hrv = 40.0), health("2026-11-18"))
        assertEquals("2026-11-17", dayHealth(null, today, empty)?.day)
        // A day without steps, heart rate or HRV shows nothing.
        assertNull(dayHealth(LastDay(today, emptyList()), today, empty))
    }

    // --- the last seven days --------------------------------------------------------------------

    @Test
    fun lastSevenDaysAddUpBySportTheMostTimeFirst() {
        val items =
            listOf(
                activity(1, "2026-11-18T07:00:00Z", "run"),
                activity(2, "2026-11-14T07:00:00Z", "ride").copy(movingS = 7200.0, distanceM = 60_000.0),
                activity(3, "2026-11-12T07:00:00Z", "run"),
                // Eight days ago: outside.
                activity(4, "2026-11-10T07:00:00Z", "swim")
            )
        val sports = lastSevenDays(items, today, utc)
        assertEquals(listOf(Sport.RIDE, Sport.RUN), sports.map { it.sport })
        assertEquals(SportTotals(Sport.RUN, 2, 10_000.0, 3400.0), sports[1])
        assertEquals(60_000.0, sports[0].distanceM, 0.0)
    }

    @Test
    fun healthAveragesSkipTheDaysWithout() {
        val days = listOf(health("a", steps = 1000.0), health("b"), health("c", steps = 3000.0))
        assertEquals(2000.0, healthAverage(days) { it.steps }!!, 0.0)
        assertNull(healthAverage(days) { it.hrv })
    }

    // --- the gauges -----------------------------------------------------------------------------

    @Test
    fun formTonesFollowTheBands() {
        assertEquals(FormTone.FRESH, formTone(6.0))
        assertEquals(FormTone.NEUTRAL, formTone(5.0))
        assertEquals(FormTone.NEUTRAL, formTone(-9.0))
        assertEquals(FormTone.OPTIMAL, formTone(-10.0))
        assertEquals(FormTone.OPTIMAL, formTone(-29.0))
        assertEquals(FormTone.OVERREACHING, formTone(-30.0))
        // The gauge's dot sits in the band of the pill's tone.
        listOf(-45.0, -30.0, -20.0, -10.0, 0.0, 5.0, 12.0, 40.0).forEach { tsb ->
            assertEquals("tsb $tsb", formTone(tsb), FormGauge.tones[FormGauge.scale.band(tsb)])
        }
    }

    @Test
    fun theFormGaugeRunsFromMinusFortyToThirty() {
        val scale = FormGauge.scale
        assertEquals(30.0, scale.max, 0.0)
        assertEquals(0f, scale.fraction(-50.0), 0f)
        assertEquals(1f, scale.fraction(50.0), 0f)
        assertEquals(0.5f, scale.fraction(-5.0), 1e-6f)
        val spans = scale.spans
        assertEquals(4, spans.size)
        assertEquals(0f, spans.first().start, 0f)
        assertEquals(1f, spans.last().endInclusive, 0f)
        spans.zipWithNext().forEach { (a, b) -> assertEquals(a.endInclusive, b.start, 1e-6f) }
        assertEquals(10f / 70f, spans[0].endInclusive, 1e-6f)
    }

    @Test
    fun equalBandsSplitTheScaleInFive() {
        assertEquals(listOf(38.0, 46.0, 54.0, 62.0, 70.0), Vo2Scale.ends)
        assertEquals(0, Vo2Scale.band(20.0))
        assertEquals(2, Vo2Scale.band(50.0))
        assertEquals(4, Vo2Scale.band(90.0))
    }

    // --- the metrics ----------------------------------------------------------------------------

    @Test
    fun metricsTakeTheEstimatesTheLatestFormAndTodaysSteps() {
        fun estimate(kind: String, v: Double) = EstimateOut(kind, v, "auto", "2026-11-01", emptyList(), emptyList())
        val m =
            metrics(
                estimates = listOf(estimate("vdot", 52.4), estimate("ftp", 280.0)),
                fitness = listOf(
                    FitnessDay("2026-11-18", 50.0, 60.0, 70.0, -10.0),
                    FitnessDay("2026-11-17", 0.0, 1.0, 1.0, 0.0)
                ),
                weightKg = 70.0,
                health = listOf(health("2026-11-17", steps = 9000.0), health("2026-11-18", steps = 2500.0)),
                today = today
            )
        assertEquals(52.4, m.vdot!!, 0.0)
        assertEquals(4.0, m.wattsPerKg!!, 1e-9)
        assertEquals(4.0, m.ftpGaugeValue!!, 1e-9)
        assertEquals("2026-11-18", m.form?.day)
        assertEquals(0.25f, m.stepsShare, 1e-6f)
    }

    @Test
    fun withoutAWeightFtpIsDrawnAgainstAUsualOne() {
        val m = Metrics(ftp = 300.0, stepsToday = 25_000.0)
        assertNull(m.wattsPerKg)
        assertEquals(4.0, m.ftpGaugeValue!!, 1e-9)
        assertEquals(1f, m.stepsShare, 0f)
        assertNull(metrics(null, null, null, null, today).vdot)
    }

    // --- Home as a whole ------------------------------------------------------------------------

    @Test
    fun homeShowsTheSkeletonThenTheWeekAndTheEmptyFeed() {
        val loading = homeUi(today, utc, Loadable(), Loadable(), Loadable(), Loadable(), Loadable(), Loadable())
        assertTrue(loading.weekLoading)
        assertNull(loading.week)
        assertFalse(loading.feedLoaded)

        val empty =
            homeUi(
                today,
                utc,
                Loadable(week()),
                Loadable(ActivityPage(emptyList(), null)),
                Loadable(),
                Loadable(),
                Loadable(),
                Loadable()
            )
        assertFalse(empty.weekLoading)
        assertEquals(7, empty.week?.days?.size)
        assertEquals(2, empty.week?.streak)
        assertTrue(empty.feedLoaded)
        assertTrue(empty.empty)
        assertNull(empty.recent)
    }
}
