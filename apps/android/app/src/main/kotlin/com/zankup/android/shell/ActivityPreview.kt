package com.zankup.android.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.R
import com.zankup.android.api.endpoints.ActivitiesApi
import com.zankup.android.api.model.ActivityDetail
import com.zankup.android.api.model.ActivityStreams
import com.zankup.android.charts.ChartCursor
import com.zankup.android.charts.ChartSection
import com.zankup.android.charts.LineChart
import com.zankup.android.charts.Profile
import com.zankup.android.charts.Series
import com.zankup.android.data.CacheKeys
import com.zankup.android.data.Store
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.domain.UnitFormat
import com.zankup.android.domain.Units
import com.zankup.android.domain.formatDuration
import com.zankup.android.map.MapControls
import com.zankup.android.map.MapMetric
import com.zankup.android.map.Route
import com.zankup.android.map.RouteMap
import com.zankup.android.map.colourRange
import com.zankup.android.map.colourStops
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject

/**
 * The activity until OS-1134 builds it: its map and charts with real data, so the map and the charts
 * (OS-1132, OS-1133) are tried and measured on the phone. The series are kept on the phone (OS-1120).
 */
@HiltViewModel
class ActivityPreviewModel
@Inject
constructor(val store: Store, private val source: ApiSource) : ViewModel() {
    fun detail(id: Long) = store.query(CacheKeys.activity(id), ActivityDetail.serializer()) { api ->
        call { api.service<ActivitiesApi>().getActivity(id.toInt()) }
    }

    suspend fun streams(id: Long): ActivityStreams? {
        store.streams(id, ActivityStreams.serializer())?.let { return it }
        val api = source.api ?: return null
        val fresh =
            call { api.service<ActivitiesApi>().getActivityStreams(id.toInt()) } as? ApiResult.Ok ?: return null
        store.putStreams(id, ActivityStreams.serializer(), fresh.value)
        return fresh.value
    }
}

/** The series' names, from the resources. */
private class Labels(
    val pace: String,
    val speed: String,
    val hr: String,
    val power: String,
    val cadence: String,
    val elevation: String,
    val route: String
)

/** What the charts and the map draw, built once from the series. */
private class Drawn(val profile: Profile, val route: Route, val series: List<Series>, val metrics: List<MapMetric>)

@Composable
fun ActivityPreview(id: Long, onBack: () -> Unit, model: ActivityPreviewModel = hiltViewModel()) {
    val query = remember(id) { model.detail(id) }
    val detail by query.state.collectAsStateWithLifecycle(initialValue = null)
    var streams by remember(id) { mutableStateOf<ActivityStreams?>(null) }
    LaunchedEffect(id) {
        query.refresh()
        streams = model.streams(id)
    }
    val activity = detail?.data
    val units = Units.METRIC
    val format = remember { UnitFormat(units, Locale.getDefault()) }
    val sport = activity?.sport?.let { key -> Sport.entries.firstOrNull { it.key == key } } ?: Sport.OTHER
    val labels =
        Labels(
            stringResource(R.string.series_pace),
            stringResource(R.string.series_speed),
            stringResource(R.string.series_hr),
            stringResource(R.string.series_power),
            stringResource(R.string.series_cadence),
            stringResource(R.string.series_elevation),
            stringResource(R.string.series_route)
        )
    val drawn = remember(streams, sport) { streams?.let { build(it, sport, format, labels) } }
    val cursor = remember(id) { ChartCursor() }
    val section = remember(id) { ChartSection() }
    var selecting by remember { mutableStateOf(false) }
    var metric by remember(drawn) { mutableStateOf(drawn?.metrics?.firstOrNull()) }
    val scroll = rememberScrollState()

    Scaffold(
        containerColor = ZankupTheme.colors.page,
        topBar = { ZankupTopBar(activity?.name ?: stringResource(R.string.nav_activity), onBack = onBack) }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (drawn == null) {
                Skeleton(Modifier.fillMaxWidth().padding(horizontal = 16.dp), height = 300.dp)
                return@Column
            }
            if (drawn.route.hasGps) {
                Box(Modifier.fillMaxWidth().height(300.dp)) {
                    RouteMap(
                        drawn.route,
                        cursor,
                        section,
                        Modifier.fillMaxWidth().height(300.dp),
                        colours = metric?.stops
                    )
                    if (metric != null) MapControls(drawn.route, cursor, drawn.metrics, metric!!) { metric = it }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.preview_section),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Switch(checked = selecting, onCheckedChange = {
                        selecting = it
                        if (!it) section.range = null
                    })
                }
                drawn.series.forEach { series ->
                    ZankupCard {
                        Text(series.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            series.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = ZankupTheme.colors.muted
                        )
                        LineChart(
                            drawn.profile,
                            series,
                            cursor,
                            section,
                            selecting = selecting,
                            unit = if (drawn.profile.byTime) 60f else 1000f,
                            xLabel = { x ->
                                if (drawn.profile.byTime) {
                                    formatDuration(
                                        x.toDouble()
                                    )
                                } else {
                                    format.distance(x.toDouble())
                                }
                            },
                            markLabel = { x ->
                                if (drawn.profile.byTime) {
                                    formatDuration(x.toDouble())
                                } else {
                                    "%.0f %s".format(x / 1000f, format.distanceUnit)
                                }
                            },
                            clip = series.inverted
                        )
                    }
                }
            }
        }
    }
}

