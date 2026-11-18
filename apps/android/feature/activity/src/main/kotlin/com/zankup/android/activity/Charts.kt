package com.zankup.android.activity

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.zankup.android.api.model.ActivityContext
import com.zankup.android.api.model.ActivityDetail
import com.zankup.android.api.model.ActivityStreams
import com.zankup.android.charts.Profile
import com.zankup.android.charts.Series
import com.zankup.android.charts.Zone
import com.zankup.android.domain.Sport
import com.zankup.android.domain.formatDuration
import com.zankup.android.domain.formatHoursMinutes
import com.zankup.android.domain.gradeAdjustedSpeeds
import com.zankup.android.domain.paceZoneSeconds
import com.zankup.android.map.MapMetric
import com.zankup.android.map.Route
import com.zankup.android.map.colourRange
import com.zankup.android.map.colourStops
import com.zankup.android.ui.Formats

/** The kinds of chart an activity has, in the order a sport shows them. */
enum class Kind { POWER, HR, SPEED, PACE, GAP, CADENCE, ELEVATION }

/** One chart card: its series, and its zones under it when it has them. */
@Immutable
class Chart(val kind: Kind, val series: Series, val zones: List<Zone> = emptyList())

/** Everything drawn from the series: the axis, the route, the charts and what the map can be coloured by. */
@Immutable
class Drawn(
    val profile: Profile,
    val route: Route,
    val charts: Map<Kind, Chart>,
    val metrics: List<MapMetric>,
    val distance: FloatArray,
    val time: FloatArray,
    val altitude: FloatArray?,
    val heartRate: FloatArray?
)

/** The names the charts and the map show, from the resources. */
class ChartLabels(
    val pace: String,
    val speed: String,
    val gap: String,
    val hr: String,
    val power: String,
    val cadence: String,
    val elevation: String,
    val route: String
)

private fun FloatArray.mean(): Float =
    filter { it.isFinite() }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: Float.NaN

/** Seconds in each zone as thick bars: "Z2", its range, the time. Nothing when every zone is empty. */
private fun zones(seconds: List<Double>?, ranges: List<String>, colours: List<Color>): List<Zone> {
    if (seconds.isNullOrEmpty() || seconds.sum() <= 0) return emptyList()
    return seconds.mapIndexed { i, s ->
        Zone(
            "Z${i + 1}",
            ranges.getOrElse(i) {
                ""
            },
            s.toFloat(),
            formatHoursMinutes(s),
            colours[i.coerceAtMost(colours.lastIndex)]
        )
    }
}

/** "120–140", the last one "≥ 175", from each zone's lower bound. */
private fun ranges(bounds: List<Double>?, format: (Double) -> String): List<String> {
    if (bounds.isNullOrEmpty()) return emptyList()
    return bounds.mapIndexed { i, lo ->
        bounds.getOrNull(i + 1)?.let { hi -> "${format(lo)}–${format(hi)}" }
            ?: "≥ ${format(lo)}"
    }
}

