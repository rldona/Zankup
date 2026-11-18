package com.zankup.android.progress.logic

import com.zankup.android.api.model.CalendarDay
import com.zankup.android.domain.POWER_LEVELS
import com.zankup.android.domain.SKILL_DURATIONS
import com.zankup.android.domain.Skill
import com.zankup.android.domain.levelAt
import com.zankup.android.domain.skillLevel
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToLong

/** The zone with the most time: its index (0 is Z1) and share (0 to 100); null without any time. */
data class TopZone(val index: Int, val share: Int)

fun topZone(seconds: List<Double>): TopZone? {
    val total = seconds.sum()
    if (total <= 0) return null
    var index = 0
    seconds.forEachIndexed { i, s -> if (s > seconds[index]) index = i }
    return TopZone(index, (seconds[index] / total * 100).roundHalfUp())
}

/** How a zone's share moved against the span before, in points; null when there is nothing before. */
fun shareChange(seconds: List<Double>, previous: List<Double>, index: Int): Int? {
    val before = previous.sum()
    val now = seconds.sum()
    if (before <= 0 || now <= 0) return null
    val a = seconds.getOrElse(index) { 0.0 } / now * 100
    val b = previous.getOrElse(index) { 0.0 } / before * 100
    return (a - b).roundHalfUp()
}

/** Strava's heart rate zone colours, light to dark. */
val HR_ZONE_COLOURS = listOf(0xFFFCD0D1, 0xFFF3A4A3, 0xFFEE7675, 0xFFEB4748, 0xFFDE2625)

/** [count] colours of a scale, light to dark: all of them, or spread over the scale's range. */
fun zoneScale(scale: List<Long>, count: Int): List<Long> {
    if (count == scale.size) return scale
    return (0 until count).map { i ->
        scale[((i * (scale.size - 1)).toDouble() / (count - 1).coerceAtLeast(1)).roundHalfUp()]
    }
}

/** How wide each level's ring is on the radar, as the level bar's segments. */
private val SEGMENT_WIDTHS = listOf(3.0, 1.5, 1.0, 1.5, 1.4, 0.9, 0.3, 0.2, 0.2)

/** How far out a level sits on the radar, 0 (centre) to 1 (the ring). */
fun radialShare(level: Double): Double {
    val total = SEGMENT_WIDTHS.sum()
    val clamped = level.coerceIn(0.0, POWER_LEVELS.toDouble())
    val full = floor(clamped).toInt()
    val done = SEGMENT_WIDTHS.take(full).sum()
    return (done + (clamped - full) * SEGMENT_WIDTHS.getOrElse(full) { 0.0 }) / total
}

/** The strongest skill and, in it, the duration that is highest on the scale. */
data class Strongest(val skill: Skill, val duration: Int, val watts: Double, val level: Double)

fun strongest(bests: Map<Int, Double>, weightKg: Double): Strongest? {
    var out: Strongest? = null
    var top = 0.0
    for (skill in Skill.entries) {
        val levels = skill.durations.map { levelAt(bests[it] ?: 0.0, it, weightKg) }
        val level = skillLevel(levels)
        if (level > top) {
            top = level
            val duration = skill.durations[levels.indexOf(levels.max())]
            out = Strongest(skill, duration, bests[duration] ?: 0.0, level)
        }
    }
    return out
}

/** The skill a spoke of the radar belongs to. */
fun skillOfSpoke(index: Int): Skill {
    var n = index
    for (s in Skill.entries) {
        if (n < s.durations.size) return s
        n -= s.durations.size
    }
    return Skill.CLIMB
}

/** 15 s · 2 min · 1 h. */
fun durationLabel(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} h"
}

/** The spokes' durations, clockwise from the top. */
val RADAR_DURATIONS: List<Int> get() = SKILL_DURATIONS

/** Best efforts at mile distances, left out with metric units. */
private val MILE_EFFORTS = setOf("half_mile", "1mile", "2mile", "10mile")

/** The run distances; the rest are the ride's measures. */
val RUN_KEYS = listOf(
    "400m",
    "half_mile",
    "1k",
    "1mile",
    "2mile",
    "5k",
    "10k",
    "15k",
    "10mile",
    "half_marathon",
    "marathon"
)

fun effortShown(key: String, imperial: Boolean) = imperial || key !in MILE_EFFORTS

/** The card's medals: one per measure, up to three, the measures of the other units left out. */
fun <T> cardMedals(medals: List<T>, key: (T) -> String, imperial: Boolean): List<T> =
    medals.filter { effortShown(key(it), imperial) }.distinctBy(key).take(3)

/** A measure's value written as Strava does: 26:45 · 551 W · 102.2 km. */
fun bestValue(key: String, value: Double, locale: Locale, imperial: Boolean): String {
    if (key.startsWith("power_")) return "${value.roundToLong()} W"
    if (key == "longest") {
        val v = value / if (imperial) MILE else 1000.0
        return "${number(v, locale, 1, trim = true)} ${if (imperial) "mi" else "km"}"
    }
    return raceTime(value)
}

/** A month's totals and its moving time added up day by day (up to today in the month in course). */
data class MonthFigures(
    val month: YearMonth,
    val count: Int,
    val time: Double,
    val distance: Double,
    val ascent: Double,
    val cumulative: List<Double>
)

/** The calendar's range for this month and the one before: [from, to). */
fun monthPairRange(today: LocalDate): DayRange {
    val month = YearMonth.from(today)
    return DayRange(month.minusMonths(1).atDay(1), month.plusMonths(1).atDay(1))
}

/** One month from the calendar's days; [today] cuts the month in course. */
fun monthOf(calendar: List<CalendarDay>, month: YearMonth, today: LocalDate): MonthFigures {
    val byDay = calendar.associateBy { it.day.take(10) }
    val last = if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()
    var count = 0
    var time = 0.0
    var distance = 0.0
    var ascent = 0.0
    val cumulative = (1..last).map { d ->
        byDay[month.atDay(d).toString()]?.activities.orEmpty().forEach { a ->
            count++
            time += a.movingS
            distance += a.distanceM ?: 0.0
            ascent += a.ascentM ?: 0.0
        }
        time
    }
    return MonthFigures(month, count, time, distance, ascent, cumulative)
}

/** Days since a moment, as whole calendar days. */
fun daysAgo(day: LocalDate, today: LocalDate): Int = ChronoUnit.DAYS.between(day, today).toInt().coerceAtLeast(0)

/** How full a goal's ring is: 0 to 1. */
fun goalFraction(current: Double, target: Double): Float =
    if (target > 0) (current / target).coerceAtMost(1.0).toFloat() else 0f
