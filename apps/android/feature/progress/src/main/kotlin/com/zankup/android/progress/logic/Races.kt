package com.zankup.android.progress.logic

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** The engine's race estimates, with their distance and the badge's words. */
enum class Race(val kind: String, val metres: Double, val badge: String) {
    R5K("race_5k", 5000.0, "5"),
    R10K("race_10k", 10000.0, "10"),
    HALF("race_half_marathon", 21097.5, "21,1"),
    MARATHON("race_marathon", 42195.0, "42,2")
}

/** A prediction on a day. */
data class Point(val day: LocalDate, val value: Double)

/** The predictions page's ranges, and the days each looks back. */
enum class PredictionRange(val key: String, val days: Long) {
    TODAY("today", 0),
    M1("1m", 30),
    M3("3m", 83),
    M6("6m", 166)
}

private fun pad(n: Long) = n.toString().padStart(2, '0')

/** 24:54, or 2:01:35 past the hour. */
fun raceTime(seconds: Double): String {
    val s = seconds.roundToLong()
    val h = s / 3600
    val m = s % 3600 / 60
    return if (h > 0) "$h:${pad(m)}:${pad(s % 60)}" else "$m:${pad(s % 60)}"
}

/** 4:59 per kilometre (or mile). */
fun racePace(seconds: Double, metres: Double, imperial: Boolean): String {
    val s = (seconds / (metres / if (imperial) MILE else 1000.0)).roundToLong()
    return "${s / 60}:${pad(s % 60)}"
}

/** A change in time, short: 2 s · 27 s · 3 min 46 s · 1 h 2 min. */
fun changeText(seconds: Double): String {
    val s = abs(seconds).roundToLong()
    if (s < 60) return "$s s"
    val m = s / 60
    if (m < 60) return "$m min ${s % 60} s"
    return "${m / 60} h ${m % 60} min"
}

/** The prediction on a day: the last change on or before it (the first one if it is older). */
fun valueOn(history: List<Point>, day: LocalDate): Double? {
    val first = history.firstOrNull() ?: return null
    var value = first.value
    for (p in history) {
        if (p.day > day) break
        value = p.value
    }
    return value
}

/**
 * The line for a range: the prediction on its first day, each change inside it and today's, so a
 * prediction that did not change is still a flat line across the range.
 */
fun series(history: List<Point>, from: LocalDate, today: LocalDate): List<Point> {
    val start = valueOn(history, from) ?: return emptyList()
    val out = mutableListOf(Point(from, start))
    var last = out[0]
    for (p in history) {
        if (p.day > from && p.day <= today && abs(p.value - last.value) >= 0.5) {
            out += p
            last = p
        }
    }
    if (last.day != today) out += Point(today, last.value)
    return out
}

private val TIME_STEPS = listOf(10, 15, 30, 60, 120, 180, 300, 600, 900, 1800, 3600)

/** Axis ticks for race times: round steps (10 s to an hour) covering the range, about [count]. */
fun timeTicks(low: Double, high: Double, count: Int = 4): List<Double> {
    var lo = low
    var hi = high
    // At least 4 % of the time high, so a prediction that hardly moved is a calm line mid-chart.
    val least = lo * 0.04
    if (hi - lo < least) {
        val mid = (lo + hi) / 2
        lo = mid - least / 2
        hi = mid + least / 2
    }
    val step = (
        TIME_STEPS.firstOrNull { st -> ceil(hi / st) - floor(lo / st) <= count }
            ?: 3600
        ).toDouble()
    val out = mutableListOf<Double>()
    var v = floor(lo / step) * step
    while (v < hi + step) {
        out += v
        if (v >= hi && out.size >= 2) break
        v += step
    }
    return out
}

/** The days labelled under a prediction's line, between its ends: Mondays over a month, else firsts. */
fun predictionLabelDays(range: PredictionRange, from: LocalDate, today: LocalDate): List<LocalDate> =
    generateSequence(from.plusDays(1)) { it.plusDays(1) }
        .takeWhile { it < today }
        .filter { if (range == PredictionRange.M1) it.dayOfWeek == DayOfWeek.MONDAY else it.dayOfMonth == 1 }
        .toList()
