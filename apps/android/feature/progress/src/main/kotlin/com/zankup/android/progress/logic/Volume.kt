package com.zankup.android.progress.logic

import com.zankup.android.api.model.Bucket
import com.zankup.android.api.model.Totals
import com.zankup.android.domain.Sport
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** The ranges of the Volume detail, 3M by default. */
enum class ProgressRange(val key: String) {
    D7("7d"),
    M1("1m"),
    M3("3m"),
    M6("6m"),
    YTD("ytd"),
    Y1("1y")
}

/** What Volume measures. */
enum class VolumeMetric {
    TIME,
    DISTANCE,
    ASCENT,
    COUNT
}

/** Days from [from] to [to] (excluded), as the API takes them. */
data class DayRange(val from: LocalDate, val to: LocalDate)

/** A range, the same span just before it, and whether its buckets are days or weeks. */
data class VolumeSpan(val current: DayRange, val previous: DayRange, val byDay: Boolean)

/** What a range covers (to excluded), in days or weeks, and the same span just before it. */
fun volumeRange(range: ProgressRange, today: LocalDate): VolumeSpan {
    val tomorrow = today.plusDays(1)
    return when (range) {
        ProgressRange.D7, ProgressRange.M1 -> {
            val from = today.minusDays(if (range == ProgressRange.D7) 6 else 30)
            val days = if (range == ProgressRange.D7) 7L else 31L
            VolumeSpan(DayRange(from, tomorrow), DayRange(from.minusDays(days), from), byDay = true)
        }
        ProgressRange.YTD -> {
            val lastYear = runCatching { today.minusYears(1) }.getOrDefault(today)
            VolumeSpan(
                DayRange(today.withDayOfYear(1), tomorrow),
                DayRange(lastYear.withDayOfYear(1), lastYear.plusDays(1)),
                byDay = false
            )
        }
        else -> {
            val weeks = when (range) {
                ProgressRange.M3 -> 12L
                ProgressRange.M6 -> 24L
                else -> 52L
            }
            val from = mondayOf(today).minusWeeks(weeks - 1)
            VolumeSpan(DayRange(from, tomorrow), DayRange(from.minusWeeks(weeks), from), byDay = false)
        }
    }
}

fun metricOf(totals: Totals?, metric: VolumeMetric): Double {
    totals ?: return 0.0
    return when (metric) {
        VolumeMetric.COUNT -> (totals.count ?: 0).toDouble()
        VolumeMetric.TIME -> totals.movingS ?: 0.0
        VolumeMetric.DISTANCE -> totals.distanceM ?: 0.0
        VolumeMetric.ASCENT -> totals.ascentM ?: 0.0
    }
}

/** A bucket's value for every sport, or only for the activities whose group is [sport]. */
fun bucketValue(bucket: Bucket, metric: VolumeMetric, sport: Sport?): Double {
    sport ?: return metricOf(bucket.totals, metric)
    return bucket.bySport.entries.filter { Sport.fromKey(it.key) == sport }.sumOf { metricOf(it.value, metric) }
}

/** The sports found in some buckets, in the domain's order. */
fun sportsIn(buckets: List<Bucket>): List<Sport> {
    val found = buckets.flatMap { b -> b.bySport.keys.map(Sport::fromKey) }.toSet()
    return Sport.entries.filter { it in found }
}

/** Rounded change from the previous range, or null when there was nothing to compare with. */
fun percentChange(current: Double, previous: Double): Int? {
    if (previous <= 0) return null
    return ((current - previous) / previous * 100).roundHalfUp()
}

/** JavaScript's Math.round: halves go up, also below zero. */
internal fun Double.roundHalfUp(): Int = floor(this + 0.5).toInt()

/** "4 h 17 min", "30 min" or "6 h": totals and the time axis. */
fun hoursMinutes(seconds: Double): String {
    // As the rest of the app writes a long time (1 h 41 min, 30 min); a whole number of hours as "6 h".
    return com.zankup.android.domain.formatHoursMinutes(seconds).removeSuffix(" 0 min")
}

private val TIME_STEPS = listOf(15, 30, 60, 90, 120, 180, 240, 300, 360, 480, 600, 720, 900, 1200, 1800, 2400)
private val ROUND_STEPS = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 7.5, 10.0)

