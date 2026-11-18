package com.zankup.android.charts

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What an activity's charts run along: the distance of every sample in metres, or its time in seconds when
 * the activity has no distance (strength, indoor). Always increasing, one value per sample.
 */
@Immutable
class Profile(val x: FloatArray, val byTime: Boolean) {
    val size get() = x.size
    val total get() = x.lastOrNull() ?: 0f

    /** The sample at [value] along the axis (the last one not after it), by binary search. */
    fun indexAt(value: Float): Int {
        if (x.isEmpty()) return -1
        var lo = 0
        var hi = x.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (x[mid] <= value) lo = mid else hi = mid - 1
        }
        return lo
    }
}

/**
 * Where the finger is along an activity's series (a sample index, -1 for none), shared by every chart and
 * the map. It is snapshot state read only while drawing, so moving it redraws the cursor and nothing else.
 */
@Stable
class ChartCursor {
    var index by mutableIntStateOf(-1)
}

/** The section picked on the charts (sample indices, both ends included), shared with the map. */
@Stable
class ChartSection {
    var range by mutableStateOf<IntRange?>(null)
}

/** One series of an activity, one value per sample in the unit its axis shows; NaN where there is none. */
@Immutable
class Series(
    val label: String,
    val values: FloatArray,
    /** How the value under the finger and the axis are written: whole, one decimal, m:ss… */
    val format: (Float) -> String,
    /** What the header shows while nothing is touched (the average, or the highest altitude). */
    val summary: String,
    /** Lower is better (a pace): the axis runs upside down, so faster is higher. */
    val inverted: Boolean = false,
    /** Keep the axis on zero (power, cadence); otherwise it hugs the values (altitude, heart rate). */
    val fromZero: Boolean = false,
    /** The elevation drawn in grey behind the series, on its own scale (as Strava combines them). */
    val backdrop: FloatArray? = null
)
