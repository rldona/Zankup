package com.zankup.android.progress.more

import com.zankup.android.api.model.Totals
import com.zankup.android.domain.formatNumber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Goals as Strava's (packages/core progress/goals.ts): rings, what is left, even pace, charted periods. */

internal const val METRES_PER_MILE = 1609.344
internal const val FEET_PER_METRE = 3.28084

/** The parts of a goal the maths need, as /api/goals sends them. */
internal data class GoalFigures(
    val period: String,
    val metric: String,
    val target: Double,
    val current: Double,
    val start: LocalDate,
    val end: LocalDate
)

/** How full the ring is: 0 to 1. */
internal fun fraction(target: Double, current: Double): Float =
    if (target > 0) min(1.0, current / target).toFloat().coerceAtLeast(0f) else 0f

/** What is left to do (positive) or done over the target (negative). */
internal fun left(g: GoalFigures): Double = g.target - g.current

/**
 * Against an even pace through the period, as of [today]: positive when behind (what an even pace
 * would have done by now and has not), negative when ahead.
 */
internal fun behind(g: GoalFigures, today: LocalDate): Double {
    val length = ChronoUnit.DAYS.between(g.start, g.end).toDouble()
    if (length <= 0) return 0.0
    val elapsed = min(length, ChronoUnit.DAYS.between(g.start, today) + 1.0)
    return g.target * elapsed / length - g.current
}

internal fun mondayOf(day: LocalDate): LocalDate = day.with(DayOfWeek.MONDAY)

/** The span a goal is charted over (to excluded): the last 12 weeks or months, or the year's months. */
internal data class ChartSpan(val from: LocalDate, val to: LocalDate, val unit: String)

internal fun chartPeriods(period: String, today: LocalDate): ChartSpan {
    val tomorrow = today.plusDays(1)
    return when (period) {
        "week" -> ChartSpan(mondayOf(today).minusDays(77), tomorrow, "week")
        "month" -> ChartSpan(today.withDayOfMonth(1).minusMonths(11), tomorrow, "month")
        else -> ChartSpan(today.withDayOfYear(1), tomorrow, "month")
    }
}

/** Running totals, for a year's line against its target's diagonal. */
internal fun runningTotal(values: List<Double>): List<Double> {
    var sum = 0.0
    return values.map {
        sum += it
        sum
    }
}

/** A bucket's figure for a goal metric (count, time, distance, ascent). */
internal fun metricOf(t: Totals?, metric: String): Double = when {
    t == null -> 0.0
    metric == "count" -> (t.count ?: 0).toDouble()
    metric == "time" -> t.movingS ?: 0.0
    metric == "distance" -> t.distanceM ?: 0.0
    else -> t.ascentM ?: 0.0
}

/** The two fields a target is typed in: hours and minutes for time, one number for the rest. */
internal data class TargetInput(val a: String, val b: String = "")

private fun plain(v: Double): String = if (v == floor(v)) v.toLong().toString() else v.toString()

/** A target in the units it is typed in: km (or mi), hours + minutes, metres (or feet), activities. */
internal fun toInput(metric: String, target: Double, imperial: Boolean): TargetInput = when (metric) {
    "time" -> TargetInput(floor(target / 3600).toLong().toString(), ((target % 3600) / 60).roundToLong().toString())
    "distance" -> TargetInput(plain((target / (if (imperial) METRES_PER_MILE else 1000.0) * 10).roundToLong() / 10.0))
    "ascent" -> TargetInput((if (imperial) target * FEET_PER_METRE else target).roundToLong().toString())
    else -> TargetInput(target.roundToLong().toString())
}

/** The typed target back in the API's units (metres, seconds, activities); 0 when it is not a number. */
internal fun fromInput(metric: String, a: String, b: String, imperial: Boolean): Double {
    fun n(s: String) = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
    return when (metric) {
        "time" -> n(a) * 3600 + n(b) * 60
        "distance" -> n(a) * (if (imperial) METRES_PER_MILE else 1000.0)
        "ascent" -> if (imperial) n(a) / FEET_PER_METRE else n(a)
        else -> n(a).roundToInt().toDouble()
    }
}

/** A long time as Progress writes it: 4 h 17 min, 45 min, 6 h. */
internal fun hoursMinutes(seconds: Double): String {
    // As the rest of the app writes a long time (1 h 41 min, 30 min); a whole number of hours as "6 h".
    return com.zankup.android.domain.formatHoursMinutes(seconds).removeSuffix(" 0 min")
}

/** A total in the person's units: 4h 17min · 28,97 km · 1421 m · 22 (packages/core volumeFormat). */
internal fun volumeValue(metric: String, v: Double, imperial: Boolean, locale: Locale, digits: Int = 2): String =
    when (metric) {
        "time" -> hoursMinutes(v)
        "distance" -> {
            val d = if (v != 0.0) digits else 0
            "${formatNumber(v / (if (imperial) METRES_PER_MILE else 1000.0), locale, d, d)} ${
                if (imperial) "mi" else "km"
            }"
        }
        "ascent" -> "${formatNumber(if (imperial) v * FEET_PER_METRE else v, locale)} ${if (imperial) "ft" else "m"}"
        else -> formatNumber(v, locale)
    }
