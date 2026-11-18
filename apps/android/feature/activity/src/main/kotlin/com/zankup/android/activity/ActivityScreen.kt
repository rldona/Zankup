package com.zankup.android.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.ActivityContext
import com.zankup.android.api.model.ActivityDetail
import com.zankup.android.charts.ChartCursor
import com.zankup.android.charts.ChartSection
import com.zankup.android.charts.LineChart
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.color
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.domain.Units
import com.zankup.android.domain.formatDuration
import com.zankup.android.map.MapControls
import com.zankup.android.map.MapMetric
import com.zankup.android.map.RouteMap
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.SportTile
import com.zankup.android.ui.body.MuscleCover
import com.zankup.android.ui.body.musclesFor
import com.zankup.android.ui.body.musclesWorked
import com.zankup.android.ui.family
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf
import kotlin.math.roundToInt

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

/** The header's height under the status bar, and the map's own height under the header. */
private val HEADER = 56.dp
private val MAP = 300.dp

/**
 * An activity (OS-1134), laid out as the React Native app's: the map from the very top under a
 * see-through header with round buttons (it fills in, with the title, as the map scrolls away), its
 * title and figures, then the blocks of its sport in Strava's order. Shown at once from what is kept,
 * refreshed behind.
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
    val labels =
        ChartLabels(
            pace = stringResource(R.string.chart_pace),
            speed = stringResource(R.string.chart_speed),
            gap = stringResource(R.string.chart_gap),
            hr = stringResource(R.string.chart_hr),
            power = stringResource(R.string.chart_power),
            cadence = stringResource(R.string.chart_cadence),
            elevation = stringResource(R.string.chart_elevation),
            altitude = stringResource(R.string.chart_altitude),
            average = stringResource(R.string.chart_avg),
            maximum = stringResource(R.string.chart_max),
            minimum = stringResource(R.string.chart_min),
            bestPace = stringResource(R.string.chart_best_pace),
            gapShort = stringResource(R.string.chart_gap_short),
            gapAverage = stringResource(R.string.chart_gap_avg),
            ascent = stringResource(R.string.chart_ascent),
            descent = stringResource(R.string.chart_descent),
            effort = stringResource(R.string.chart_effort),
            np = stringResource(R.string.chart_np),
            vi = stringResource(R.string.chart_vi),
            spm = stringResource(R.string.chart_spm)
        )
    val drawn =
        remember(streams, a, context?.data, formats) {
            val s = streams ?: return@remember null
            a?.let { draw(s, it, context?.data, sport, formats, labels) }
        }
    val cursor = remember(id) { ChartCursor() }
    val section = remember(id) { ChartSection() }
    var selecting by rememberSaveable(id) { mutableStateOf(false) }
    // Coloured by the first metric (pace or speed) from the start, as the React Native app.
    var metric by remember(drawn) { mutableStateOf<MapMetric?>(drawn?.metrics?.firstOrNull()) }
    var editing by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    LaunchedEffect(sport) { if (sport == Sport.RIDE) model.refreshPowerCurve() }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val header = top + HEADER
    val routeMap = drawn != null && drawn.route.hasGps
    // A strength workout without a route has its body map for a cover, as a route has its map: the
    // watch's exercises, else the kind of workout chosen for it.
    val muscles = remember(a?.sets, a?.workoutType) {
        a?.let { musclesFor(musclesWorked(it.sets), it.workoutType) }.orEmpty()
    }
    val muscleCover = a != null && !a.hasGps && !routeMap && muscles.isNotEmpty()
    val hasMap = routeMap || muscleCover
    val list = rememberLazyListState()
    val density = LocalDensity.current
    // The header fills in over the last 48 dp before the map leaves it.
    val solid by remember(hasMap, header) {
        derivedStateOf {
            if (!hasMap) return@derivedStateOf 1f
            if (list.firstVisibleItemIndex > 0) return@derivedStateOf 1f
            val end = with(density) { MAP.toPx() }
            val fade = with(density) { 48.dp.toPx() }
            ((list.firstVisibleItemScrollOffset - (end - fade)) / fade).coerceIn(0f, 1f)
        }
    }

    Box(Modifier.fillMaxSize().background(ZankupTheme.colors.page)) {
        when {
            a == null && detail?.failure != null ->
                ErrorState(
                    onRetry = model::retry,
                    modifier = Modifier.padding(top = header),
                    message = stringResource(R.string.not_found)
                )
            a == null -> Loading(Modifier.padding(top = header))
            else ->
                LazyColumn(
                    Modifier.testTag("activity"),
                    state = list,
                    contentPadding = PaddingValues(top = if (hasMap) 0.dp else header, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (muscleCover) {
                        item("cover") { MuscleCover(muscles, top = header, height = MAP + header) }
                    }
                    if (routeMap) {
                        item("map") {
                            Box(Modifier.fillMaxWidth().height(MAP + header)) {
                                RouteMap(
                                    drawn!!.route,
                                    cursor,
                                    section,
                                    Modifier.fillMaxWidth().height(MAP + header),
                                    colours = metric?.stops,
                                    colour = sport.family.color
                                )
                                metric?.let { chosen ->
                                    MapControls(drawn.route, cursor, drawn.metrics, chosen, top = header) {
                                        metric = it
                                    }
                                }
                            }
                        }
                    }
                    item("title") { TitleBlock(a, sport) }
                    item("figures") { FiguresBlock(a, sport) }
                    if (!a.goalsReached.isNullOrEmpty()) item("goal") { GoalReached(links.goals) }
                    if (summary != null || aiAvailable) item("summary") { SummaryCard(summary, aiAvailable, model) }
                    sportBlocks(a, context?.data, sport, links, drawn, model, cursor, section, selecting) { on ->
                        selecting = on
                        if (!on) section.range = null
                    }
                    a.description?.takeIf { it.isNotBlank() }?.let { item("description") { Description(it) } }
                    if (a.bestEfforts.isNotEmpty()) item("bests") { BestEfforts(a) }
                }
        }
        Header(
            title = a?.name ?: stringResource(sportName(sport)),
            top = top,
            solid = solid,
            onBack = links.back,
            onEdit = if (a != null) ({ editing = true }) else null,
            onShare = if (a != null) ({ sharing = true }) else null
        )
    }
    if (editing && a != null) EditSheet(a, model, onDone = { editing = false }, onDeleted = links.back)
    if (sharing && a != null) ShareSheet(a, model, onDone = { sharing = false })
}

/**
 * The header over the map: back, the title (only once filled in) and edit and share, each on a round
 * background while the header is see-through so it reads over the map.
 */
