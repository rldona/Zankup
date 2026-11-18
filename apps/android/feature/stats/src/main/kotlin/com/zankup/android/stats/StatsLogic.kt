package com.zankup.android.stats

import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Pure logic of the stats screens, ported from packages/core/src/stats/period.ts,
// packages/core/src/domain/compare.ts and apps/mobile/src/stats/dates.ts. Unit-tested.

/** The volume chart's bucket, as the API names it. */
enum class VolumeUnit { DAY, WEEK, MONTH, YEAR }

/** A span for the API: [from] included, [to] excluded, and the volume chart's bucket. */
data class Period(val from: LocalDate, val to: LocalDate, val unit: VolumeUnit)

/** The toolbar's choices: last 12 months, a year, a run of months, all time. */
enum class StatsMode { LAST12, YEAR, MONTHS, ALL }

/** A month counted from year 0 (year * 12 + month, January = 0), so one step is always one month. */
fun monthIndex(month: YearMonth): Int = month.year * 12 + month.monthValue - 1

fun monthOf(index: Int): YearMonth = YearMonth.of(Math.floorDiv(index, 12), Math.floorMod(index, 12) + 1)

/** Whole months, both ends included, in either order: one by day, up to three by week, more by month. */
fun monthsRange(first: Int, last: Int): Period {
    val a = min(first, last)
    val b = maxOf(first, last)
    val months = b - a + 1
    val unit = when {
        months == 1 -> VolumeUnit.DAY
        months <= 3 -> VolumeUnit.WEEK
        else -> VolumeUnit.MONTH
    }
    return Period(monthOf(a).atDay(1), monthOf(b).plusMonths(1).atDay(1), unit)
}

fun yearRange(year: Int): Period = Period(LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1), VolumeUnit.MONTH)

fun allTimeRange(today: LocalDate, firstYear: Int): Period =
    Period(LocalDate.of(firstYear, 1, 1), today.plusDays(1), VolumeUnit.YEAR)

fun last12Range(today: LocalDate): Period = Period(today.minusYears(1).plusDays(1), today.plusDays(1), VolumeUnit.MONTH)

/**
 * Where the toolbar stands: the mode, the year shown and the run of months (it opens on last month to
 * this one). Each arrow is allowed only inside the history: no year before [firstYear], none after
 * this one, and the two months never cross.
 */
data class StatsPeriod(
    val today: LocalDate,
    val firstYear: Int,
    val mode: StatsMode,
    val year: Int,
    val fromMonth: Int,
    val toMonth: Int
) {
    val now get() = monthIndex(YearMonth.from(today))

    val range: Period
        get() = when (mode) {
            StatsMode.LAST12 -> last12Range(today)
            StatsMode.YEAR -> yearRange(year)
            StatsMode.MONTHS -> monthsRange(fromMonth, toMonth)
            StatsMode.ALL -> allTimeRange(today, firstYear)
        }

    val canYearBack get() = year > firstYear
    val canYearForward get() = year < today.year
    val canFromBack get() = fromMonth > firstYear * 12
    val canFromForward get() = fromMonth < toMonth
    val canToBack get() = toMonth > fromMonth
    val canToForward get() = toMonth < now

    fun yearBy(step: Int) = copy(year = (year + step).coerceIn(firstYear, maxOf(firstYear, today.year)))

    fun fromBy(step: Int) = copy(fromMonth = (fromMonth + step).coerceIn(min(firstYear * 12, toMonth), toMonth))

    fun toBy(step: Int) = copy(toMonth = (toMonth + step).coerceIn(fromMonth, maxOf(fromMonth, now)))

    companion object {
        fun initial(today: LocalDate, firstYear: Int = today.year): StatsPeriod {
            val now = monthIndex(YearMonth.from(today))
            return StatsPeriod(today, firstYear, StatsMode.MONTHS, today.year, now - 1, now)
        }
    }
}

/** "sep 2026": a month short, without Spanish's "de" or the abbreviation's dot. */
fun monthLabel(index: Int, locale: Locale): String {
    val month = monthOf(index)
    val name = month.month.getDisplayName(TextStyle.SHORT, locale).replace(".", "")
    return "$name ${month.year}"
}

// --- the summary against a year before -------------------------------------------------------

/** The change in whole percent (null with nothing before) and each bar's share of the row. */
data class Comparison(val percent: Int?, val now: Float, val before: Float)

