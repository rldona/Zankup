package com.zankup.android.progress.more

import androidx.compose.ui.graphics.Color
import com.zankup.android.domain.Sport
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Training zones as Strava's (packages/core progress/zones.ts): top zone, its change, ranges, the sentence. */

internal enum class ZoneKind(val key: String) {
    HR("hr"),
    POWER("power"),
    PACE("pace")
}

/** The zones a sport has: every sport heart rate, runs and walks pace too, rides power too. */
internal fun zoneKinds(sport: Sport?): List<ZoneKind> = when (sport) {
    Sport.RUN, Sport.WALK -> listOf(ZoneKind.HR, ZoneKind.PACE)
    Sport.RIDE -> listOf(ZoneKind.HR, ZoneKind.POWER)
    else -> listOf(ZoneKind.HR)
}

/** The zones a sport opens on: a run's pace, a ride's power, heart rate for the rest. */
internal fun defaultZoneKind(sport: Sport?): ZoneKind = when (sport) {
    Sport.RUN -> ZoneKind.PACE
    Sport.RIDE -> ZoneKind.POWER
    else -> ZoneKind.HR
}

internal data class TopZone(val index: Int, val share: Int)

/** The zone with the most time: its index (0 is Z1) and share (0 to 100); null without any time. */
internal fun topZone(seconds: List<Double>): TopZone? {
    val total = seconds.sum()
    if (total <= 0) return null
    var index = 0
    seconds.forEachIndexed { i, s -> if (s > seconds[index]) index = i }
    return TopZone(index, (seconds[index] / total * 100).roundToInt())
}

/** How a zone's share moved against the span before, in points; null when there is nothing before. */
internal fun shareChange(seconds: List<Double>, previous: List<Double>, index: Int): Int? {
    val before = previous.sum()
    val now = seconds.sum()
    if (before <= 0 || now <= 0) return null
    val a = seconds.getOrElse(index) { 0.0 } / now * 100
    val b = previous.getOrElse(index) { 0.0 } / before * 100
    return (a - b).roundToInt()
}

/** A zone's range from the lower bounds: "<110", "111 - 150" or ">301", as Strava writes them. */
internal fun zoneRange(bounds: List<Double>, index: Int): String {
    if (bounds.isEmpty()) return ""
    val lo = bounds.getOrElse(index) { 0.0 }.roundToLong()
    val next = bounds.getOrNull(index + 1)?.roundToLong()
    if (index == 0 && lo <= 0) return "<${(next ?: 1) - 1}"
    if (next == null) return ">$lo"
    return "${if (index == 0) lo else lo + 1} - $next"
}

/** A zone's range as paces: "> 7:00", "5:25 - 6:02", "< 4:46" (per km or mile, [unit] metres). */
internal fun paceRange(bounds: List<Double>, index: Int, unit: Double): String {
    fun pace(mps: Double): String {
        val s = (unit / mps).roundToLong()
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }
    val lo = bounds.getOrNull(index)
    val hi = bounds.getOrNull(index + 1)
    if (lo == null || lo == 0.0) return if (hi != null && hi != 0.0) "> ${pace(hi)}" else ""
    if (hi == null) return "< ${pace(lo)}"
    return "${pace(hi)} - ${pace(lo)}"
}

internal enum class Band { EASY, STEADY, HARD }

internal data class Glance(val zone: Int, val percent: Int, val up: Boolean, val band: Band)

/** The zone that moved most against the span before (its time, in %) and its band; null with nothing before. */
internal fun glance(seconds: List<Double>, previous: List<Double>): Glance? {
    if (previous.none { it > 0 } || seconds.none { it > 0 }) return null
    var zone = 0
    var most = 0.0
    seconds.forEachIndexed { i, s ->
        val before = previous.getOrElse(i) { 0.0 }
        if (before <= 0) return@forEachIndexed
        val change = (s - before) / before
        if (abs(change) > abs(most)) {
            most = change
            zone = i
        }
    }
    if (most == 0.0) return null
    val band = when {
        zone < 2 -> Band.EASY
        zone < ceil(seconds.size / 2.0) -> Band.STEADY
        else -> Band.HARD
    }
    return Glance(zone, (abs(most) * 100).roundToInt(), most > 0, band)
}

/** A change in time, short: 2s · 27s · 3min 46s · 1h 2min. */
internal fun changeText(seconds: Double): String {
    val s = abs(seconds).roundToLong()
    if (s < 60) return "${s}s"
    val m = s / 60
    if (m < 60) return "${m}min ${s % 60}s"
    return "${m / 60}h ${m % 60}min"
}

/** Zone colours as Strava's: heart rate in reds, pace in blues, power in purples, light (Z1) to dark. */
private val ZONE_SCALES =
    mapOf(
        ZoneKind.HR to listOf(0xFFFCD0D1, 0xFFF3A4A3, 0xFFEE7675, 0xFFEB4748, 0xFFDE2625),
        ZoneKind.PACE to listOf(0xFFCCE0F2, 0xFF99C0EC, 0xFF66A0E1, 0xFF327FD9, 0xFF0160CF, 0xFF004DA7),
        ZoneKind.POWER to
            listOf(0xFFE8CFF9, 0xFFD29EF1, 0xFFBC6EED, 0xFFA63DE7, 0xFF910DE2, 0xFF730AB2, 0xFF550886)
    )

/** [count] colours of a kind's scale, light to dark: all of them, or spread over the scale. */
internal fun zoneScale(kind: ZoneKind, count: Int): List<Color> {
    val scale = ZONE_SCALES.getValue(kind)
    if (count == scale.size) return scale.map { Color(it) }
    return List(count) { i ->
        Color(scale[(i * (scale.size - 1) / maxOf(1, count - 1).toDouble()).roundToInt()])
    }
}

/** Progress's ranges: 7 days, a month (default), 3 and 6 months, the year to date and a year. */
internal enum class ProgressRange(val key: String) {
    D7("7d"),
    M1("1m"),
    M3("3m"),
    M6("6m"),
    YTD("ytd"),
    Y1("1y")
}

/** What a range covers, [from, to). */
internal data class DaySpan(val from: LocalDate, val to: LocalDate)

/** The range's span, as packages/core volumeRange's `current`. */
internal fun rangeSpan(range: ProgressRange, today: LocalDate): DaySpan {
    val tomorrow = today.plusDays(1)
    return when (range) {
        ProgressRange.D7 -> DaySpan(today.minusDays(6), tomorrow)
        ProgressRange.M1 -> DaySpan(today.minusDays(30), tomorrow)
        ProgressRange.YTD -> DaySpan(today.withDayOfYear(1), tomorrow)
        ProgressRange.M3 -> DaySpan(mondayOf(today).minusDays(7L * 11), tomorrow)
        ProgressRange.M6 -> DaySpan(mondayOf(today).minusDays(7L * 23), tomorrow)
        ProgressRange.Y1 -> DaySpan(mondayOf(today).minusDays(7L * 51), tomorrow)
    }
}