@Composable
private fun Header(
    title: String,
    top: Dp,
    solid: Float,
    onBack: () -> Unit,
    onEdit: (() -> Unit)?,
    onShare: (() -> Unit)?
) {
    val colors = ZankupTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(colors.page.copy(alpha = solid)) }
            .padding(top = top)
            .height(HEADER)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeaderButton(ZIcon.Back, stringResource(com.zankup.android.designsystem.R.string.back), 1f - solid, onBack)
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).alpha(solid)
        )
        onEdit?.let { HeaderButton(ZIcon.Edit, stringResource(R.string.edit), 1f - solid, it) }
        onShare?.let { HeaderButton(ZIcon.Share, stringResource(R.string.share), 1f - solid, it) }
    }
}

@Composable
private fun HeaderButton(icon: ZIcon, label: String, round: Float, onClick: () -> Unit) {
    val colors = ZankupTheme.colors
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).drawBehind {
            if (round > 0f) {
                drawCircle(colors.surface.copy(alpha = round))
                drawCircle(
                    colors.line.copy(alpha = round),
                    radius = size.minDimension / 2 - 0.5.dp.toPx(),
                    style = Stroke(1.dp.toPx())
                )
            }
        }
    ) { ZankupIcon(icon, label, tint = colors.fg) }
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

/** "Sunny, 18°, wind 12 km/h", as the React Native app writes the weather. */
@Composable
private fun weatherLine(a: ActivityDetail): String? {
    val w = a.weather ?: return null
    val imperial = LocalFormats.current.units == Units.IMPERIAL
    val bits =
        listOfNotNull(
            w.condition,
            w.temperature?.let { "${(if (imperial) it * 1.8 + 32 else it).roundToInt()}°" },
            w.windSpeed?.let { "${stringResource(R.string.wind)} ${(it * 3.6).roundToInt()} km/h" }
        )
    return bits.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

@Composable
private fun TitleBlock(a: ActivityDetail, sport: Sport) {
    val formats = LocalFormats.current
    val colors = ZankupTheme.colors
    val name = a.name ?: stringResource(sportName(sport))
    val meta =
        listOfNotNull(
            formats.activityDate(a.startUtc, a.timezone),
            a.workoutType?.let { workoutName(it) }?.takeIf { it != name },
            a.device,
            a.gear?.name
        ).joinToString(" · ")
    Row(
        Modifier.padding(horizontal = 16.dp).padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SportTile(sport.family, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, color = colors.muted, style = MaterialTheme.typography.bodyMedium)
            weatherLine(a)?.let { Text(it, color = colors.muted, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** The figures in two centred columns, label over value, read down each column; the rest behind "More". */
@Composable
private fun FiguresBlock(a: ActivityDetail, sport: Sport) {
    val formats = LocalFormats.current
    val colors = ZankupTheme.colors
    val (main, more) = remember(a, formats) { figures(a, sport.usesPace, formats) }
    var open by rememberSaveable { mutableStateOf(false) }

    @Composable
    fun Grid(items: List<Figure>) {
        val half = (items.size + 1) / 2
        Row(Modifier.fillMaxWidth()) {
            listOf(items.take(half), items.drop(half)).forEach { column ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    column.forEach { figure ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(figure.label), fontSize = 11.sp, color = colors.muted)
                            Text(figure.value, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = Numbers.tabular)
                        }
                    }
                }
            }
        }
    }
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Grid(main)
        if (open && more.isNotEmpty()) Grid(more)
        if (more.isNotEmpty()) {
            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { open = !open }
                    .semantics { stateDescription = if (open) "expanded" else "collapsed" }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(if (open) R.string.less else R.string.more),
                    color = colors.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                ZankupIcon(if (open) ZIcon.Collapse else ZIcon.Expand, null, tint = colors.accent, size = 20.dp)
            }
        }
    }
}

