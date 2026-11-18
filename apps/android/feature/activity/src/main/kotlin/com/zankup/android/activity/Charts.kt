package com.zankup.android.activity

import androidx.compose.runtime.Immutable
import com.zankup.android.api.model.ActivityContext
import com.zankup.android.api.model.ActivityDetail
import com.zankup.android.api.model.ActivityStreams
import com.zankup.android.charts.Profile
import com.zankup.android.charts.Series
import com.zankup.android.domain.Sport
import com.zankup.android.domain.Units
import com.zankup.android.domain.formatDuration
import com.zankup.android.domain.formatNumber
import com.zankup.android.domain.gradeAdjustedSpeeds
import com.zankup.android.domain.paceZoneSeconds
import com.zankup.android.map.MapMetric
import com.zankup.android.map.Route
import com.zankup.android.map.colourRange
import com.zankup.android.map.colourStops
import com.zankup.android.ui.Formats
import kotlin.math.roundToInt

/** The kinds of chart an activity has, in the order a sport shows them. */
enum class Kind { POWER, HR, SPEED, PACE, GAP, CADENCE, ELEVATION }

/** One chart card: its series and the figures under it (average, maximum…). */
@Immutable
class Chart(val kind: Kind, val series: Series, val footer: List<Pair<String, String>> = emptyList())

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
    val heartRate: FloatArray?,
    /** Seconds in each pace zone (a run with the server's zones), else null. */
    val paceSeconds: List<Double>? = null
)

/** The names the charts, their figures and the map show, from the resources. */
class ChartLabels(
    val pace: String,
    val speed: String,
    val gap: String,
    val hr: String,
    val power: String,
    val cadence: String,
    /** "Elevation" on a run, "Altitude" elsewhere (as Strava's). */
    val elevation: String,
    val altitude: String,
    val average: String,
    val maximum: String,
    val minimum: String,
    val bestPace: String,
    val gapShort: String,
    val gapAverage: String,
    val ascent: String,
    val descent: String,
    val effort: String,
    val np: String,
    val vi: String,
    val spm: String
)

private fun FloatArray.mean(): Float =
    filter { it.isFinite() && it > 0f }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: Float.NaN

private fun FloatArray.highest(): Float = filter { it.isFinite() }.maxOrNull() ?: Float.NaN

