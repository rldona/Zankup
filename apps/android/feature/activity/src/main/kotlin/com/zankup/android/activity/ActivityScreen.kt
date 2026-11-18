package com.zankup.android.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.ActivityDetail
import com.zankup.android.charts.ChartCursor
import com.zankup.android.charts.ChartSection
import com.zankup.android.charts.LineChart
import com.zankup.android.charts.ZoneBars
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.domain.Units
import com.zankup.android.domain.formatDuration
import com.zankup.android.map.MapControls
import com.zankup.android.map.MapMetric
import com.zankup.android.map.RouteMap
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.SportBadge
import com.zankup.android.ui.family
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf

/** Where the activity's links go: the app's navigation, which this module does not know. */
class ActivityLinks(
    val back: () -> Unit,
    val open: (Long) -> Unit,
    val analysis: (Long) -> Unit,
    val matches: (Long) -> Unit,
    val fitness: () -> Unit,
    val effort: () -> Unit,
    val goals: () -> Unit,
    val bests: (String?) -> Unit
)

/**
 * An activity (OS-1134): the map, its title and figures, then the blocks of its sport in Strava's
 * order, the charts with their zones and the section, photos, segments, laps, the description and its
 * best efforts. Shown at once from what is kept, refreshed behind.
 */
@Composable
fun ActivityScreen(
    id: Long,
    links: ActivityLinks,
    aiAvailable: Boolean,
    model: ActivityModel = hiltViewModel(key = "activity-$id")
) {
    // Opened during composition: the screen reads its queries right below.
    remember(id) {
        model.open(id)
        id
    }
    val detail by model.detail.state.collectAsStateWithLifecycle(initialValue = null)
    val context by model.activityContext.state.collectAsStateWithLifecycle(initialValue = null)
    val streams by model.streams.collectAsStateWithLifecycle()
    val summary by model.summary.collectAsStateWithLifecycle()
    val a = detail?.data
    val formats = LocalFormats.current
    val sport = sportOf(a?.sport)
    val zoneColours = ZankupTheme.colors.zones
    val labels =
        ChartLabels(
            stringResource(R.string.chart_pace),
            stringResource(R.string.chart_speed),
            stringResource(R.string.chart_gap),
            stringResource(R.string.chart_hr),
            stringResource(R.string.chart_power),
            stringResource(R.string.chart_cadence),
            stringResource(R.string.chart_elevation),
            stringResource(R.string.chart_route)
        )
    val drawn =
        remember(streams, a, context?.data, formats) {
            val s = streams ?: return@remember null
            a?.let { draw(s, it, context?.data, sport, formats, labels, zoneColours) }
        }
    val cursor = remember(id) { ChartCursor() }
    val section = remember(id) { ChartSection() }
    var selecting by rememberSaveable(id) { mutableStateOf(false) }
    var metric by remember(drawn) { mutableStateOf<MapMetric?>(drawn?.metrics?.firstOrNull()) }
    var editing by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    LaunchedEffect(sport) { if (sport == Sport.RIDE) model.refreshPowerCurve() }

    Scaffold(
        containerColor = ZankupTheme.colors.page,
        topBar = {
            ZankupTopBar(a?.name ?: stringResource(sportName(sport)), onBack = links.back) {
                if (a != null) {
                    IconButton(onClick = { editing = true }) { ZankupIcon(ZIcon.Edit, stringResource(R.string.edit)) }
                    IconButton(onClick = { sharing = true }) { ZankupIcon(ZIcon.Share, stringResource(R.string.share)) }
                }
            }
        }
    ) { padding ->
        when {
            a == null && detail?.failure != null ->
                ErrorState(
                    onRetry = model::retry,
                    modifier = Modifier.padding(padding),
                    message = stringResource(R.string.not_found)
                )
            a == null -> Loading(Modifier.padding(padding))
            else ->
                LazyColumn(
                    Modifier.padding(padding).testTag("activity"),
                    verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.sm)
                ) {
                    if (drawn != null && drawn.route.hasGps) {
                        item("map") {
                            Box(Modifier.fillMaxWidth().height(300.dp)) {
                                RouteMap(
                                    drawn.route,
                                    cursor,
                                    section,
                                    Modifier.fillMaxWidth().height(300.dp),
                                    colours = metric?.stops
                                )
                                metric?.let { chosen ->
                                    MapControls(drawn.route, cursor, drawn.metrics, chosen) {
                                        metric =
                                            it
                                    }
                                }
                            }
                        }
                    }
                    item("title") { TitleBlock(a, sport) }
                    item("figures") { FiguresBlock(a, sport) }
                    if (!a.goalsReached.isNullOrEmpty()) item("goal") { GoalReached(links.goals) }
                    if (summary != null || aiAvailable) item("summary") { SummaryCard(summary, model) }
                    contextBlocks(a, context?.data, sport, links)
                    if (sport == Sport.RUN && a.splits.isNotEmpty()) {
                        item("analysis") { AnalysisCard(a, drawn) { links.analysis(id) } }
                        item("splits") { SplitsTable(a) }
                    }
                    if (sport == Sport.STRENGTH && a.sets.isNotEmpty()) item("glance") { ExercisesGlance(a) }
                    if (drawn != null && drawn.charts.isNotEmpty()) {
                        item("profile") {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.profile),
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    stringResource(R.string.section),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ZankupTheme.colors.muted
                                )
                                Switch(
                                    checked = selecting,
                                    onCheckedChange = {
                                        selecting = it
                                        if (!it) section.range = null
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                        if (sport == Sport.RIDE) item("curve") { PowerCurveCard(a, model) }
                        order(sport).mapNotNull { drawn.charts[it] }.forEach { chart ->
                            item(chart.kind.name) {
                                ChartCard(chart, drawn, cursor, section, selecting, a, context?.data)
                            }
                        }
                        item("section-card") { SectionCard(drawn, section, sport, a.hasGps, model) }
                    }
                    if (sport == Sport.STRENGTH && a.sets.isNotEmpty()) item("sets") { SetsBlock(a) }
                    if (a.photoList.isNotEmpty()) item("photos") { Photos(a, model) }
                    if (a.segments.isNotEmpty()) item("segments") { SegmentsBlock(a) }
                    if (a.laps.size > 1) item("laps") { LapsBlock(a, sport) }
                    a.description?.takeIf { it.isNotBlank() }?.let { item("description") { Description(it) } }
                    if (a.bestEfforts.isNotEmpty()) item("bests") { BestEfforts(a) }
                    item("end") { Box(Modifier.height(24.dp)) }
                }
        }
    }
    if (editing && a != null) EditSheet(a, model, onDone = { editing = false }, onDeleted = links.back)
    if (sharing && a != null) ShareSheet(a, model, onDone = { sharing = false })
}