/** A chart's colour, as Strava's: the kinds' zone colours, cadence in zone 4's orange, the elevation grey. */
@Composable
private fun chartColour(kind: Kind) = when (kind) {
    Kind.HR -> HR_SCALE[4]
    Kind.POWER -> POWER_SCALE[4]
    Kind.PACE, Kind.GAP, Kind.SPEED -> PACE_SCALE[4]
    Kind.CADENCE -> ZankupTheme.colors.zones[3]
    Kind.ELEVATION -> ZankupTheme.colors.muted
}

/** One chart on a card of its own: its title, the chart and its figures centred under it. */
@Composable
private fun ChartCard(
    chart: Chart,
    drawn: Drawn,
    cursor: ChartCursor,
    section: ChartSection,
    selecting: Boolean,
    extra: List<Pair<String, String>>
) {
    val formats = LocalFormats.current
    val unit = if (formats.units == Units.METRIC) 1000f else 1609.344f
    val colors = ZankupTheme.colors
    ZankupCard(Modifier.padding(horizontal = 16.dp)) {
        CardTitle(chart.series.label, Modifier.padding(bottom = 24.dp))
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
            // Every chart scaled as the React Native app's: outliers trimmed, four round steps.
            clip = true,
            color = chartColour(chart.kind),
            height = 170.dp,
            filled = true
        )
        val footer = chart.footer + extra
        if (footer.isNotEmpty()) {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                footer.forEach { (label, value) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, fontSize = 11.sp, color = colors.muted)
                        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, style = Numbers.tabular)
                    }
                }
            }
        }
    }
}

/**
 * The blocks of each sport, in the React Native app's (Strava's) order: a run's context, analysis and
 * splits before its charts; a ride's power curve and zones round its power chart; a strength workout's
 * exercises first and its sets after.
 */