fun compare(now: Double, before: Double): Comparison {
    val top = maxOf(now, before)
    return Comparison(
        percent = if (before != 0.0) (((now - before) / before) * 100).roundToInt() else null,
        now = if (top > 0) (now / top).toFloat() else 0f,
        before = if (top > 0) (before / top).toFloat() else 0f
    )
}

/** What the summary's key calls the two spans it compares. */
sealed interface Compared {
    /** Longer than a year (All): nothing worth comparing. */
    data object None : Compared

    /** Any other span: "12 months before" and "Last 12 months". */
    data object Rolling : Compared

    /** A year, or whole months: last year's name first. */
    data class Named(val before: String, val now: String) : Compared
}

fun comparisonLabels(from: LocalDate, to: LocalDate, locale: Locale): Compared {
    if (ChronoUnit.DAYS.between(from, to) > 366) return Compared.None
    if (from.dayOfMonth != 1 || to.dayOfMonth != 1) return Compared.Rolling
    val last = to.minusDays(1)
    if (from.monthValue == 1 && to.monthValue == 1 && to.year == from.year + 1) {
        return Compared.Named((from.year - 1).toString(), from.year.toString())
    }
    fun short(d: LocalDate, year: Boolean) = d.month.getDisplayName(TextStyle.SHORT, locale).replace(".", "") +
        if (year) " ${d.year}" else ""
    fun name(start: LocalDate, end: LocalDate) = when {
        YearMonth.from(start) == YearMonth.from(end) -> short(start, true)
        start.year == end.year -> "${short(start, false)}–${short(end, true)}"
        else -> "${short(start, true)}–${short(end, true)}"
    }
    return Compared.Named(name(from.minusYears(1), last.minusYears(1)), name(from, last))
}

// --- best-effort curves and efficiency -------------------------------------------------------

/** "30 s", "5 min", "1 h". */
fun durationLabel(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${formatShort(seconds / 60.0)} min"
    else -> "${formatShort(seconds / 3600.0)} h"
}

private fun formatShort(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

/** Every duration of either curve, in order. */
fun curveDurations(vararg curves: Map<Int, Double>): List<Int> = curves.flatMap { it.keys }.distinct().sorted()

/**
 * The value axis, scaled to this period: a few all-time spikes (GPS, sprints) would flatten the rest.
 */
fun curveDomain(all: Collection<Double>, period: Collection<Double>): ClosedFloatingPointRange<Double> {
    val values = all + period
    if (values.isEmpty()) return 0.0..1.0
    val top = (period.ifEmpty { values }).max()
    return values.min() * 0.95..top * 1.15
}

/** The change from the first third of the points to the last, in percent; null with fewer than three. */
fun efficiencyChange(values: List<Double>): Double? {
    if (values.size < 3) return null
    val third = ceil(values.size / 3.0).toInt()
    val first = values.take(third).average()
    if (first == 0.0) return null
    return (values.takeLast(third).average() - first) / first * 100
}

// --- records and estimates -------------------------------------------------------------------

/** Mile distances mean nothing to someone who thinks in km: left out of the lists with metric units. */
val MILE_EFFORTS = setOf("half_mile", "1mile", "2mile", "10mile")

fun effortShown(key: String, imperial: Boolean) = imperial || key !in MILE_EFFORTS

val ESTIMATE_ORDER = listOf("ftp", "critical_speed", "vdot", "lthr", "hr_max")
val RACES = listOf("race_5k", "race_10k", "race_half_marathon", "race_marathon")

/** "riegel:10" → ("riegel", "10"). */
fun methodParts(method: String): Pair<String, String> {
    val parts = method.split(":")
    return parts[0] to parts.getOrElse(1) { "" }
}

// --- the calendar ----------------------------------------------------------------------------

/** The Monday-to-Sunday weeks that cover a month: the first day and the day after the last. */
fun monthGrid(month: YearMonth): Pair<LocalDate, LocalDate> {
    val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val after = month.plusMonths(1).atDay(1)
    val end = if (after.dayOfWeek == DayOfWeek.MONDAY) {
        after
    } else {
        after.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    }
    return start to end
}

/** Days in rows of seven. */
fun <T> weeksOf(days: List<T>): List<List<T>> = days.chunked(7)

/**
 * A tap on day [index]: a second day after a first makes a range; any other tap starts again from it.
 */
fun pick(current: IntRange?, index: Int): IntRange =
    if (current != null && current.first == current.last && current.first != index) {
        min(current.first, index)..maxOf(current.first, index)
    } else {
        index..index
    }

/** What a span of days adds up to: activities, metres and moving seconds. */
data class SpanTotal(val count: Int, val distanceM: Double, val movingS: Double)

fun <T> spanTotal(acts: List<T>, distance: (T) -> Double?, moving: (T) -> Double): SpanTotal =
    SpanTotal(acts.size, acts.sumOf { distance(it) ?: 0.0 }, acts.sumOf(moving))

/** A bubble's diameter in dp: big enough for the icon to read, bigger with the effort. */
fun bubbleSize(load: Double?, heaviest: Double): Float = 18f + 10f * sqrt((load ?: 10.0) / heaviest).toFloat()

/** The weekday letters from Monday. */
fun weekdayLetters(locale: Locale): List<String> = DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, locale) }

