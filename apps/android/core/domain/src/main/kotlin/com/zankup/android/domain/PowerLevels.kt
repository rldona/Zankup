package com.zankup.android.domain

import kotlin.math.ln

/**
 * A rider's skills on Progress, as Strava groups them: the best power over each skill's durations (in
 * seconds), short for sprinting, middle for attacking, long for climbing.
 */
enum class Skill(val durations: List<Int>) {
    SPRINT(listOf(15, 30, 60)),
    ATTACK(listOf(120, 180, 300, 600)),
    CLIMB(listOf(900, 1200, 1800, 2700, 3600))
}

/** The radar's spokes, clockwise from the top: every skill's durations once, sprint to climb. */
val SKILL_DURATIONS: List<Int> = Skill.entries.flatMap { it.durations }

/** The scale's levels: 1 is a beginner, 9 the best riders. */
const val POWER_LEVELS = 9

/**
 * What a beginner and the best hold for a duration, in W/kg, after the shape of Coggan's power
 * profile. Durations in between are read on a log scale of time.
 */
private data class Anchor(val seconds: Int, val beginner: Double, val best: Double)

private val ANCHORS = listOf(
    Anchor(5, 8.0, 24.0),
    Anchor(60, 4.5, 11.5),
    Anchor(300, 2.5, 7.6),
    Anchor(3600, 1.8, 6.4)
)

/**
 * The W/kg range at a duration (s), beginner to best: 4.5 to 11.5 at a minute, 1.8 to 6.4 at an hour.
 * Outside the anchors it stays at the nearest one.
 */
fun scaleAt(duration: Int): ClosedFloatingPointRange<Double> {
    val pairs = ANCHORS.zipWithNext()
    val (a, b) = pairs.firstOrNull { (_, b) -> duration <= b.seconds } ?: pairs.last()
    val f = (
        (ln(duration.toDouble()) - ln(a.seconds.toDouble())) /
            (ln(b.seconds.toDouble()) - ln(a.seconds.toDouble()))
        )
        .coerceIn(0.0, 1.0)
    return (a.beginner + (b.beginner - a.beginner) * f)..(a.best + (b.best - a.best) * f)
}

/**
 * Where [watts] held for [duration] seconds put a rider of [weightKg]: from 0 (nothing) to 9, with
 * fractions, so a bar can fill part of a level. Level 1 is where the scale starts.
 */
fun levelAt(watts: Double, duration: Int, weightKg: Double): Double {
    if (watts <= 0 || weightKg <= 0) return 0.0
    val scale = scaleAt(duration)
    val share = (watts / weightKg - scale.start) / (scale.endInclusive - scale.start)
    return (1 + (POWER_LEVELS - 1) * share).coerceIn(0.0, POWER_LEVELS.toDouble())
}

/**
 * The watts that reach a [level] at a duration for a weight, the inverse of [levelAt], for the
 * "next level at 320 W" line. Anything under level 1 asks for level 1's watts.
 */
fun levelWatts(level: Double, duration: Int, weightKg: Double): Double {
    val scale = scaleAt(duration)
    val share = (level.coerceAtLeast(1.0) - 1) / (POWER_LEVELS - 1)
    return (scale.start + share * (scale.endInclusive - scale.start)) * weightKg
}

/** A skill's level: the average of its durations' levels, so one lucky effort does not carry it. */
fun skillLevel(levels: List<Double>): Double = if (levels.isEmpty()) 0.0 else levels.average()