private fun FloatArray.average(): Float =
    filter { it.isFinite() }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: Float.NaN

private fun build(s: ActivityStreams, sport: Sport, format: UnitFormat, labels: Labels): Drawn {
    val n = s.t.size
    var last = 0.0
    val dist = FloatArray(n) { i -> (s.dist?.getOrNull(i)?.takeIf { it >= last } ?: last).also { last = it }.toFloat() }
    val byTime = (dist.lastOrNull() ?: 0f) <= 0f
    val t = FloatArray(n) { s.t[it].toFloat() }
    val profile = Profile(if (byTime) t else dist, byTime)
    val alt = s.alt?.let { a -> FloatArray(n) { a.getOrNull(it)?.toFloat() ?: Float.NaN } }
    fun of(values: List<Number?>?) = values?.let { v -> FloatArray(n) { v.getOrNull(it)?.toFloat() ?: Float.NaN } }
    val hr = of(s.hr)
    val power = of(s.power)
    val cad = of(s.cad)
    val speed = of(s.speed)
    val route =
        Route(
            DoubleArray(n) { s.lat?.getOrNull(it) ?: Double.NaN },
            DoubleArray(n) { s.lon?.getOrNull(it) ?: Double.NaN },
            t
        )
    val series = mutableListOf<Series>()
    val metrics = mutableListOf<MapMetric>()

    // A metric the route can be coloured by, with the legend's slow/low end on the left.
    fun colourBy(key: String, label: String, values: FloatArray, end: (Float) -> String) {
        val stops = colourStops(route, values, inverted = false) ?: return
        val (lo, hi) = colourRange(route, values) ?: return
        metrics += MapMetric(key, label, stops, end(lo), end(hi))
    }

    if (speed != null && sport.usesPace) {
        val pace = FloatArray(n) { i -> speed[i].let { if (it.isFinite() && it > 0.5f) 1000f / it else Float.NaN } }
        val average = format.pace(1000.0 / pace.average())
        series += Series(labels.pace, pace, { formatDuration(it.toDouble()) }, average, inverted = true, backdrop = alt)
        colourBy("pace", labels.pace, speed) { format.pace(it.toDouble()) }
    } else if (speed != null) {
        val kmh = FloatArray(n) { speed[it] * 3.6f }
        val average = format.speed(speed.average().toDouble())
        series += Series(labels.speed, kmh, { "%.1f".format(it) }, average, fromZero = true, backdrop = alt)
        colourBy("speed", labels.speed, speed) { format.speed(it.toDouble()) }
    }
    if (hr != null) {
        series += Series(labels.hr, hr, { "%.0f".format(it) }, "%.0f bpm".format(hr.average()), backdrop = alt)
        colourBy("hr", labels.hr, hr) { "%.0f bpm".format(it) }
    }
    if (power != null) {
        val average = "%.0f W".format(power.average())
        series += Series(labels.power, power, { "%.0f".format(it) }, average, fromZero = true, backdrop = alt)
        colourBy("power", labels.power, power) { "%.0f W".format(it) }
    }
    if (cad != null) {
        val average = "%.0f".format(cad.average())
        series += Series(labels.cadence, cad, { "%.0f".format(it) }, average, fromZero = true, backdrop = alt)
    }
    if (alt != null) {
        val highest = format.elevation(alt.filter { it.isFinite() }.maxOrNull()?.toDouble() ?: 0.0)
        series += Series(labels.elevation, alt, { "%.0f".format(it) }, highest)
        colourBy("alt", labels.elevation, alt) { format.elevation(it.toDouble()) }
    }
    metrics.add(0, MapMetric("plain", labels.route, null))
    return Drawn(profile, route, series, metrics)
}
