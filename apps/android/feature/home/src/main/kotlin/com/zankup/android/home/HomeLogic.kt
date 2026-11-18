package com.zankup.android.home

import com.zankup.android.api.model.ActivitySummary
import com.zankup.android.api.model.EstimateOut
import com.zankup.android.api.model.FitnessDay
import com.zankup.android.api.model.HealthDay
import com.zankup.android.api.model.Value
import com.zankup.android.api.model.Week
import com.zankup.android.domain.Sport
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

// What Home works out from the week, the feed's first page, the health days and the estimates: plain
// Kotlin, so it is tested without a phone.

/** The steps a day is measured against (the ring). */
const val STEPS_GOAL = 10_000.0

/** One of This week's seven circles, Monday to Sunday. */
data class WeekDay(
    val date: LocalDate,
    /** The day's first activity: its sport colours the circle and opens it. */
    val first: Sport?,
    val firstId: Long?,
    /** A second activity that day, drawn as a dot. */
    val second: Sport?,
    val isToday: Boolean,
    val isFuture: Boolean
)

/** Monday to Sunday of [today]'s week, each with the activities the API counted on it. */
fun weekDays(week: Week, today: LocalDate): List<WeekDay> {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val byDate = week.days.associateBy { it.date }
    return (0L until 7L).map { offset ->
        val date = monday.plusDays(offset)
        val activities = byDate[date.toString()]?.activities.orEmpty()
        val first = activities.getOrNull(0)
        WeekDay(
            date = date,
            first = first?.let { Sport.fromKey(it.sport) },
            firstId = first?.id?.toLong(),
            second = activities.getOrNull(1)?.let { Sport.fromKey(it.sport) },
            isToday = date == today,
            isFuture = date > today
        )
    }
}

/**
 * This week's time trained against a usual week, the previous weeks' average: [share] is 100 for an
 * ordinary week. Null when there is no history yet.
 */
data class UsualWeek(val share: Int, val averageS: Double) {
    /** How much of the bar is filled: it runs to [BAR_MAX] % of a usual week. */
    val fill: Float get() = (share / BAR_MAX).coerceIn(0f, 1f)

    /** Where the usual week is marked on the bar. */
    val mark: Float get() = 100f / BAR_MAX

    companion object {
        const val BAR_MAX = 150f
    }
}

fun usualWeek(week: Week): UsualWeek? {
    if (week.previous.isEmpty()) return null
    val average = week.previous.sumOf { it.movingS } / week.previous.size
    if (average <= 0) return null
    return UsualWeek((week.thisWeek.movingS / average * 100).roundToInt(), average)
}

/** The day an activity happened on, where it happened (its time zone), else in [fallback]. */
fun activityDay(activity: ActivitySummary, fallback: ZoneId): LocalDate? {
    val zone = activity.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: fallback
    val instant =
        runCatching { Instant.parse(activity.startUtc) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(activity.startUtc).toInstant(ZoneOffset.UTC) }.getOrNull()
            ?: return null
    return instant.atZone(zone).toLocalDate()
}

/** The most recent day with activities, and its activities, the latest first. */
data class LastDay(val day: LocalDate, val activities: List<ActivitySummary>)

fun lastDay(items: List<ActivitySummary>, zone: ZoneId): LastDay? {
    val dated = items.mapNotNull { a -> activityDay(a, zone)?.let { it to a } }
    val day = dated.maxOfOrNull { it.first } ?: return null
    val activities = dated.filter { it.first == day }.map { it.second }.sortedByDescending { it.startUtc }
    return LastDay(day, activities)
}

enum class DayHeading { TODAY, YESTERDAY, DATE }

fun dayHeading(day: LocalDate, today: LocalDate): DayHeading = when (day) {
    today -> DayHeading.TODAY
    today.minusDays(1) -> DayHeading.YESTERDAY
    else -> DayHeading.DATE
}

/** The last day's activities only when it is today or yesterday: an older day is not news. */
fun recentDay(last: LastDay?, today: LocalDate): LastDay? =
    last?.takeIf { dayHeading(it.day, today) != DayHeading.DATE }

private val HealthDay.hasFigures get() = steps != null || restingHr != null || hrv != null

/**
 * The health day shown under the last day: that day's when it is recent; otherwise today's once it has
 * something, else yesterday's. Null when the day has no steps, heart rate or HRV.
 */
fun dayHealth(recent: LastDay?, today: LocalDate, days: List<HealthDay>): HealthDay? {
    fun on(day: LocalDate) = days.firstOrNull { it.day == day.toString() }
    val day =
        if (recent != null) {
            on(recent.day)
        } else {
            on(today)?.takeIf { it.hasFigures } ?: on(today.minusDays(1))
        }
    return day?.takeIf { it.hasFigures }
}

