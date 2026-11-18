package com.zankup.android.progress.more

import com.zankup.android.api.model.BestOut
import com.zankup.android.domain.Units
import com.zankup.android.domain.formatNumber
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Best times as Strava's (packages/core progress/bests.ts): places, medals, the chart's scale and trend. */

internal val RUN_KEYS =
    listOf("400m", "half_mile", "1k", "1mile", "2mile", "5k", "10k", "15k", "10mile", "half_marathon", "marathon")

internal val RIDE_KEYS =
    listOf(
        "longest",
        "power_5",
        "power_15",
        "power_30",
        "power_60",
        "power_300",
        "power_600",
        "power_1200",
        "power_3600"
    )

/** Distances in miles, left out with metric units. */
private val MILE_EFFORTS = setOf("half_mile", "1mile", "2mile", "10mile")

/** Whether a measure is shown with these units: the mile distances only in miles. */
internal fun effortShown(key: String, units: Units): Boolean = units == Units.IMPERIAL || key !in MILE_EFFORTS

/** Whether the measure is a run's (else a ride's). */
internal fun isRunKey(key: String) = key in RUN_KEYS

internal fun lowerIsBetter(key: String) = !key.startsWith("power_") && key != "longest"

/** Best first; equal ones by date, the earlier first. */
internal fun ranked(key: String, bests: List<BestOut>): List<BestOut> {
    val lower = lowerIsBetter(key)
    return bests.sortedWith { a, b ->
        when {
            a.value != b.value -> if (lower) a.value.compareTo(b.value) else b.value.compareTo(a.value)
            else -> a.day.compareTo(b.day)
        }
    }
}

/** The place of a best among others: one more than those strictly better. */
internal fun placeOf(key: String, bests: List<BestOut>, best: BestOut): Int {
    val lower = lowerIsBetter(key)
    return 1 + bests.count { if (lower) it.value < best.value else it.value > best.value }
}

/** A best's medal in its year (1 to 3); null otherwise. */
internal fun yearMedal(key: String, bests: List<BestOut>, best: BestOut): Int? {
    val year = best.day.take(4)
    val place = placeOf(key, bests.filter { it.day.startsWith(year) }, best)
    return place.takeIf { it <= 3 }
}

/** The years with bests, newest first. */
internal fun yearsOf(bests: List<BestOut>): List<String> = bests.map { it.day.take(4) }.distinct().sortedDescending()

/** A measure's value as Strava writes it: 26:45 · 551 W · 102,2 km. */
internal fun bestValue(key: String, value: Double, locale: Locale, imperial: Boolean): String {
    if (key.startsWith("power_")) return "${value.roundToLong()} W"
    if (key == "longest") {
        val v = value / (if (imperial) METRES_PER_MILE else 1000.0)
        return "${formatNumber(v, locale, 0, 1)} ${if (imperial) "mi" else "km"}"
    }
    val s = value.roundToLong()
    val h = s / 3600
    val m = s % 3600 / 60
    fun pad(n: Long) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${pad(m)}:${pad(s % 60)}" else "$m:${pad(s % 60)}"
}

/** The distance in metres of a run key, for its pace. */
internal val RUN_METRES =
    mapOf(
        "400m" to 400.0,
        "half_mile" to 804.672,
        "1k" to 1000.0,
        "1mile" to 1609.344,
        "2mile" to 3218.688,
        "5k" to 5000.0,
        "10k" to 10000.0,
        "15k" to 15000.0,
        "10mile" to 16093.44,
        "half_marathon" to 21097.5,
        "marathon" to 42195.0
    )

/** 4:59 per kilometre (or mile). */
internal fun racePace(seconds: Double, metres: Double, imperial: Boolean): String {
    val s = (seconds / (metres / (if (imperial) METRES_PER_MILE else 1000.0))).roundToLong()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** Twelve bests on the chart at a time. */
internal const val CHART_SLOTS = 12

/** The chart's offset (its left slot) range; with fewer bests than slots they sit on the right. */
internal fun offsetRange(count: Int, slots: Int = CHART_SLOTS): ClosedFloatingPointRange<Float> {
    val low = min(0, count - slots)
    return low.toFloat()..max(low, count - slots).toFloat()
}

/** The best at the right edge for an offset: the one the list shows at its top. */
internal fun rightIndex(offset: Float, count: Int, slots: Int = CHART_SLOTS): Int =
    min(count - 1, max(0, Math.round(offset) + slots - 1))

/** The vertical scale: from the record (top) to the 90th percentile of the rest (bottom). */
internal fun chartScale(key: String, bests: List<BestOut>): Pair<Double, Double> {
    val order = ranked(key, bests)
    val record = order.firstOrNull()?.value ?: 0.0
    val worst = order.getOrNull(floor(0.9 * (order.size - 1)).toInt())?.value ?: record
    return record to worst
}

/** The trend: at each best, the average of those within [days] either side. */
internal fun trend(points: List<BestOut>, days: Long = 45): List<Double> {
    val at = points.map { LocalDate.parse(it.day.take(10)) }
    return points.indices.map { i ->
        val near = points.indices.filter { abs(ChronoUnit.DAYS.between(at[it], at[i])) <= days }
        near.sumOf { points[it].value } / max(1, near.size)
    }
}

/** 15 s · 2 min · 1 h. */
internal fun durationLabel(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} h"
}