fun draw(
    s: ActivityStreams,
    a: ActivityDetail,
    context: ActivityContext?,
    sport: Sport,
    f: Formats,
    labels: ChartLabels
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
        DoubleArray(n) { s.lon?.getOrNull(it) ?: Double.NaN },
        t
    )
    val m = a.metrics
    val charts = linkedMapOf<Kind, Chart>()
    // As the React Native app: speed (or pace) first, so the route opens coloured by it.
    val metrics = mutableListOf<MapMetric>()
    val metric = f.units == Units.METRIC
    val perUnit = if (metric) 1000f else 1609.344f

    fun colourBy(key: String, label: String, values: FloatArray, end: (Float) -> String) {
        val stops = colourStops(route, values, inverted = false) ?: return
        val (lo, hi) = colourRange(route, values) ?: return
        metrics += MapMetric(key, label, stops, end(lo), end(hi))
    }

    fun paceOf(mps: Float) = if (mps.isFinite() && mps > 0.5f) perUnit / mps / 60f else Float.NaN
    fun paceText(minutes: Float): String = if (minutes.isFinite()) formatDuration((minutes * 60).toDouble()) else "—"
    fun bpm(v: Number?) = v?.toDouble()?.takeIf { it.isFinite() }?.let { "${it.roundToInt()} bpm" } ?: "—"
    fun watts(v: Number?) = v?.toDouble()?.takeIf { it.isFinite() }?.let { "${it.roundToInt()} W" } ?: "—"

    var paceSeconds: List<Double>? = null
    if (speed != null && speed.any { it.isFinite() && it > 0f }) {
        val moving = speed.filter { it.isFinite() && it > 0.5f }
        val mean = moving.takeIf { it.isNotEmpty() }?.average()
        if (sport.usesPace) {
            // Minutes per km (or mile); standing still has no pace.
            val pace = FloatArray(n) { paceOf(speed[it]) }
            val footer =
                listOfNotNull(
                    labels.average to (mean?.let { f.unit.pace(it) } ?: "—"),
                    labels.bestPace to (moving.maxOrNull()?.let { f.unit.pace(it.toDouble()) } ?: "—"),
                    m?.gapSpeed?.let { labels.gapShort to f.unit.pace(it) }
                )
            charts[Kind.PACE] =
                Chart(
                    Kind.PACE,
                    Series(
                        labels.pace,
                        pace,
                        ::paceText,
                        mean?.let {
                            f.unit.pace(it)
                        } ?: "—",
                        inverted = true,
                        backdrop = alt
                    ),
                    footer
                )
            context?.paceZones?.bounds?.let { b ->
                paceSeconds =
                    paceZoneSeconds(speed.map { it.takeIf(Float::isFinite)?.toDouble() }, t.map { it.toDouble() }, b)
            }
            if (sport == Sport.RUN && alt != null) {
                val gap =
                    gradeAdjustedSpeeds(
                        speed.map { it.takeIf(Float::isFinite)?.toDouble() },
                        dist.map { it.toDouble() },
                        alt.map { it.takeIf(Float::isFinite)?.toDouble() }
                    )
                val gapPace = FloatArray(n) { paceOf(gap.getOrNull(it)?.toFloat() ?: Float.NaN) }
                val gapMoving = gap.filterNotNull().filter { it > 0.5 }
                val average = m?.gapSpeed ?: gapMoving.takeIf { it.isNotEmpty() }?.average()
                if (gapMoving.isNotEmpty()) {
                    charts[Kind.GAP] =
                        Chart(
                            Kind.GAP,
                            Series(
                                labels.gap,
                                gapPace,
                                ::paceText,
                                average?.let { f.unit.pace(it) } ?: "—",
                                inverted = true,
                                backdrop = alt
                            ),
                            listOf(labels.gapAverage to (average?.let { f.unit.pace(it) } ?: "—"))
                        )
                }
            }
            colourBy("pace", labels.pace, speed) { f.unit.pace(it.toDouble()) }
        } else {
            val factor = if (metric) 3.6f else 2.23694f
            val shown = FloatArray(n) { speed[it] * factor }
            val footer =
                listOf(
                    labels.average to (mean?.let { f.unit.speed(it) } ?: "—"),
                    labels.maximum to (moving.maxOrNull()?.let { f.unit.speed(it.toDouble()) } ?: "—")
                )
            charts[Kind.SPEED] =
                Chart(
                    Kind.SPEED,
                    Series(
                        labels.speed,
                        shown,
                        { formatNumber(it.toDouble(), f.locale, 1, 1) },
                        mean?.let { f.unit.speed(it) } ?: "—",
                        fromZero = true,
                        backdrop = alt
                    ),
                    footer
                )
            colourBy("speed", labels.speed, speed) { f.unit.speed(it.toDouble()) }
        }
    }
    if (hr != null && hr.any { it.isFinite() && it > 0f }) {
        val footer =
            listOfNotNull(
                labels.average to bpm(a.avgHr ?: hr.mean()),
                labels.maximum to bpm(a.maxHr ?: hr.highest()),
                m?.trimp?.let { labels.effort to formatNumber(it.roundToInt().toDouble(), f.locale) }
            )
        charts[Kind.HR] =
            Chart(Kind.HR, Series(labels.hr, hr, { "%.0f".format(it) }, bpm(hr.mean()), backdrop = alt), footer)
        colourBy("hr", labels.hr, hr) { "%.0f bpm".format(it) }
    }
    if (power != null && power.any { it.isFinite() && it > 0f }) {
        val footer =
            buildList {
                add(labels.average to watts(a.avgPower ?: power.mean()))
                add(labels.maximum to watts(power.highest()))
                a.normalizedPower?.let { np ->
                    add(labels.np to watts(np))
                    a.avgPower?.takeIf { it > 0 }?.let {
                        add(labels.vi to formatNumber(np.toDouble() / it, f.locale, 0, 2))
                    }
                }
            }
        charts[Kind.POWER] =
            Chart(
                Kind.POWER,
                Series(labels.power, power, {
                    "%.0f".format(it)
                }, watts(power.mean()), fromZero = true, backdrop = alt),
                footer
            )
        colourBy("power", labels.power, power) { "%.0f W".format(it) }
    }
    if (cad != null && cad.any { it.isFinite() && it > 0f }) {
        val per = if (sport == Sport.RIDE) "rpm" else labels.spm
        val footer =
            listOf(
                labels.average to "${cad.mean().roundToInt()} $per",
                labels.maximum to "${cad.highest().roundToInt()} $per"
            )
        charts[Kind.CADENCE] =
            Chart(
                Kind.CADENCE,
                Series(labels.cadence, cad, { "%.0f".format(it) }, "${cad.mean().roundToInt()} $per", backdrop = alt),
                footer
            )
    }
    if (alt != null && alt.any { it.isFinite() }) {
        val footer =
            listOfNotNull(
                a.ascentM?.let { labels.ascent to f.elevation(it) },
                a.descentM?.let { labels.descent to f.elevation(it) },
                labels.minimum to f.elevation(alt.filter { it.isFinite() }.minOrNull()?.toDouble()),
                labels.maximum to f.elevation(alt.highest().toDouble())
            )
        val label = if (sport == Sport.RUN) labels.elevation else labels.altitude
        charts[Kind.ELEVATION] =
            Chart(
                Kind.ELEVATION,
                Series(label, alt, { "%.0f".format(it) }, f.elevation(alt.highest().toDouble())),
                footer
            )
        colourBy("alt", label, alt) { f.elevation(it.toDouble()) }
    }
    return Drawn(Profile(if (byTime) t else dist, byTime), route, charts, metrics, dist, t, alt, hr, paceSeconds)
}

/** The charts each sport shows, in its order (Strava's), among those its series have. */
fun order(sport: Sport): List<Kind> = when (sport) {
    Sport.RIDE -> listOf(Kind.POWER, Kind.HR, Kind.SPEED, Kind.CADENCE, Kind.ELEVATION)
    Sport.RUN -> listOf(Kind.PACE, Kind.GAP, Kind.HR, Kind.CADENCE, Kind.ELEVATION)
    Sport.STRENGTH -> listOf(Kind.HR)
    else -> listOf(Kind.POWER, Kind.PACE, Kind.SPEED, Kind.GAP, Kind.CADENCE, Kind.ELEVATION, Kind.HR)
}
