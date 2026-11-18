package com.zankup.android.health

import com.zankup.android.api.model.HealthDay
import com.zankup.android.api.model.Stage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

// What the Health tab works out, kept pure so it is tested without rendering.

/** The periods of the toolbar: 30, 90 or 365 days. */
enum class HealthPeriod(val days: Int) {
    D30(30),
    D90(90),
    D365(365)
}

/** A span of days, [start] included and [end] excluded, as `GET /api/wellness/days` takes it. */
data class HealthRange(val start: LocalDate, val end: LocalDate)

/** The period ending today: from so many days before tomorrow to tomorrow (exclusive). */
fun healthRange(period: HealthPeriod, today: LocalDate): HealthRange {
    val end = today.plusDays(1)
    return HealthRange(end.minusDays(period.days.toLong()), end)
}

/** One entry per day of [range], the day's figures or null: charts leave a gap where there are none. */
fun healthSpan(days: List<HealthDay>, range: HealthRange): List<Pair<LocalDate, HealthDay?>> {
    val byDay = days.associateBy { it.day }
    val out = ArrayList<Pair<LocalDate, HealthDay?>>()
    var day = range.start
    while (day < range.end) {
        out += day to byDay[day.toString()]
        day = day.plusDays(1)
    }
    return out
}

enum class RelativeDay { TODAY, YESTERDAY, OTHER }

fun relativeDay(day: LocalDate, today: LocalDate): RelativeDay = when (day) {
    today -> RelativeDay.TODAY
    today.minusDays(1) -> RelativeDay.YESTERDAY
    else -> RelativeDay.OTHER
}

/** Seconds as hours, for the sleep chart; 0 when there are none. */
fun hours(seconds: Int?): Double = (seconds ?: 0) / 3600.0

// --- sources --------------------------------------------------------------------------------------

/** Where a figure comes from. The generated client has one enum per field; they share these values. */
enum class HealthSource(val key: String) {
    GARMIN("garmin"),
    GOOGLE_HEALTH("google_health"),
    HEALTH_CONNECT("health_connect");

    companion object {
        fun of(key: String): HealthSource = entries.firstOrNull { it.key == key } ?: HEALTH_CONNECT
    }
}

// --- the night ------------------------------------------------------------------------------------

/** The stages' lanes, top to bottom. */
enum class SleepLane { AWAKE, REM, LIGHT, DEEP }

fun laneOf(stage: Stage.Stage): SleepLane = when (stage) {
    Stage.Stage.AWAKE -> SleepLane.AWAKE
    Stage.Stage.REM -> SleepLane.REM
    Stage.Stage.LIGHT -> SleepLane.LIGHT
    Stage.Stage.DEEP -> SleepLane.DEEP
}

/** Which side the block before or after sits on, when it touches this one. */
enum class Side { ABOVE, BELOW }

/** A stretch in one lane, in seconds from the start of the night. */
data class SleepBlock(val start: Int, val end: Int, val lane: Int, val from: Side?, val to: Side?)

private const val TOUCH_S = 60

/** Consecutive spans of the same stage become one block; neighbours touch when there is no gap. */
fun sleepBlocks(stages: List<Stage>): List<SleepBlock> {
    data class Run(val start: Int, var end: Int, val lane: Int)
    val merged = ArrayList<Run>()
    for (s in stages.sortedBy { it.offsetS }) {
        val lane = laneOf(s.stage).ordinal
        val last = merged.lastOrNull()
        if (last != null && last.lane == lane && s.offsetS - last.end <= TOUCH_S) {
            last.end = maxOf(last.end, s.offsetS + s.durationS)
        } else {
            merged += Run(s.offsetS, s.offsetS + s.durationS, lane)
        }
    }
    fun side(here: Int, other: Run?, touching: Boolean) =
        if (other != null && touching) (if (other.lane < here) Side.ABOVE else Side.BELOW) else null
    return merged.mapIndexed { i, b ->
        val prev = merged.getOrNull(i - 1)
        val next = merged.getOrNull(i + 1)
        SleepBlock(
            b.start,
            b.end,
            b.lane,
            from = side(b.lane, prev, prev != null && b.start - prev.end <= TOUCH_S),
            to = side(b.lane, next, next != null && next.start - b.end <= TOUCH_S)
        )
    }
}

/** Seconds spent in each lane. */
fun stageTotals(stages: List<Stage>): Map<SleepLane, Int> {
    val out = SleepLane.entries.associateWith { 0 }.toMutableMap()
    stages.forEach { out[laneOf(it.stage)] = out.getValue(laneOf(it.stage)) + it.durationS }
    return out
}

enum class SleepBand { EXCELLENT, GOOD, FAIR, POOR }

/** The score's band, as Garmin and Fitbit name them: 90+, 80+, 60+, below. */
fun sleepScoreBand(score: Int): SleepBand = when {
    score >= 90 -> SleepBand.EXCELLENT
    score >= 80 -> SleepBand.GOOD
    score >= 60 -> SleepBand.FAIR
    else -> SleepBand.POOR
}

enum class SleepGoal { MET, ALMOST, SHORT }

const val SLEEP_GOAL_S = 7 * 3600

/** Hours asleep against the recommended seven: met, almost (six or more) or short. */
fun sleepDurationBand(asleepS: Int): SleepGoal = when {
    asleepS >= SLEEP_GOAL_S -> SleepGoal.MET
    asleepS >= SLEEP_GOAL_S - 3600 -> SleepGoal.ALMOST
    else -> SleepGoal.SHORT
}

/** An instant from the API, with or without its zone (then UTC). */
fun parseUtc(iso: String): Instant? = runCatching { Instant.parse(iso) }.getOrNull()
    ?: runCatching { LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC) }.getOrNull()

/** The wall clock where the night was slept: [iso] moved by its offset. */
fun nightClock(iso: String, offsetS: Int?, plusS: Int = 0): LocalTime? = parseUtc(iso)
    ?.plusSeconds(plusS.toLong())
    ?.atOffset(ZoneOffset.ofTotalSeconds(offsetS ?: 0))
    ?.toLocalTime()