private fun LazyListScope.sportBlocks(
    a: ActivityDetail,
    context: ActivityContext?,
    sport: Sport,
    links: ActivityLinks,
    drawn: Drawn?,
    model: ActivityModel,
    cursor: ChartCursor,
    section: ChartSection,
    selecting: Boolean,
    onSelecting: (Boolean) -> Unit
) {
    val charts = drawn?.charts.orEmpty()
    val hasCharts = drawn != null && drawn.profile.size > 1 && charts.isNotEmpty()

    fun chart(kind: Kind) {
        val found = charts[kind] ?: return
        item(kind.name) {
            val extra =
                when {
                    kind == Kind.PACE && sport == Sport.RUN -> runExtra(a)
                    kind == Kind.POWER -> powerExtra(context)
                    else -> emptyList()
                }
            ChartCard(found, drawn!!, cursor, section, selecting, extra)
        }
    }
    fun heading() {
        if (hasCharts) item("profile") { ProfileHeading(selecting, onSelecting) }
    }
    fun sectionCard() {
        if (drawn != null) item("section-card") { SectionCard(drawn, section, sport, a.hasGps, model) }
    }
    fun hrZones() {
        if (charts.containsKey(Kind.HR)) item("hr-zones") { HrZonesCard(a) }
    }
    fun photos() {
        if (a.photoList.isNotEmpty()) item("photos") { Photos(a, model) }
    }
    fun routeAndSegments() {
        if (a.routeGroup != null) item("route") { RouteRank(a) }
        if (a.segments.isNotEmpty()) item("segments") { SegmentsBlock(a) }
    }
    fun laps() {
        if (a.laps.size > 1) item("laps") { LapsBlock(a, sport) }
    }
    fun insights(ride: Boolean) {
        context ?: return
        context.fitness?.let { f -> item("fitness") { FitnessBlock(f, links.fitness) } }
        if (context.matches.size > 1 && context.matches.any { it.`this` }) {
            item("matches") { MatchesBlock(context.matches, sport) { links.matches(a.id.toLong()) } }
        }
        context.effort?.let { e -> item("effort") { EffortBlock(e, links.effort) } }
        if (context.goals.isNotEmpty()) item("goals") { GoalsBlock(context.goals, links.goals) }
        if (context.medals.isNotEmpty() || context.segments.isNotEmpty()) {
            item("results") { ResultsBlock(context, ride, links.bests) }
        }
    }
    fun powerZones() {
        val m = a.metrics
        val z = m?.powerZones
        val b = m?.powerZoneBounds
        if (!z.isNullOrEmpty() && !b.isNullOrEmpty()) {
            item("power-zones") { ZonesCard(stringResource(R.string.power_zones), z, b, POWER_SCALE, "W") }
        }
    }

    when (sport) {
        Sport.RIDE -> {
            insights(ride = true)
            heading()
            item("curve") { PowerCurveCard(a, model) }
            chart(Kind.POWER)
            powerZones()
            chart(Kind.HR)
            hrZones()
            chart(Kind.SPEED)
            chart(Kind.CADENCE)
            chart(Kind.ELEVATION)
            sectionCard()
            photos()
            routeAndSegments()
            laps()
        }
        Sport.RUN -> {
            insights(ride = false)
            if (a.splits.size > 1) {
                item("analysis") { AnalysisCard(a, drawn) { links.analysis(a.id.toLong()) } }
                item("splits") { SplitsTable(a) }
            }
            heading()
            chart(Kind.PACE)
            chart(Kind.GAP)
            val paceZones = context?.paceZones
            val seconds = drawn?.paceSeconds
            if (paceZones != null && seconds != null) {
                item("pace-zones") { PaceZonesCard(seconds, paceZones.thresholdMps, paceZones.bounds) }
            }
            chart(Kind.HR)
            hrZones()
            chart(Kind.CADENCE)
            chart(Kind.ELEVATION)
            sectionCard()
            photos()
            routeAndSegments()
            laps()
        }
        Sport.STRENGTH -> {
            if (a.sets.isNotEmpty()) item("glance") { ExercisesGlance(a) }
            context?.effort?.let { e -> item("effort") { EffortBlock(e, links.effort) } }
            if (!context?.goals.isNullOrEmpty()) item("goals") { GoalsBlock(context!!.goals, links.goals) }
            heading()
            chart(Kind.HR)
            hrZones()
            sectionCard()
            if (a.sets.isNotEmpty()) item("sets") { SetsBlock(a) }
            photos()
            laps()
        }
        else -> {
            if (hasCharts) {
                heading()
                order(sport).forEach { chart(it) }
                sectionCard()
            }
            photos()
            hrZones()
            if (a.sets.isNotEmpty()) item("sets") { SetsBlock(a) }
            routeAndSegments()
            powerZones()
            laps()
        }
    }
}

/** A run's pace card also shows its moving and elapsed time and its fastest split. */
@Composable
private fun runExtra(a: ActivityDetail): List<Pair<String, String>> {
    val formats = LocalFormats.current
    val fastest = a.splits.filter { it.timeS > 0 }.maxOfOrNull { it.distanceM / it.timeS }
    return listOfNotNull(
        stringResource(R.string.fig_moving) to formats.duration(a.movingS ?: a.durationS),
        stringResource(R.string.elapsed_time) to formats.duration(a.durationS),
        fastest?.let { stringResource(R.string.fastest_split) to formats.unit.pace(it) }
    )
}

/** A ride's power card also shows its work, intensity and training load (the server's). */
@Composable
private fun powerExtra(context: ActivityContext?): List<Pair<String, String>> {
    val power = context?.power ?: return emptyList()
    val locale = LocalFormats.current.locale
    return listOfNotNull(
        stringResource(R.string.work) to
            "${com.zankup.android.domain.formatNumber(power.workKj.roundToInt().toDouble(), locale)} kJ",
        power.intensity?.let {
            stringResource(R.string.intensity) to
                com.zankup.android.domain.formatNumber(it, locale, 0, 2)
        },
        power.load?.let { stringResource(R.string.load) to "${it.roundToInt()}" }
    )
}

/** "Profile" and the "Section" chip that turns picking a section on the charts on and off. */
@Composable
private fun ProfileHeading(selecting: Boolean, onSelecting: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.profile),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selecting,
            onClick = { onSelecting(!selecting) },
            label = { Text(stringResource(R.string.section), fontWeight = FontWeight.SemiBold) },
            shape = RoundedCornerShape(50),
            border = null,
            colors =
            FilterChipDefaults.filterChipColors(
                containerColor = ZankupTheme.colors.surface2,
                selectedContainerColor = ZankupTheme.colors.accent,
                selectedLabelColor = ZankupTheme.colors.accentFg
            )
        )
    }
}