fun draw(
    s: ActivityStreams,
    a: ActivityDetail,
    context: ActivityContext?,
    sport: Sport,
    f: Formats,
    labels: ChartLabels,
    zoneColours: List<Color>
): Drawn {
    val n = s.t.size
    var last = 0.0
    val dist = FloatArray(n) { i -> (s.dist?.getOrNull(i)?.takeIf { it >= last } ?: last).also { last = it }.toFloat() }
    val byTime = (dist.lastOrNull() ?: 0f) <= 0f
    val t = FloatArray(n) { s.t[it].toFloat() }
    fun of(values: List<Number?>?) = values?.let { v -> FloatArray(n) { v.getOrNull(it)?.toFloat() ?: Float.NaN } }
    val alt = of(s.alt)
    val hr = of(s.hr)
    val power = of(s.power)
    val cad = of(s.cad)
    val speed = of(s.speed)
    val route = Route(
        DoubleArray(n) { s.lat?.getOrNull(it) ?: Double.NaN },
        DoubleArray(n) {
            s.lon?.getOrNull(it)
                ?: Double.NaN
        },
        t
    )
    val m = a.metrics
    val charts = linkedMapOf<Kind, Chart>()
    val metrics = mutableListOf(MapMetric("plain", labels.route, null))

    fun colourBy(key: String, label: String, values: FloatArray, end: (Float) -> String) {
        val stops = colourStops(route, values, inverted = false) ?: return
        val (lo, hi) = colourRange(route, values) ?: return
        metrics += MapMetric(key, label, stops, end(lo), end(hi))
    }

    fun paceOf(mps: Float) = if (mps.isFinite() && mps > 0.5f) 1000f / mps else Float.NaN
    val perUnit = f.unit.let { if (f.units == com.zankup.android.domain.Units.METRIC) 1000f else 1609.344f }

    if (power != null) {
        val bounds = ranges(m?.powerZoneBounds) { "%.0f".format(it) }
        val series =
            Series(labels.power, power, {
                "%.0f W".format(it)
            }, "%.0f W".format(power.mean()), fromZero = true, backdrop = alt)
        charts[Kind.POWER] = Chart(Kind.POWER, series, zones(m?.powerZones, bounds, zoneColours))
        colourBy("power", labels.power, power) { "%.0f W".format(it) }
    }
    if (hr != null) {
        val bounds = ranges(m?.hrZoneBounds) { "%.0f".format(it) }
        val series = Series(labels.hr, hr, { "%.0f".format(it) }, "%.0f bpm".format(hr.mean()), backdrop = alt)
        charts[Kind.HR] = Chart(Kind.HR, series, zones(m?.hrZones, bounds, zoneColours))
        colourBy("hr", labels.hr, hr) { "%.0f bpm".format(it) }
    }
    if (speed != null && sport.usesPace) {
        val pace = FloatArray(n) { paceOf(speed[it]) * perUnit / 1000f }
        val bounds = context?.paceZones?.bounds
        val seconds =
            bounds?.let { b ->
                paceZoneSeconds(speed.map { it.takeIf(Float::isFinite)?.toDouble() }, t.map { it.toDouble() }, b)
            }
        val ranges = ranges(bounds?.map { 1000.0 / it * perUnit / 1000 }) { formatDuration(it) }
        val series = Series(
            labels.pace,
            pace,
            { formatDuration(it.toDouble()) },
            f.unit.pace(
                1000.0 / (paceOf(speed.mean()))
            ),
            inverted = true,
            backdrop = alt
        )
        charts[Kind.PACE] = Chart(Kind.PACE, series, zones(seconds, ranges, zoneColours))
        colourBy("pace", labels.pace, speed) { f.unit.pace(it.toDouble()) }
        if (alt != null) {
            val gap =
                gradeAdjustedSpeeds(
                    speed.map {
                        it.takeIf(Float::isFinite)?.toDouble()
                    },
                    dist.map { it.toDouble() },
                    alt.map { it.takeIf(Float::isFinite)?.toDouble() }
                )
            val gapPace = FloatArray(n) { paceOf(gap.getOrNull(it)?.toFloat() ?: Float.NaN) * perUnit / 1000f }
            charts[Kind.GAP] =
                Chart(
                    Kind.GAP,
                    Series(
                        labels.gap,
                        gapPace,
                        { formatDuration(it.toDouble()) },
                        m?.gapSpeed?.let { f.unit.pace(it) } ?: "—",
                        inverted = true,
                        backdrop = alt
                    )
                )
        }
    } else if (speed != null) {
        val factor = if (f.units == com.zankup.android.domain.Units.METRIC) 3.6f else 2.23694f
        val shown = FloatArray(n) { speed[it] * factor }
        val series =
            Series(labels.speed, shown, {
                "%.1f".format(it)
            }, f.unit.speed(speed.mean().toDouble()), fromZero = true, backdrop = alt)
        charts[Kind.SPEED] = Chart(Kind.SPEED, series)
        colourBy("speed", labels.speed, speed) { f.unit.speed(it.toDouble()) }
    }
    if (cad != null) {
        charts[Kind.CADENCE] =
            Chart(
                Kind.CADENCE,
                Series(labels.cadence, cad, {
                    "%.0f".format(it)
                }, "%.0f".format(cad.mean()), fromZero = true, backdrop = alt)
            )
    }
    if (alt != null) {
        val highest = f.elevation(alt.filter { it.isFinite() }.maxOrNull()?.toDouble())
        charts[Kind.ELEVATION] = Chart(Kind.ELEVATION, Series(labels.elevation, alt, { "%.0f".format(it) }, highest))
        colourBy("alt", labels.elevation, alt) { f.elevation(it.toDouble()) }
    }
    return Drawn(Profile(if (byTime) t else dist, byTime), route, charts, metrics, dist, t, alt, hr)
}

/** The charts each sport shows, in its order (Strava's), among those its series have. */
fun order(sport: Sport): List<Kind> = when (sport) {
    Sport.RIDE -> listOf(Kind.POWER, Kind.HR, Kind.SPEED, Kind.CADENCE, Kind.ELEVATION)
    Sport.RUN -> listOf(Kind.PACE, Kind.GAP, Kind.HR, Kind.CADENCE, Kind.ELEVATION)
    Sport.STRENGTH -> listOf(Kind.HR)
    else -> listOf(Kind.PACE, Kind.SPEED, Kind.HR, Kind.POWER, Kind.CADENCE, Kind.ELEVATION)
}
