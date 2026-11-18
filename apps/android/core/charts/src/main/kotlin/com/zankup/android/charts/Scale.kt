package com.zankup.android.charts

import com.zankup.android.domain.niceAxis
import com.zankup.android.domain.tightAxis

/** The values' range for an axis: all of them, or from the 2nd to the 98th percentile ([clip]). */
internal fun valueRange(values: FloatArray, clip: Boolean): Pair<Float, Float>? {
    val finite = values.filter { it.isFinite() }
    if (finite.isEmpty()) return null
    if (!clip || finite.size < 50) return finite.min() to finite.max()
    val sorted = finite.sorted()
    return sorted[(sorted.size * 0.02).toInt()] to sorted[((sorted.size - 1) * 0.98).toInt()]
}

/** The round axis for a series: from zero when it says so, else the tightest one around its values. */
internal fun axisFor(values: FloatArray, fromZero: Boolean, clip: Boolean): com.zankup.android.domain.Axis? {
    val (lo, hi) = valueRange(values, clip) ?: return null
    return if (fromZero) niceAxis(0.0, hi.toDouble().coerceAtLeast(1.0)) else tightAxis(lo.toDouble(), hi.toDouble())
}

/**
 * Round marks along the x axis that fit: every 1, 2, 5, 10, 20 or 50 units (km, mi or minutes), the
 * smallest step that gives at most [most] marks. Returns the step, in metres or seconds.
 */
fun markStep(total: Float, unit: Float, most: Int = 6): Float {
    if (total <= 0f) return unit
    val step = listOf(1, 2, 5, 10, 20, 50, 100, 200, 500).map { it * unit }.firstOrNull { total / it <= most }
    return step ?: (total / most)
}

/**
 * The value of every pixel column: the average of the samples that fall in it, so a long activity
 * draws as fast as a short one and its shape survives. NaN where a column has no value.
 */
internal fun columns(profile: Profile, values: FloatArray, count: Int): FloatArray {
    val out = FloatArray(count) { Float.NaN }
    if (count <= 0 || profile.size == 0 || profile.total <= 0f) return out
    val perColumn = profile.total / count
    var i = 0
    for (c in 0 until count) {
        val end = (c + 1) * perColumn
        var sum = 0f
        var n = 0
        while (i < profile.size && profile.x[i] <= end) {
            val v = values.getOrElse(i) { Float.NaN }
            if (v.isFinite()) {
                sum += v
                n++
            }
            i++
        }
        if (n > 0) out[c] = sum / n
    }
    // A column narrower than the samples: carry the neighbour so the line has no holes that are not gaps.
    for (c in 1 until count) {
        if (out[c].isNaN() &&
            out[c - 1].isFinite() &&
            c + 1 < count &&
            out[c + 1].isFinite()
        ) {
            out[c] = out[c - 1]
        }
    }
    return out
}