/** One sport's totals over the last seven days. */
data class SportTotals(val sport: Sport, val count: Int, val distanceM: Double, val movingS: Double)

/** The last seven days, today included, by sport, the most time first. */
fun lastSevenDays(items: List<ActivitySummary>, today: LocalDate, zone: ZoneId): List<SportTotals> {
    val from = today.minusDays(6)
    return items
        .filter { a -> activityDay(a, zone)?.let { it in from..today } == true }
        .groupBy { Sport.fromKey(it.sport) }
        .map { (sport, list) ->
            SportTotals(
                sport = sport,
                count = list.size,
                distanceM = list.sumOf { it.distanceM ?: 0.0 },
                movingS = list.sumOf { it.movingS ?: it.durationS }
            )
        }.sortedByDescending { it.movingS }
}

/** The average of a health figure over the days that have it, or null. */
fun healthAverage(days: List<HealthDay>, figure: (HealthDay) -> Value?): Double? =
    days.mapNotNull { figure(it)?.value }.takeIf { it.isNotEmpty() }?.average()

/** How the form (TSB) is coloured and named. */
enum class FormTone { OVERREACHING, OPTIMAL, NEUTRAL, FRESH }

fun formTone(tsb: Double): FormTone = when {
    tsb > 5 -> FormTone.FRESH
    tsb > -10 -> FormTone.NEUTRAL
    tsb > -30 -> FormTone.OPTIMAL
    else -> FormTone.OVERREACHING
}

/**
 * A gauge's scale: from [min] to [max], in bands that each end at one of [ends] (the last one at
 * [max]), so a value's dot sits in the colour of its band.
 */
data class GaugeScale(val min: Double, val ends: List<Double>) {
    val max: Double get() = ends.last()

    /** Where [value] sits from 0 (the start of the arc) to 1 (its end). */
    fun fraction(value: Double): Float = ((value - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()

    /** Each band's start and end, as fractions of the arc. */
    val spans: List<ClosedFloatingPointRange<Float>>
        get() = ends.mapIndexed { i, end -> fraction(if (i == 0) min else ends[i - 1])..fraction(end) }

    /** The band [value] falls in: the first whose end it does not pass. */
    fun band(value: Double): Int = ends.indexOfFirst { value <= it }.takeIf { it >= 0 } ?: ends.lastIndex

    companion object {
        /** [count] equal bands (VO₂ max, FTP). */
        fun equal(min: Double, max: Double, count: Int) =
            GaugeScale(min, (1..count).map { min + (max - min) * it / count })
    }
}

/** The training load's gauge: freshness from -40 to +30, in [formTone]'s bands. */
object FormGauge {
    val tones = listOf(FormTone.OVERREACHING, FormTone.OPTIMAL, FormTone.NEUTRAL, FormTone.FRESH)
    val scale = GaugeScale(-40.0, listOf(-30.0, -10.0, 5.0, 30.0))
}

/** VO₂ max (VDOT) from 30 to 70 and FTP in W/kg from 1.5 to 5, in five bands. */
val Vo2Scale = GaugeScale.equal(30.0, 70.0, 5)
val FtpScale = GaugeScale.equal(1.5, 5.0, 5)

/** The weight FTP is drawn against when the profile has none. */
const val DEFAULT_WEIGHT_KG = 75.0

/** The metrics' tiles: each one only when the API has its figure, steps always. */
data class Metrics(
    val vdot: Double? = null,
    val ftp: Double? = null,
    val weightKg: Double? = null,
    val stepsToday: Double = 0.0,
    val form: FitnessDay? = null
) {
    val wattsPerKg: Double? get() = ftp?.let { f -> weightKg?.takeIf { it > 0 }?.let { f / it } }

    /** Where FTP sits on its gauge, against the profile's weight or a usual one. */
    val ftpGaugeValue: Double? get() = ftp?.let { it / (weightKg?.takeIf { w -> w > 0 } ?: DEFAULT_WEIGHT_KG) }

    val stepsShare: Float get() = (stepsToday / STEPS_GOAL).coerceIn(0.0, 1.0).toFloat()
}

fun metrics(
    estimates: List<EstimateOut>?,
    fitness: List<FitnessDay>?,
    weightKg: Double?,
    health: List<HealthDay>?,
    today: LocalDate
) = Metrics(
    vdot = estimates?.firstOrNull { it.kind == "vdot" }?.value?.takeIf { it > 0 },
    ftp = estimates?.firstOrNull { it.kind == "ftp" }?.value?.takeIf { it > 0 },
    weightKg = weightKg,
    stepsToday = health?.firstOrNull { it.day == today.toString() }?.steps?.value ?: 0.0,
    form = fitness?.maxByOrNull { it.day }
)
