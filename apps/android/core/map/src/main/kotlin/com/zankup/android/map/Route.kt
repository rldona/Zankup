package com.zankup.android.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** An activity's route: one position per sample (NaN in a GPS gap) and the samples' times in seconds. */
@Immutable
class Route(val lat: DoubleArray, val lon: DoubleArray, val t: FloatArray) {
    /** The samples that have a position, in order: the line drawn on the map. */
    val drawn: IntArray = lat.indices.filter { lat[it].isFinite() && lon[it].isFinite() }.toIntArray()

    val hasGps get() = drawn.size >= 2

    /** The nearest sample with a position, so the map's dot never disappears in a GPS gap. */
    fun positionAt(index: Int): Int? {
        if (drawn.isEmpty() || index < 0) return null
        var lo = 0
        var hi = drawn.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (drawn[mid] < index) lo = mid + 1 else hi = mid
        }
        val after = drawn[lo]
        val before = if (lo > 0) drawn[lo - 1] else after
        return if (index - before <= after - index) before else after
    }

    /** The sample at [seconds] from the start (the last one not after it). */
    fun indexAtTime(seconds: Float): Int {
        var lo = 0
        var hi = t.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (t[mid] <= seconds) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** Each drawn point's share of the line's length, as MapLibre's `line-progress` measures it. */
    fun progress(): DoubleArray {
        val out = DoubleArray(drawn.size)
        var total = 0.0
        for (k in 1 until drawn.size) {
            total += metres(drawn[k - 1], drawn[k])
            out[k] = total
        }
        if (total > 0) for (k in out.indices) out[k] /= total
        return out
    }

    private fun metres(a: Int, b: Int): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat[a])
        val p2 = Math.toRadians(lat[b])
        val dp = p2 - p1
        val dl = Math.toRadians(lon[b] - lon[a])
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }
}

/**
 * The colour ramp for a route coloured by a metric, low to high: the zone colours, which read as
 * "easy → hard" in every theme.
 */
val RAMP = listOf(Color(0xFFA5D8FF), Color(0xFF69DB7C), Color(0xFFFFD43B), Color(0xFFFF922B), Color(0xFFF03E3E))

/** A colour on the ramp for a share from 0 to 1, blended between its two nearest stops. */
fun rampColour(share: Float): Color {
    val s = share.coerceIn(0f, 1f) * (RAMP.size - 1)
    val i = s.toInt().coerceAtMost(RAMP.size - 2)
    val f = s - i
    val a = RAMP[i]
    val b = RAMP[i + 1]
    return Color(a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f)
}

/** At most this many colour stops along the line: smooth enough, and quick for MapLibre to restyle. */
const val MAX_STOPS = 240

/** The metric's 2nd and 98th percentile on the route: the two ends of its ramp, for the legend. */
fun colourRange(route: Route, values: FloatArray): Pair<Float, Float>? {
    val finite = route.drawn.map { values.getOrElse(it) { Float.NaN } }.filter { it.isFinite() }.sorted()
    if (finite.size < 2) return null
    return finite[(finite.size * 0.02).toInt()] to finite[((finite.size - 1) * 0.98).toInt()]
}

/**
 * The route's colour stops for a metric: (share of the line's length, colour), each the metric's value at
 * that point placed between its 2nd and 98th percentile (so a stop does not wash the rest out);
 * [inverted] for a pace, where lower is harder. Null when the metric has no values on the route.
 */
fun colourStops(route: Route, values: FloatArray, inverted: Boolean): List<Pair<Double, Color>>? {
    val onRoute = route.drawn.map { values.getOrElse(it) { Float.NaN } }
    val (lo, hi) = colourRange(route, values) ?: return null
    val span = (hi - lo).takeIf { it > 0f } ?: return null
    val progress = route.progress()
    val every = (route.drawn.size / MAX_STOPS).coerceAtLeast(1)
    val stops = mutableListOf<Pair<Double, Color>>()
    var last = Float.NaN
    for (k in route.drawn.indices step every) {
        val v = onRoute[k].takeIf { it.isFinite() } ?: last
        if (!v.isFinite()) continue
        last = v
        val share = (v - lo) / span
        stops += progress[k] to rampColour(if (inverted) 1f - share else share)
    }
    // line-progress stops must rise strictly.
    return stops.distinctBy { it.first }.takeIf { it.size >= 2 }
}
