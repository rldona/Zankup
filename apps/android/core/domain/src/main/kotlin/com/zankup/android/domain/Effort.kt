package com.zankup.android.domain

import kotlin.math.roundToInt

/**
 * The range a week's relative effort is judged against (the sum of its activities' load, the engine's
 * TRIMP), as Strava draws it: "171–341" around an average of 256.
 */
data class EffortRange(val lo: Int, val hi: Int)

/** Where a week's effort falls against its range. */
enum class EffortStatus {
    BELOW,
    IN,
    ABOVE,

    /** Half again over the top of the range: a week to recover from. */
    FAR_ABOVE
}

/** How hard an activity was, for its colour on the week's chart. */
enum class EffortBand {
    LIGHT,
    MODERATE,
    HARD
}

/** How many weeks before the one judged make its range. */
private const val WEEKS_BEFORE = 3

/**
 * The range for a week from the efforts of the weeks [before] it, oldest first: two thirds to four
 * thirds of the last three weeks' average (fewer when there are fewer). Null with nothing to compare
 * with, or with no effort at all.
 */
fun weeklyRange(before: List<Double>): EffortRange? {
    val last = before.takeLast(WEEKS_BEFORE)
    if (last.isEmpty()) return null
    val average = last.average()
    if (average <= 0) return null
    return EffortRange(lo = (average * 2 / 3).roundToInt(), hi = (average * 4 / 3).roundToInt())
}

/** Where a week's [score] falls against its [range]; in range when there is none to judge by. */
fun effortStatus(score: Double, range: EffortRange?): EffortStatus = when {
    range == null -> EffortStatus.IN
    score < range.lo -> EffortStatus.BELOW
    score <= range.hi -> EffortStatus.IN
    score > range.hi * 1.5 -> EffortStatus.FAR_ABOVE
    else -> EffortStatus.ABOVE
}

/**
 * The week's running total, Monday first, for the line that climbs through it. Days after [upTo]
 * (today's index) are left out, so the line stops today instead of running flat to Sunday.
 */
fun cumulative(days: List<Double>, upTo: Int = days.size - 1): List<Double> =
    days.take((upTo + 1).coerceAtLeast(0)).runningReduce(Double::plus)

/** An activity's band by its effort: light under 50, moderate under 150, hard from there. */
fun effortBand(effort: Double): EffortBand = when {
    effort < 50 -> EffortBand.LIGHT
    effort < 150 -> EffortBand.MODERATE
    else -> EffortBand.HARD
}