@Composable
private fun Loading(modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Skeleton(Modifier.fillMaxWidth(), height = 280.dp)
        Skeleton(Modifier.fillMaxWidth(0.7f), height = 24.dp)
        Skeleton(Modifier.fillMaxWidth(), height = 120.dp)
        Skeleton(Modifier.fillMaxWidth(), height = 180.dp)
    }
}

/** A card with the page's side margins. */
@Composable
internal fun Block(title: String? = null, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    ZankupCard(Modifier.padding(horizontal = 16.dp), onClick = onClick) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (onClick != null) ZankupIcon(ZIcon.Chevron, null, tint = ZankupTheme.colors.muted)
            }
        }
        content()
    }
}

@Composable
private fun TitleBlock(a: ActivityDetail, sport: Sport) {
    val formats = LocalFormats.current
    val meta =
        listOfNotNull(
            formats.activityDate(a.startUtc, a.timezone),
            a.workoutType,
            a.device,
            a.gear?.name
        ).joinToString(" · ")
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SportBadge(sport.family)
            Text(a.name ?: stringResource(sportName(sport)), style = MaterialTheme.typography.headlineSmall)
        }
        Text(meta, style = MaterialTheme.typography.bodyMedium, color = ZankupTheme.colors.muted)
        a.weather?.let { w ->
            val parts =
                listOfNotNull(
                    w.temperature?.let { "%.0f °C".format(it) },
                    w.condition,
                    w.windSpeed?.let { "%.0f km/h".format(it * 3.6) },
                    w.humidity?.let { "%.0f %%".format(it) }
                )
            if (parts.isNotEmpty()) {
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZankupTheme.colors.muted
                )
            }
        }
    }
}

