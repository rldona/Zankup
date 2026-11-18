package com.zankup.android.domain

/** Minetti's cost of running on the flat, in J/(kg·m). */
private const val FLAT_COST = 3.6

/** Gradients are clamped here (±45 %), where Minetti's measurements end and his curve stops making sense. */
private const val MAX_GRADE = 0.45

/** Gradients over less than this many metres of distance are noise, read as flat. */
private const val MIN_RUN_M = 5.0

/**
 * Minetti's energy cost of running at a [grade] (0.08 is 8 % uphill), in J/(kg·m): 3.6 on the flat,
 * more uphill, a little less on a gentle downhill and more again on a steep one. The same polynomial
 * as the engine's average grade-adjusted pace, so the chart and the figure agree.
 */
fun runningCost(grade: Double): Double {
    val i = grade.coerceIn(-MAX_GRADE, MAX_GRADE)
    return (((((155.4 * i - 30.4) * i - 43.3) * i + 46.3) * i + 19.5) * i) + FLAT_COST
}

/**
 * Grade-adjusted speed (m/s) at each sample, for the run's "Grade-adjusted pace" chart: the speed
 * times the cost of the gradient over the cost on the flat, so a slow climb reads as the flat effort
 * it was. The gradient is altitude over the [windowM] metres of distance around the sample (or to the
 * samples either side when they are further apart than that). Null where speed, distance or altitude
 * is missing.
 */
fun gradeAdjustedSpeeds(
    speed: List<Double?>,
    distance: List<Double?>,
    altitude: List<Double?>,
    windowM: Double = 50.0
): List<Double?> {
    val n = speed.size
    val half = windowM / 2
    fun located(j: Int) = distance.getOrNull(j) != null && altitude.getOrNull(j) != null
    val out = MutableList<Double?>(n) { null }
    // The window's ends only move forward, as the distance only grows.
    var first = 0
    var last = 0
    for (i in 0 until n) {
        val v = speed[i] ?: continue
        if (!located(i)) continue
        val d = distance[i]!!
        while (first < i && (!located(first) || d - distance[first]!! > half)) first++
        if (last < i) last = i
        while (last < n - 1 && located(last + 1) && distance[last + 1]!! - d <= half) last++
        var lo = first
        var hi = last
        if (lo == hi) {
            if (lo > 0 && located(lo - 1)) lo--
            if (hi < n - 1 && located(hi + 1)) hi++
        }
        val run = distance[hi]!! - distance[lo]!!
        val grade = if (run > MIN_RUN_M) (altitude[hi]!! - altitude[lo]!!) / run else 0.0
        out[i] = v * runningCost(grade) / FLAT_COST
    }
    return out
}
