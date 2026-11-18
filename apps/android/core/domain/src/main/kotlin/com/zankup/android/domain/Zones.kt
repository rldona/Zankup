package com.zankup.android.domain

import kotlin.math.roundToInt

/**
 * The lower bounds of the five heart rate zones as shares of the reference heart rate: 50, 60, 70, 80
 * and 90 % of max HR, the model the app uses when the person set nothing else.
 */
val HR_ZONE_SHARES: List<Double> = listOf(0.5, 0.6, 0.7, 0.8, 0.9)

/**
 * A sample that comes longer than this after the one before (a paused watch) counts nothing, so a
 * coffee stop does not add minutes to a zone.
 */
const val MAX_SAMPLE_GAP_S: Double = 30.0

/** Slower than this (m/s) the runner is standing, and the time is in no pace zone. */
private const val STANDING_SPEED = 0.5

/**
 * The zones' lower bounds in bpm, from a [reference] heart rate (max HR, or the threshold with the
 * threshold model's shares) and the [shares] of it each zone starts at: 190 bpm gives 95, 114, 133, 152
 * and 171.
 */
fun zoneLimits(reference: Double, shares: List<Double> = HR_ZONE_SHARES): List<Int> =
    shares.map { (it * reference).roundToInt() }

/**
 * Seconds spent in each heart rate zone, from the series' [times] (s) and [heartRate] (bpm, null where
 * the strap lost contact) and the zones' [limits] from [zoneLimits]. Each sample counts until the next
 * one; time below the first zone is left out.
 */
fun timeInZones(times: List<Double>, heartRate: List<Double?>, limits: List<Int>): List<Double> =
    secondsPerZone(times, heartRate, limits.map { it.toDouble() }) { true }

/**
 * Seconds spent in each pace zone, from the speed series ([speeds], m/s) and the zones' lower bounds
 * as speeds ([bounds], m/s, the first one 0) the server sends. Standing still counts nothing.
 */
fun paceZoneSeconds(
    speeds: List<Double?>,
    times: List<Double>,
    bounds: List<Double>,
    maxGap: Double = MAX_SAMPLE_GAP_S
): List<Double> = secondsPerZone(times, speeds, bounds, maxGap) { it >= STANDING_SPEED }

/**
 * The pace zone (0 is Z1) a speed in m/s falls in: the highest whose lower bound it reaches. Null for
 * no speed or no zones, so a split without time gets no colour.
 */
fun paceZoneOf(speed: Double, bounds: List<Double>): Int? {
    if (!(speed > 0) || bounds.isEmpty()) return null
    return bounds.indexOfLast { speed >= it }.coerceAtLeast(0)
}

/**
 * The time from each sample to the next added to the highest zone whose lower bound the sample
 * reaches. Missing samples, samples [counts] rejects and gaps over [maxGap] add nothing.
 */
private inline fun secondsPerZone(
    times: List<Double>,
    values: List<Double?>,
    lowerBounds: List<Double>,
    maxGap: Double = MAX_SAMPLE_GAP_S,
    counts: (Double) -> Boolean
): List<Double> {
    val seconds = DoubleArray(lowerBounds.size)
    for (i in 0 until minOf(times.size - 1, values.size)) {
        val value = values[i] ?: continue
        val dt = times[i + 1] - times[i]
        if (dt <= 0 || dt > maxGap || !counts(value)) continue
        val zone = lowerBounds.indexOfLast { value >= it }
        if (zone >= 0) seconds[zone] += dt
    }
    return seconds.toList()
}