@Composable
private fun FiguresBlock(a: ActivityDetail, sport: Sport) {
    val formats = LocalFormats.current
    val (main, more) = remember(a, formats) { figures(a, sport.usesPace, formats) }
    var open by rememberSaveable { mutableStateOf(false) }
    val shown = if (open) main + more else main
    Column(Modifier.padding(horizontal = 16.dp)) {
        shown.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                row.forEach { figure ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(figure.label),
                            style = MaterialTheme.typography.labelMedium,
                            color = ZankupTheme.colors.muted
                        )
                        Text(
                            figure.value,
                            style = MaterialTheme.typography.headlineSmall.merge(Numbers.tabular),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
        if (more.isNotEmpty()) {
            TextButton(onClick = { open = !open }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(if (open) R.string.less else R.string.more))
                ZankupIcon(if (open) ZIcon.Collapse else ZIcon.Expand, null)
            }
        }
    }
}

@Composable
private fun ChartCard(
    chart: Chart,
    drawn: Drawn,
    cursor: ChartCursor,
    section: ChartSection,
    selecting: Boolean,
    a: ActivityDetail,
    context: com.zankup.android.api.model.ActivityContext?
) {
    val formats = LocalFormats.current
    val unit = if (formats.units == Units.METRIC) 1000f else 1609.344f
    Block {
        Text(chart.series.label, style = MaterialTheme.typography.titleMedium)
        Text(chart.series.summary, style = MaterialTheme.typography.bodySmall, color = ZankupTheme.colors.muted)
        LineChart(
            drawn.profile,
            chart.series,
            cursor,
            section,
            selecting = selecting,
            unit = if (drawn.profile.byTime) 60f else unit,
            xLabel = { x ->
                if (drawn.profile.byTime) formatDuration(x.toDouble()) else formats.distance(x.toDouble())
            },
            markLabel = { x ->
                if (drawn.profile.byTime) {
                    formatDuration(
                        x.toDouble()
                    )
                } else {
                    "%.0f %s".format(x / unit, formats.unit.distanceUnit)
                }
            },
            clip = chart.series.inverted,
            color = chartColour(chart.kind)
        )
        if (chart.kind == Kind.POWER) context?.power?.let { PowerFigures(it) }
        if (chart.kind == Kind.PACE) PaceFigures(a)
        if (chart.zones.isNotEmpty()) {
            Text(
                stringResource(
                    if (chart.kind ==
                        Kind.POWER
                    ) {
                        R.string.power_zones
                    } else if (chart.kind ==
                        Kind.PACE
                    ) {
                        R.string.pace_zones
                    } else {
                        R.string.hr_zones
                    }
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )
            ZoneBars(chart.zones)
        } else if (chart.kind == Kind.HR && a.metrics?.hrZoneBounds == null) {
            Text(
                stringResource(R.string.no_zones),
                style = MaterialTheme.typography.bodySmall,
                color = ZankupTheme.colors.muted
            )
        }
    }
}

@Composable
private fun chartColour(kind: Kind) = when (kind) {
    Kind.HR -> ZankupTheme.colors.sport.row
    Kind.POWER -> ZankupTheme.colors.sport.strength
    Kind.PACE, Kind.GAP, Kind.SPEED -> ZankupTheme.colors.sport.ride
    Kind.CADENCE -> ZankupTheme.colors.sport.walk
    Kind.ELEVATION -> ZankupTheme.colors.muted
}

internal fun LazyListScope.contextBlocks(
    a: ActivityDetail,
    context: com.zankup.android.api.model.ActivityContext?,
    sport: Sport,
    links: ActivityLinks
) {
    context ?: return
    context.fitness?.let { f -> item("fitness") { FitnessBlock(f, links.fitness) } }
    if (context.matches.size >
        1
    ) {
        item("matches") { MatchesBlock(context.matches, sport) { links.matches(a.id.toLong()) } }
    }
    context.effort?.let { e -> item("effort") { EffortBlock(e, links.effort) } }
    if (context.goals.isNotEmpty()) item("goals") { GoalsBlock(context.goals, links.goals) }
    if (context.medals.isNotEmpty() ||
        context.segments.isNotEmpty()
    ) {
        item("results") { ResultsBlock(context, links.bests) }
    }
}
