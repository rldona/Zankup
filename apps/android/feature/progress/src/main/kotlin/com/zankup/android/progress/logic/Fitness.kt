package com.zankup.android.progress.logic

import com.zankup.android.api.model.Bucket
import com.zankup.android.api.model.FitnessDay
import com.zankup.android.domain.EffortRange
import com.zankup.android.domain.EffortStatus
import com.zankup.android.domain.effortStatus
import com.zankup.android.domain.weeklyRange
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** The fitness page's ranges, a month by default. */
enum class FitnessRange(val key: String, val days: Long) {
    M1("1m", 30),
    M3("3m", 90),
    M6("6m", 182),
    Y1("1y", 365),
    Y2("2y", 730)
}

/** The days a range covers, as the API takes them: from its first day to tomorrow (excluded). */
fun rangePeriod(range: FitnessRange, today: LocalDate): DayRange =
    DayRange(today.minusDays(range.days), today.plusDays(1))

enum class Direction {
    UP,
    DOWN,
    NONE
}

/** From one score to another: Strava's "▼ 24 % −23 pt". */
data class Change(val points: Int, val percent: Int, val direction: Direction)

fun fitnessChange(from: Double, to: Double): Change {
    val points = to.roundHalfUp() - from.roundHalfUp()
    val percent = if (from > 0) (abs(to - from) / from * 100).roundHalfUp() else 0
    val direction = when {
        points > 0 -> Direction.UP
        points < 0 -> Direction.DOWN
        else -> Direction.NONE
    }
    return Change(points, percent, direction)
}

/** The relative effort of a very hard day: a ball tops out there. */
private const val HARD_DAY_EFFORT = 200.0

/**
 * A day's ball: nothing without an activity, then bigger the harder the day by its relative effort
 * (the same figure the Relative effort card shows). A walk or half an hour of gym stays the smallest;
 * a 10 km run or a long ride grow with what they cost, up to [HARD_DAY_EFFORT]. Small, as Strava's:
 * the hard day's is twice the easy one's, but neither takes over the line.
 */
fun loadRadius(load: Double): Float {
    if (load <= 0) return 0f
    return (1.6 + 2.4 * (load / HARD_DAY_EFFORT).coerceIn(0.0, 1.0)).toFloat()
}

private const val CTL_DAYS = 42

/**
 * The series up to today. The server rewrites it when an activity comes in, so after days without
 * training it stops at the last one: the missing days are added as the model has them, without load.
 */
fun untilToday(days: List<FitnessDay>, today: LocalDate): List<FitnessDay> {
    val last = days.lastOrNull() ?: return days
    val start = day(last.day) ?: return days
    val out = days.toMutableList()
    var ctl = last.ctl
    var d = start.plusDays(1)
    while (d <= today) {
        ctl -= ctl / CTL_DAYS
        out += last.copy(day = d.toString(), load = 0.0, ctl = ctl)
        d = d.plusDays(1)
    }
    return out
}

private fun roundScale(top: Double, intervals: Int): List<Double> {
    var magnitude = 10.0.pow(floor(log10(top / intervals)))
    repeat(40) {
        for (m in listOf(1.0, 2.0, 2.5, 5.0)) {
            val step = m * magnitude
            if (intervals * step >= top - 1e-9) return (0..intervals).map { (it * step * 1e6).roundToInt() / 1e6 }
        }
        magnitude *= 10
    }
    return listOf(0.0, top)
}

/**
 * The fitness chart's axis: from zero to the tightest round value over the line's top, in four to nine
 * steps (Strava: 106 → 120, not 125; 80 → 90, not 100), so the line uses the height. On a tie, the
 * fewer steps. Never under 10.
 */
fun fitnessScale(values: List<Double>): List<Double> {
    val top = max(10.0, values.maxOrNull() ?: 0.0) * 1.05
    return (4..9).map { roundScale(top, it) }.reduce { best, s -> if (s.last() < best.last()) s else best }
}

/** The days written under a chart of [count] days: about [wanted], evenly spread, the first and the last. */
fun spreadDays(count: Int, wanted: Int = 5): List<Int> {
    if (count <= 0) return emptyList()
    if (count <= wanted) return (0 until count).toList()
    val every = (count - 1).toDouble() / (wanted - 1)
    return (0 until wanted).map { (it * every).roundHalfUp() }
}

/** A week of relative effort: its score, the range of the three weeks before it and where it falls. */
data class EffortWeek(val monday: LocalDate, val score: Int, val range: EffortRange?, val status: EffortStatus)

/**
 * Weekly buckets of effort (oldest first) as Relative effort's weeks. The first three buckets only give
 * the range of the fourth, so they are not weeks of their own.
 */
fun effortWeeks(buckets: List<Bucket>): List<EffortWeek> {
    val scores = buckets.mapNotNull { b -> day(b.start)?.let { it to (b.totals.load ?: 0.0).roundHalfUp() } }
    return scores.drop(3).mapIndexed { i, (monday, score) ->
        val range = weeklyRange(scores.subList(i, i + 3).map { it.second.toDouble() })
        EffortWeek(monday, score, range, effortStatus(score.toDouble(), range))
    }
}

/** Each day's load of the week starting on [monday], from the volume by day. */
fun weekLoads(buckets: List<Bucket>, monday: LocalDate): List<Double> {
    val byDay = buckets.associate { it.start.take(10) to (it.totals.load ?: 0.0) }
    return weekDays(monday).map { byDay[it.toString()] ?: 0.0 }
}