// --- the reviews -----------------------------------------------------------------------------

enum class ReviewPeriod { MONTH, YEAR }

/** The month ("2026-11") or year ("2026") [by] periods away. */
fun shiftPeriod(period: ReviewPeriod, value: String, by: Int): String {
    if (period == ReviewPeriod.YEAR) return (value.toInt() + by).toString()
    return YearMonth.parse(value).plusMonths(by.toLong()).toString()
}

/** The value a switch from month to year (or back) keeps: the year of the month, or its December. */
fun switchPeriod(to: ReviewPeriod, value: String): String = when (to) {
    ReviewPeriod.YEAR -> value.take(4)
    ReviewPeriod.MONTH -> if (value.length == 4) "$value-12" else value
}

fun currentPeriodValue(period: ReviewPeriod, today: LocalDate): String = when (period) {
    ReviewPeriod.YEAR -> today.year.toString()
    ReviewPeriod.MONTH -> YearMonth.from(today).toString()
}

/** "November 2026" or "2026". */
fun periodTitle(period: ReviewPeriod, value: String, locale: Locale): String {
    if (period == ReviewPeriod.YEAR) return value
    val month = YearMonth.parse(value)
    return DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month).replaceFirstChar { it.titlecase(locale) }
}

/** The change from [before] to [now] as "+12 %", or null when there was nothing before. */
fun change(now: Double, before: Double): String? {
    if (before == 0.0) return null
    val pct = ((now - before) / before * 100).roundToInt()
    return "${if (pct > 0) "+" else ""}$pct %"
}

// --- year by year ----------------------------------------------------------------------------

/** The hue of a colour, in degrees. */
fun hue(c: Color): Float {
    val r = c.red
    val g = c.green
    val b = c.blue
    val max = maxOf(r, g, b)
    val d = max - minOf(r, g, b)
    if (d == 0f) return 0f
    val h = when (max) {
        r -> ((g - b) / d) % 6
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    }
    return (h * 60 + 360) % 360
}

private fun apart(a: Color, b: Color): Boolean {
    val d = abs(hue(a) - hue(b))
    return min(d, 360 - d) >= 40
}

/**
 * One colour per year: the selected year in the accent, the others in the sports' colours, newest
 * first, leaving out any hue close to one already given.
 */
fun yearColours(years: List<Int>, selected: Int, accent: Color, sports: List<Color>, fallback: Color): Map<Int, Color> {
    val picked = mutableListOf(accent)
    sports.forEach { colour -> if (picked.all { apart(it, colour) }) picked += colour }
    val rest = picked.drop(1)
    val out = linkedMapOf(selected to accent)
    years.filter { it != selected }.sortedDescending().forEachIndexed { i, year ->
        out[year] = if (rest.isEmpty()) fallback else rest[i % rest.size]
    }
    return out
}

/** The lines in drawing order: the selected one last, on top. */
fun <T> selectedLast(lines: List<T>, year: (T) -> Int, selected: Int): List<T> =
    lines.filter { year(it) != selected } + lines.filter { year(it) == selected }

/** A year line's total so far, in [scale] units (km or mi). */
fun lineTotal(cumulative: List<Double>, scale: Double): Double = (cumulative.lastOrNull() ?: 0.0) / scale

const val METRES_PER_MILE = 1609.344