/**
 * Ticks for the axis, from zero: time in round minutes, distance in whole kilometres (or miles:
 * [unitSize] 1.609344), elevation in metres (or feet) and activities in whole numbers.
 */
fun axisTicks(max: Double, metric: VolumeMetric, intervals: Int, unitSize: Double = 1.0): List<Double> {
    if (metric == VolumeMetric.TIME) {
        val minutes = max(30.0, max / 60)
        val step = TIME_STEPS.firstOrNull { it * intervals >= minutes }?.toDouble()
            ?: (ceil(minutes / intervals / 600) * 600)
        return (0..intervals).map { it * step * 60 }
    }
    val unit = when (metric) {
        VolumeMetric.DISTANCE -> 1000 * unitSize
        VolumeMetric.COUNT -> 1.0
        else -> unitSize
    }
    val top = max(1.0, max / unit)
    val raw = top / intervals
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = ROUND_STEPS.map { it * magnitude }
        .firstOrNull { it * intervals >= top && (metric != VolumeMetric.COUNT || it == floor(it)) }
        ?: ceil(raw)
    return (0..intervals).map { (it * step * unit * 1000).roundToLong() / 1000.0 }
}

/**
 * What a sport is measured by, the first one charted: distance for those that cover ground, time for
 * the gym. Without a sport, time first. Activities only count in the detail screen.
 */
fun metricsFor(sport: Sport?): List<VolumeMetric> = when (sport) {
    null -> listOf(VolumeMetric.TIME, VolumeMetric.DISTANCE, VolumeMetric.ASCENT)
    Sport.STRENGTH -> listOf(VolumeMetric.TIME)
    Sport.RUN, Sport.WALK -> listOf(VolumeMetric.DISTANCE, VolumeMetric.TIME, VolumeMetric.ASCENT)
    Sport.RIDE -> listOf(VolumeMetric.DISTANCE, VolumeMetric.ASCENT, VolumeMetric.TIME)
    Sport.SWIM, Sport.ROW -> listOf(VolumeMetric.DISTANCE, VolumeMetric.TIME)
    Sport.OTHER -> listOf(VolumeMetric.TIME, VolumeMetric.DISTANCE)
}

/** The same span [years] years back, in whole weeks (52 each), so its days fall on the same weekdays. */
fun yearsBack(period: DayRange, years: Int): DayRange =
    DayRange(period.from.minusDays(364L * years), period.to.minusDays(364L * years))

/** How many years back can be compared: those that reach the first activity. */
fun comparableYears(period: DayRange, firstActivity: LocalDate?, most: Int = 15): List<Int> {
    firstActivity ?: return emptyList()
    return (1..most).takeWhile { yearsBack(period, it).to > firstActivity }
}

/** Totals, axis labels and ticks written for the person's units and language. */
class VolumeFormat(val imperial: Boolean, val locale: Locale) {
    private val distanceUnit = if (imperial) MILE else 1000.0
    private val distanceName = if (imperial) "mi" else "km"
    private val heightName = if (imperial) "ft" else "m"

    /** A total: 4h 17min · 28.97 km · 1,421 m · 22. */
    fun value(metric: VolumeMetric, v: Double, digits: Int = 2): String = when (metric) {
        VolumeMetric.TIME -> hoursMinutes(v)
        VolumeMetric.DISTANCE -> "${number(v / distanceUnit, locale, if (v != 0.0) digits else 0)} $distanceName"
        VolumeMetric.ASCENT -> "${number(if (imperial) v * FEET_PER_METRE else v, locale)} $heightName"
        VolumeMetric.COUNT -> number(v, locale)
    }

    /** An axis label: 4h 0min · 30 km · 2,500 m · 5. */
    fun tick(metric: VolumeMetric, v: Double): String = when (metric) {
        VolumeMetric.TIME -> hoursMinutes(v)
        VolumeMetric.DISTANCE -> {
            val d = v / distanceUnit
            "${number(d, locale, if (d == floor(d)) 0 else 1)} $distanceName"
        }
        VolumeMetric.ASCENT -> "${number(v, locale)} $heightName"
        VolumeMetric.COUNT -> number(v, locale)
    }

