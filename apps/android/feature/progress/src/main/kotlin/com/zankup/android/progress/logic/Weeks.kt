package com.zankup.android.progress.logic

import com.zankup.android.api.model.CalendarActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The Monday of the week [day] is in. */
fun mondayOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** The Monday of the week [index] weeks back from this one (0 is this week). */
fun weekAt(index: Int, today: LocalDate): LocalDate = mondayOf(today).minusWeeks(index.toLong())

/** The seven days of the week starting on [monday]. */
fun weekDays(monday: LocalDate): List<LocalDate> = (0L until 7L).map { monday.plusDays(it) }

/** How many weeks the log has: from the first activity's week to this one, both in. */
fun weekCount(firstActivity: LocalDate?, today: LocalDate): Int {
    firstActivity ?: return 1
    return (ChronoUnit.WEEKS.between(mondayOf(firstActivity), mondayOf(today)).toInt() + 1).coerceAtLeast(1)
}

/** The index of the week [day] falls in, counted back from this week. */
fun indexOfWeek(day: LocalDate, today: LocalDate): Int =
    ChronoUnit.WEEKS.between(mondayOf(day), mondayOf(today)).toInt()

/** Every month from this one back to the first activity's, newest first. */
fun monthsBack(first: LocalDate, today: LocalDate): List<YearMonth> {
    val end = YearMonth.from(first)
    return generateSequence(YearMonth.from(today)) { it.minusMonths(1) }.takeWhile { it >= end }.toList()
}

/** A month's grid: from the Monday on or before its first day to the Monday after its last week (excluded). */
fun monthGrid(month: YearMonth): Pair<LocalDate, LocalDate> {
    val start = mondayOf(month.atDay(1))
    val next = month.plusMonths(1).atDay(1)
    val end = next.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
    return start to end
}

/** What a day's bubble on the training log shows. */
enum class LogMetric(internal val full: Double) {
    TIME(7200.0),
    DISTANCE(40_000.0),
    ASCENT(1000.0)
}

fun valueOf(activity: CalendarActivity, metric: LogMetric): Double = when (metric) {
    LogMetric.TIME -> activity.movingS
    LogMetric.DISTANCE -> activity.distanceM ?: 0.0
    LogMetric.ASCENT -> activity.ascentM ?: 0.0
}

fun total(activities: List<CalendarActivity>, metric: LogMetric): Double = activities.sumOf { valueOf(it, metric) }

/** A day's bubble, from 26 dp (a few minutes) to [max] (two hours, 40 km, 1000 m or more); 0 for nothing. */
fun bubbleSize(value: Double, metric: LogMetric, max: Int = 54): Int {
    if (value <= 0) return 0
    return (26 + (max - 26) * sqrt(min(1.0, value / metric.full))).roundToInt()
}

/** The card's dot for a day: bigger, longer (10 to 26 dp); a speck on a rest day, a ring today. */
fun dotSize(seconds: Double, today: Boolean): Int = when {
    seconds > 0 -> (10 + 16 * sqrt(min(1.0, seconds / 7200))).roundToInt()
    today -> 10
    else -> 5
}

/** What a bubble says: 0:50 · 12.3 (km or mi) · 439 (m or ft). */
fun bubbleLabel(value: Double, metric: LogMetric, locale: Locale, imperial: Boolean): String = when (metric) {
    LogMetric.TIME -> {
        val minutes = (value / 60).roundToInt()
        "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
    }
    LogMetric.DISTANCE -> {
        val v = value / if (imperial) MILE else 1000.0
        number(v, locale, if (v >= 100) 0 else 1, trim = true)
    }
    LogMetric.ASCENT -> number(if (imperial) value * FEET_PER_METRE else value, locale, 0)
}

/** A week's place in the Series column. */
enum class WeekMark {
    STREAK,
    CURRENT,
    DONE,
    MISSED,
    FUTURE
}

/**
 * The Series column: a week of the streak (ticked, on the band), this week when the streak reaches it
 * (the flame), another week with activity (ticked), a past week without (empty) or one still to come.
 */
fun weekMark(monday: LocalDate, today: LocalDate, active: Boolean, since: LocalDate?, weeks: Int): WeekMark {
    val thisWeek = mondayOf(today)
    if (monday > thisWeek) return WeekMark.FUTURE
    val inStreak = since != null && weeks > 0 && monday >= since
    if (monday == thisWeek) {
        return when {
            inStreak -> WeekMark.CURRENT
            active -> WeekMark.DONE
            else -> WeekMark.FUTURE
        }
    }
    if (inStreak) return WeekMark.STREAK
    return if (active) WeekMark.DONE else WeekMark.MISSED
}

/** A day of the API ("2026-11-18"), or null when it is not one. */
fun day(iso: String?): LocalDate? = iso?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
