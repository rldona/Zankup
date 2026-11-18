package com.zankup.android.progress.more

import com.zankup.android.api.model.CalendarDay
import com.zankup.android.domain.POWER_LEVELS
import com.zankup.android.domain.Skill
import java.time.LocalDate
import kotlin.math.floor

// --- skills (packages/core progress/skillLevels.ts; the scale itself is core:domain PowerLevels) ---

/** Without a weight in the profile, the scale is read for a rider of this much. */
internal const val REFERENCE_KG = 75.0

/** The level bar's nine segments, narrower towards the top. */
internal val SEGMENT_WIDTHS = listOf(3f, 1.5f, 1f, 1.5f, 1.4f, 0.9f, 0.3f, 0.2f, 0.2f)

/** The whole level a fractional one names (1 to 9). */
internal fun levelName(level: Double): Int = floor(level).toInt().coerceIn(1, POWER_LEVELS)

/** How a level fills the nine segments: each full one, then the part of the one it is in. */
internal fun segments(level: Double): List<Float> = List(POWER_LEVELS) { i -> (level - i).toFloat().coerceIn(0f, 1f) }

/** How far out a level sits on the radar, 0 (centre) to 1 (the ring), the rings spaced as the segments. */
internal fun radialShare(level: Double): Float {
    val total = SEGMENT_WIDTHS.sum()
    val clamped = level.coerceIn(0.0, POWER_LEVELS.toDouble())
    val full = floor(clamped).toInt()
    val done = SEGMENT_WIDTHS.take(full).sum()
    return (done + (clamped - full).toFloat() * SEGMENT_WIDTHS.getOrElse(full) { 0f }) / total
}

/** The skill a spoke of the radar belongs to (its index among [SKILL_DURATIONS]). */
internal fun skillOf(index: Int): Skill {
    var n = index
    for (s in Skill.entries) {
        if (n < s.durations.size) return s
        n -= s.durations.size
    }
    return Skill.CLIMB
}

// --- monthly activities (packages/core progress/monthTotals.ts) ---

internal data class MonthFigures(
    val count: Int = 0,
    val time: Double = 0.0,
    val distance: Double = 0.0,
    val ascent: Double = 0.0
)

internal data class Month(
    val start: LocalDate,
    val days: Int,
    val totals: MonthFigures,
    /** Moving time added up to the end of each day, up to today for the month in course. */
    val cumulative: List<Double>
)

internal data class MonthPair(val current: Month, val previous: Month)

/** The calendar's range for this month and the one before: [from, to). */
internal fun monthPairRange(today: LocalDate): DaySpan =
    DaySpan(today.withDayOfMonth(1).minusMonths(1), today.withDayOfMonth(1).plusMonths(1))

/** One month from the calendar's days, all sports or only [sport]; [today] cuts the month in course. */
internal fun monthOf(calendar: List<CalendarDay>, start: LocalDate, today: LocalDate, sport: String? = null): Month {
    val days = start.lengthOfMonth()
    val byDay = calendar.associateBy { it.day.take(10) }
    var count = 0
    var time = 0.0
    var distance = 0.0
    var ascent = 0.0
    val cumulative = mutableListOf<Double>()
    val last = if (start.year == today.year && start.month == today.month) today.dayOfMonth else days
    for (i in 1..last) {
        val day = byDay[start.withDayOfMonth(i).toString()]
        for (a in day?.activities.orEmpty()) {
            if (sport != null && a.sport != sport) continue
            count += 1
            time += a.movingS
            distance += a.distanceM ?: 0.0
            ascent += a.ascentM ?: 0.0
        }
        cumulative += time
    }
    return Month(start, days, MonthFigures(count, time, distance, ascent), cumulative)
}

/** This month and the last, from the calendar of both. */
internal fun months(calendar: List<CalendarDay>, today: LocalDate, sport: String? = null): MonthPair {
    val first = today.withDayOfMonth(1)
    return MonthPair(monthOf(calendar, first, today, sport), monthOf(calendar, first.minusMonths(1), today, sport))
}

/** The sports done in either month, the most time first. */
internal fun monthSports(calendar: List<CalendarDay>): List<String> {
    val time = linkedMapOf<String, Double>()
    for (d in calendar) for (a in d.activities) time[a.sport] = (time[a.sport] ?: 0.0) + a.movingS
    return time.entries.sortedByDescending { it.value }.map { it.key }
}