    /** Ticks in the person's units (miles and feet round as miles and feet). */
    fun ticks(max: Double, metric: VolumeMetric, intervals: Int): List<Double> {
        if (metric == VolumeMetric.ASCENT && imperial) {
            return axisTicks(max * FEET_PER_METRE, metric, intervals).map { it / FEET_PER_METRE }
        }
        val unitSize = if (metric == VolumeMetric.DISTANCE && imperial) MILE / 1000 else 1.0
        return axisTicks(max, metric, intervals, unitSize)
    }
}

/** A word under a chart, at its point's index. */
data class ChartLabel(val index: Int, val text: String)

private fun shortMonth(day: LocalDate, locale: Locale) =
    DateTimeFormatter.ofPattern("MMM", locale).format(day).replace(".", "").uppercase(locale)

/** "7 Sep - 13 Sep 2026": a week as the headers write it. */
fun weekTitle(monday: LocalDate, locale: Locale): String {
    val day = DateTimeFormatter.ofPattern("d MMM", locale)
    val year = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    return "${day.format(monday)} - ${year.format(monday.plusDays(6))}"
}

/** "9 - 15 NOV" or, across months, "26 OCT - 1 NOV". */
fun weekRange(monday: LocalDate, locale: Locale): String {
    val sunday = monday.plusDays(6)
    val a = shortMonth(monday, locale)
    val b = shortMonth(sunday, locale)
    return if (a == b) {
        "${monday.dayOfMonth} - ${sunday.dayOfMonth} $b"
    } else {
        "${monday.dayOfMonth} $a - ${sunday.dayOfMonth} $b"
    }
}

/** Months under a weekly chart: one where a month starts, as SEP · OCT · NOV. */
fun monthLabels(starts: List<LocalDate>, locale: Locale): List<ChartLabel> = starts.mapIndexedNotNull { i, s ->
    if (i > 0 && s.month != starts[i - 1].month) ChartLabel(i, shortMonth(s, locale)) else null
}

/** A day's initial: M, T, W… */
fun weekdayLetter(day: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern("EEEEE", locale).format(day).uppercase(locale)

/**
 * The words under a range's chart: each day's initial over 7 days, each Monday (as 9 NOV) over a
 * month, and the months over longer ranges.
 */
fun rangeLabels(range: ProgressRange, starts: List<LocalDate>, locale: Locale): List<ChartLabel> = when (range) {
    ProgressRange.D7 -> starts.mapIndexed { i, s -> ChartLabel(i, weekdayLetter(s, locale)) }
    ProgressRange.M1 -> starts.mapIndexedNotNull { i, s ->
        if (s.dayOfWeek == DayOfWeek.MONDAY) {
            ChartLabel(i, "${s.dayOfMonth} ${shortMonth(s, locale)}")
        } else {
            null
        }
    }
    else -> monthLabels(starts, locale)
}

/** The sentence on how a range went, before it is put in words. */
sealed interface Glance {
    data object None : Glance

    data object New : Glance

    data object Same : Glance

    data class Up(val pct: Int) : Glance

    data class Down(val pct: Int) : Glance

    data class CompareUp(val pct: Int) : Glance

    data class CompareDown(val pct: Int) : Glance
}

/** "Your progress at a glance": a range's total against the span before, or [compare] years back. */
fun glance(compare: Int?, total: Double, previousTotal: Double): Glance {
    val pct = percentChange(total, previousTotal)
    if (compare != null) {
        pct ?: return Glance.New
        return if (pct >= 0) Glance.CompareUp(pct) else Glance.CompareDown(-pct)
    }
    return when {
        total == 0.0 -> Glance.None
        pct == null -> Glance.New
        pct == 0 -> Glance.Same
        pct > 0 -> Glance.Up(pct)
        else -> Glance.Down(-pct)
    }
}

/** A value's place on a chart from zero to [max], 0 at the bottom and 1 at the top. */
internal fun share(value: Double, max: Double): Float = (value.coerceAtMost(max) / max.coerceAtLeast(1e-9)).toFloat()

internal fun Int.percentOf(total: Int) = if (total == 0) 0 else (this * 100.0 / total).roundToInt()
