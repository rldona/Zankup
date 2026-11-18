package com.zankup.android.domain

/** A split (a lap or a kilometre) as the charts need it: its distance and, when it has one, its time. */
data class Split(val distanceM: Double, val timeS: Double?)

/** Each split's speed in m/s, 0 for one without time, so a missing lap draws as an empty bar. */
fun splitSpeeds(splits: List<Split>): List<Double> = splits.map { split ->
    val time = split.timeS
    if (time != null && time != 0.0) split.distanceM / time else 0.0
}

/**
 * The split under the finger on the training analysis chart: [share] is how far across the chart it
 * is (0 to 1), and each split is as wide as its distance. Past the end it is the last split.
 */
fun splitAt(share: Double, distances: List<Double>): Int {
    val target = share * distances.sum()
    var covered = 0.0
    distances.forEachIndexed { i, distance ->
        covered += distance
        if (target <= covered) return i
    }
    return (distances.size - 1).coerceAtLeast(0)
}

/**
 * The sample under the cursor: the index whose x (distance or time, never decreasing) is closest to
 * [x], the earlier one on a tie. -1 when there are no samples.
 */
fun nearestIndex(xs: List<Double>, x: Double): Int {
    if (xs.isEmpty()) return -1
    // The first sample at or past x, then whichever of it and the one before is closer.
    var lo = 0
    var hi = xs.size - 1
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (xs[mid] < x) lo = mid + 1 else hi = mid
    }
    return if (lo > 0 && x - xs[lo - 1] <= xs[lo] - x) lo - 1 else lo
}
