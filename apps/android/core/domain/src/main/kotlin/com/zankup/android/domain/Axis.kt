package com.zankup.android.domain

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/** A chart's value axis: where it starts and ends, and its labelled ticks, evenly spaced. */
data class Axis(val min: Double, val max: Double, val ticks: List<Double>)

/** Round steps, times a power of ten: 1, 2, 2.5 and 5 read well as labels (10, 20, 25, 50…). */
private val ROUND_STEPS = listOf(1.0, 2.0, 2.5, 5.0)

/** Tolerance for floating point when a step lands exactly on the data. */
private const val EPSILON = 1e-9

/**
 * The axis with the smallest round step that covers [lo] to [hi] in [intervals] steps, starting on a
 * multiple of the step: 47.3–61.2 gives 45, 50 … 65.
 */
fun niceAxis(lo: Double, hi: Double, intervals: Int = 4): Axis {
    val span = (hi - lo).takeIf { it != 0.0 } ?: abs(hi).takeIf { it != 0.0 } ?: 1.0
    var magnitude = 10.0.pow(floor(log10(span / intervals)))
    // Forty powers of ten is far past any real data; it only guards against NaN looping forever.
    repeat(40) {
        for (m in ROUND_STEPS) {
            val step = m * magnitude
            val start = floor(lo / step + EPSILON) * step
            if (start + intervals * step >= hi - EPSILON) {
                val ticks = (0..intervals).map { tidy(start + it * step) }
                return Axis(tidy(start), tidy(start + intervals * step), ticks)
            }
        }
        magnitude *= 10
    }
    return Axis(lo, hi, listOf(lo, hi))
}

/**
 * The tightest round axis over [lo] to [hi] in four to six steps, so the lines fill the chart: 0–5312
 * ends at 6000 (six steps of 1000), not at 8000 (four of 2000). On a tie, the fewer steps.
 */
fun tightAxis(lo: Double, hi: Double): Axis = (4..6).map { niceAxis(lo, hi, it) }.minBy { it.max - it.min }

/** Drops the float dust of adding steps (0.30000000000000004 back to 0.3). */
private fun tidy(v: Double) = (v * 1e6).roundToLong() / 1e6
