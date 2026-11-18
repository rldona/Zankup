package com.zankup.android.progress.more

import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.StatsApi
import com.zankup.android.api.model.BestOut
import com.zankup.android.data.Query
import com.zankup.android.data.Store
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupChip
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupSheet
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.domain.Units
import com.zankup.android.network.call
import com.zankup.android.progress.R
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.sportName
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/** Every activity's best at one measure, each measure kept under its own key. */
@HiltViewModel
class BestsModel
@Inject
constructor(private val store: Store) : ViewModel() {
    private val queries = mutableMapOf<String, Query<List<BestOut>>>()

    internal fun bests(key: String): Query<List<BestOut>> = queries.getOrPut(key) {
        store.query("more/bests/$key", ListSerializer(BestOut.serializer())) { api ->
            call { api.service<StatsApi>().getBests(key) }
        }.also { q -> viewModelScope.launch { q.refresh() } }
    }
}

/** A measure's name: 5 km, ½ mile… Power: 30 s, Longest ride. */
@Composable
internal fun bestName(key: String): String = when {
    key.startsWith("power_") ->
        stringResource(R.string.more_best_power, durationLabel(key.removePrefix("power_").toIntOrNull() ?: 0))
    key == "longest" -> stringResource(R.string.more_best_longest)
    else ->
        stringResource(
            when (key) {
                "400m" -> R.string.more_effort_400m
                "half_mile" -> R.string.more_effort_half_mile
                "1k" -> R.string.more_effort_1k
                "1mile" -> R.string.more_effort_1mile
                "2mile" -> R.string.more_effort_2mile
                "5k" -> R.string.more_effort_5k
                "15k" -> R.string.more_effort_15k
                "10mile" -> R.string.more_effort_10mile
                "half_marathon" -> R.string.more_effort_half_marathon
                "marathon" -> R.string.more_effort_marathon
                else -> R.string.more_effort_10k
            }
        )
}

/** A row of the year-grouped list: a year's header, or the [k]th best (newest first). */
private sealed interface ListRowItem {
    data class Header(val year: String) : ListRowItem

    data class Item(val k: Int, val best: BestOut) : ListRowItem
}

private enum class Tab { ANALYSIS, TOP }

private enum class Panel { SPORT, YEAR }

/**
 * Best times, from Progress (bests.tsx): one measure (a run distance, a ride's power over a duration or
 * its distance) with two tabs. Analysis: the record, the second and third; a chart of the bests with
 * their trend, twelve at a time, scrolled sideways; the bests by year below with their year medals.
 * The chart and the list move together. Top 10: the ten best. A best opens its activity.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BestsScreen(key: String, onBack: () -> Unit, onOpenActivity: (Long) -> Unit, model: BestsModel = hiltViewModel()) {
    val colors = ZankupTheme.colors
    val formats = LocalFormats.current
    val imperial = formats.units == Units.IMPERIAL
    var picked by rememberSaveable { mutableStateOf(key.ifEmpty { "10k" }) }
    var tab by rememberSaveable { mutableStateOf(Tab.ANALYSIS) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    // Each answer tagged with its measure, so the last measure's is never taken for the picked one.
    val tagged = remember(picked) { model.bests(picked).state.map { picked to it } }
    val answer by tagged.collectAsStateWithLifecycle(initialValue = null)
    val state = answer?.takeIf { it.first == picked }?.second
    // The last measure stays until the picked one loads: it loads behind, with no skeleton.
    var shown by remember { mutableStateOf<Pair<String, List<BestOut>>?>(null) }
    LaunchedEffect(picked, state?.data) { state?.data?.let { shown = picked to it } }
    val shownKey = shown?.first ?: picked
    val all = shown?.second.orEmpty()
    val loaded = shown != null || state?.failure != null
    val run = isRunKey(picked)
    val keys = (if (run) RUN_KEYS else RIDE_KEYS).filter { effortShown(it, formats.units) }
    val top = remember(shownKey, all) { ranked(shownKey, all) }
    fun value(v: Double) = bestValue(shownKey, v, formats.locale, imperial)
    fun pace(v: Double) = if (isRunKey(shownKey)) {
        "${racePace(v, RUN_METRES[shownKey] ?: 1000.0, imperial)} /${if (imperial) "mi" else "km"}"
    } else {
        null
    }
    val ordered = remember(all) { all.sortedByDescending { it.day } }
    val chrono = remember(ordered) { ordered.reversed() }
    val medals = remember(all, shownKey) { all.associate { it.activityId to yearMedal(shownKey, all, it) } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rows =
        remember(ordered) {
            buildList {
                ordered.forEachIndexed { k, b ->
                    val y = b.day.take(4)
                    if (k == 0 || !ordered[k - 1].day.startsWith(y)) add(ListRowItem.Header(y))
                    add(ListRowItem.Item(k, b))
                }
            }
        }
    val lazyIndex = remember(rows) { rows.mapIndexedNotNull { i, r -> (r as? ListRowItem.Item)?.let { i } } }
    val sync = remember(chrono) { ChartSync(chrono.size) }

    // Another measure: the newest best at the chart's right edge and the list at its top.
    LaunchedEffect(chrono) {
        sync.reset()
        listState.scrollToItem(0)
    }
    // The list drives the chart, unless the chart is the one moving.
    LaunchedEffect(rows, sync) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (i, px) ->
            if (sync.chartDriving || rows.isEmpty()) return@collect
            val row = rows.getOrNull(i) ?: return@collect
            val size = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.takeIf { it > 0 } ?: 1
            val (k, share) =
                when (row) {
                    is ListRowItem.Header -> ((rows.getOrNull(i + 1) as? ListRowItem.Item)?.k ?: 0) to 0f
                    is ListRowItem.Item -> row.k to (px.toFloat() / size).coerceIn(0f, 1f)
                }
            sync.fromList(k, share)
        }
    }
    // The chart drives the list while the finger (or its fling) is on it.
    LaunchedEffect(lazyIndex, sync) {
        snapshotFlow { rightIndex(sync.offset, sync.count) }.collect { right ->
            if (!sync.chartDriving) return@collect
            val k = sync.count - 1 - right
            lazyIndex.getOrNull(k)?.let { listState.scrollToItem(it) }
        }
    }

    Scaffold(containerColor = colors.page, topBar = {
        ZankupTopBar(bestName(picked), onBack = onBack) { InfoAction(stringResource(R.string.more_bests_info)) }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().drawBehind {
                    drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                }
            ) {
                Tab.entries.forEach { t ->
                    val on = t == tab
                    Box(
                        Modifier
                            .weight(1f)
                            .clickable(role = Role.Tab) { tab = t }
                            .drawBehind {
                                if (on) {
                                    val h = 3.dp.toPx()
                                    drawRect(
                                        colors.accent,
                                        Offset(0f, size.height - h),
                                        androidx.compose.ui.geometry.Size(size.width, h)
                                    )
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(if (t == Tab.ANALYSIS) R.string.more_analysis else R.string.more_top10),
                            color = if (on) colors.fg else colors.muted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sport = if (run) Sport.RUN else Sport.RIDE
                IconChip(sportIcon(sport), stringResource(sportName(sport)), selected = true) { panel = Panel.SPORT }
                keys.forEach { k -> ZankupChip(bestName(k), selected = k == picked, onClick = { picked = k }) }
            }

            if (tab == Tab.ANALYSIS && top.isNotEmpty()) {
                Column(Modifier.weight(1f)) {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.more_all_time),
                            color = colors.fg,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Medal(1, 52.dp)
                            Column(Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        value(top[0].value),
                                        color = colors.fg,
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        style = Numbers.tabular
                                    )
                                    pace(top[0].value)?.let {
                                        Text(
                                            it,
                                            color = colors.muted,
                                            fontSize = 15.sp,
                                            modifier = Modifier.padding(bottom = 5.dp)
                                        )
                                    }
                                }
                                Text(longDay(top[0].day), color = colors.muted, fontSize = 13.sp)
                            }
                            top.drop(1).take(2).forEachIndexed { i, b ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Medal(i + 2, 24.dp)
                                    Text(value(b.value), color = colors.fg, fontSize = 13.sp, style = Numbers.tabular)
                                }
                            }
                        }
                    }
                    BestsChart(shownKey, all, chrono, medals, sync, ::value)
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .border(1.25.dp, colors.line, CircleShape)
                                .clickable(role = Role.Button) { panel = Panel.YEAR }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.more_filter_year),
                                color = colors.fg,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            ZankupIcon(
                                ZIcon.Expand,
                                null,
                                tint = colors.fg,
                                size = 22.dp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    BestsList(rows, listState, medals, ::value, ::pace) { onOpenActivity(it.activityId.toLong()) }
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                    when {
                        !loaded -> Skeleton(Modifier.padding(16.dp).fillMaxWidth(), height = 200.dp)
                        top.isEmpty() -> Text(
                            stringResource(R.string.more_no_bests),
                            color = colors.muted,
                            modifier = Modifier.padding(16.dp)
                        )
                        else ->
                            top.take(10).forEach { b ->
                                TopRow(
                                    placeOf(shownKey, all, b),
                                    value(b.value),
                                    listOfNotNull(pace(b.value), longDay(b.day)).joinToString(" · ")
                                ) {
                                    onOpenActivity(b.activityId.toLong())
                                }
                            }
                    }
                }
            }
        }
    }

    when (panel) {
        Panel.SPORT ->
            ZankupSheet(onDismiss = { panel = null }) {
                Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 8.dp)) {
                    SheetTitle(stringResource(R.string.more_filter_sport))
                    listOf(Sport.RUN, Sport.RIDE).forEach { s ->
                        PickRow(sportIcon(s), stringResource(sportName(s)), (s == Sport.RUN) == run) {
                            picked = if (s == Sport.RUN) "10k" else "longest"
                            panel = null
                        }
                    }
                }
            }
        Panel.YEAR ->
            ZankupSheet(onDismiss = { panel = null }) {
                Column(
                    Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    (listOf<String?>(null) + yearsOf(all)).forEach { y ->
                        Text(
                            y ?: stringResource(R.string.more_last_12_months),
                            color = colors.fg,
                            fontSize = 15.sp,
                            modifier =
                            Modifier.fillMaxWidth().clickable(role = Role.Button) {
                                panel = null
                                sync.chartDriving = false
                                val k = if (y ==
                                    null
                                ) {
                                    0
                                } else {
                                    ordered.indexOfFirst { it.day <= "$y-12-31" }.coerceAtLeast(0)
                                }
                                scope.launch {
                                    listState.animateScrollToItem(
                                        if (k ==
                                            0
                                        ) {
                                            0
                                        } else {
                                            lazyIndex.getOrElse(k) { 0 }
                                        }
                                    )
                                }
                            }.padding(vertical = 14.dp)
                        )
                    }
                }
            }
        null -> Unit
    }
}

/** "18 November 2026", or without the year for the rows under their year. */
@Composable
private fun longDay(iso: String, withYear: Boolean = true): String {
    val locale = LocalFormats.current.locale
    val day = runCatching { LocalDate.parse(iso.take(10)) }.getOrNull() ?: return iso
    if (withYear) return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(day)
    return DateTimeFormatter.ofPattern(if (locale.language == "es") "d 'de' MMMM" else "MMMM d", locale).format(day)
}

/**
 * Where the chart is: its left slot ([offset], fractional while it moves) and who moves it, the chart
 * (finger or fling) or the list. [offset] is read only while drawing.
 */
private class ChartSync(val count: Int) {
    private val range = offsetRange(count)
    var offset by mutableFloatStateOf(range.endInclusive)
    var chartDriving = false
    var fling: Job? = null

    fun reset() {
        fling?.cancel()
        chartDriving = false
        offset = range.endInclusive
    }

    fun set(value: Float) {
        offset = value.coerceIn(range.start, range.endInclusive)
    }

    /** The list's top best ([k], newest first) and how far past it, as the chart's right edge. */
    fun fromList(k: Int, share: Float) {
        val right = count - 1 - (k + share)
        set(right - (CHART_SLOTS - 1))
    }
}

/**
 * The bests as Strava's chart: one slot each, oldest to newest, twelve on screen, the better ones
 * higher, the year's medals in their colours, the bests of the year at the right edge filled and the
 * rest hollow, the trend as a smooth line. The scale stays while it scrolls; it follows the finger and
 * keeps going a little after a fling.
 */
@Composable
private fun BestsChart(
    key: String,
    all: List<BestOut>,
    chrono: List<BestOut>,
    medals: Map<Int, Int?>,
    sync: ChartSync,
    label: (Double) -> String
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val n = chrono.size
    val lower = lowerIsBetter(key)
    val (record, worst) = remember(key, all) { chartScale(key, all) }
    val span = abs(worst - record).takeIf { it > 0 } ?: (abs(record) * 0.1).takeIf { it > 0 } ?: 1.0
    val middle = (record + worst) / 2
    val line = remember(chrono) { trend(chrono) }
    val height = 260.dp
    val right by remember(sync) { derivedStateOf { rightIndex(sync.offset, n) } }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 16.dp).height(height)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val axis = with(density) { 70.dp.toPx() }
        val topPx = with(density) { 14.dp.toPx() }
        val base = with(density) { (height - 34.dp).toPx() }
        val slot = (widthPx - axis - with(density) { 8.dp.toPx() }).coerceAtLeast(1f) / CHART_SLOTS
        fun y(v: Double): Float {
            val behind = if (lower) v - record else record - v
            return topPx + (behind / span).toFloat().coerceIn(0f, 1f) * (base - topPx)
        }
        val drag =
            rememberDraggableState { dx -> sync.set(sync.offset - dx / slot) }
        val labelStyle = TextStyle(color = colors.fg, fontSize = 12.sp, fontFeatureSettings = "tnum")
        Canvas(
            Modifier
                .fillMaxSize()
                .draggable(
                    drag,
                    Orientation.Horizontal,
                    onDragStarted = {
                        sync.fling?.cancel()
                        sync.chartDriving = true
                    },
                    onDragStopped = { velocity ->
                        sync.fling =
                            scope.launch {
                                // Dragged right shows earlier bests, as Strava's.
                                animateDecay(
                                    sync.offset,
                                    -velocity / slot,
                                    FloatExponentialDecaySpec(frictionMultiplier = 2f)
                                ) {
                                        v,
                                        _
                                    ->
                                    sync.set(v)
                                }
                                sync.chartDriving = false
                            }
                    }
                )
        ) {
            val hair = 1.dp.toPx()
            listOf(record, middle, worst).forEach { v ->
                drawLine(colors.line, Offset(axis, y(v)), Offset(size.width, y(v)), hair)
                val layout = measurer.measure(label(v), labelStyle)
                drawText(layout, topLeft = Offset(4.dp.toPx(), y(v) - layout.size.height / 2f))
            }
            if (n == 0) return@Canvas
            // Read here only: following the finger redraws this canvas and nothing else.
            val offset = sync.offset
            val rightNow = rightIndex(offset, n)
            val year = chrono[rightNow].day.take(4)
            fun x(i: Int) = axis + 8.dp.toPx() + slot / 2 + i * slot
            clipRect(left = axis) {
                translate(left = -offset * slot) {
                    chrono.indices.forEach { i -> drawLine(colors.line, Offset(x(i), topPx), Offset(x(i), base), hair) }
                    if (n > 1) {
                        val path = Path()
                        chrono.indices.forEach { i ->
                            val px = x(i)
                            val py = y(line[i])
                            if (i == 0) {
                                path.moveTo(px, py)
                            } else {
                                val qx = x(i - 1)
                                val qy = y(line[i - 1])
                                val mid = (qx + px) / 2
                                path.cubicTo(mid, qy, mid, py, px, py)
                            }
                        }
                        drawPath(path, colors.fitness, style = Stroke(2.5.dp.toPx()))
                    }
                    val r = 6.dp.toPx()
                    chrono.forEachIndexed { i, b ->
                        val m = medals[b.activityId]
                        val colour = if (m != null) MEDAL_COLOURS[m - 1] else colors.muted
                        val at = Offset(x(i), y(b.value))
                        if (b.day.startsWith(year)) {
                            drawCircle(colour, r, at)
                        } else {
                            drawCircle(colors.page, r, at)
                            drawCircle(colour, 5.dp.toPx(), at, style = Stroke(2.dp.toPx()))
                        }
                    }
                }
            }
        }
        if (n > 0) {
            val first = chrono[maxOf(0, right - CHART_SLOTS + 1)]
            val last = chrono[right]
            Text(
                "${longDay(first.day)} – ${longDay(last.day)}",
                color = colors.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 78.dp, top = height - 24.dp)
            )
        }
    }
}

/** The bests by year, newest first; each year's header stays on top while its bests scroll under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BestsList(
    rows: List<ListRowItem>,
    state: LazyListState,
    medals: Map<Int, Int?>,
    value: (Double) -> String,
    pace: (Double) -> String?,
    onOpen: (BestOut) -> Unit
) {
    val colors = ZankupTheme.colors
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        rows.forEach { row ->
            when (row) {
                is ListRowItem.Header ->
                    stickyHeader(key = "y${row.year}") {
                        Text(
                            row.year,
                            color = colors.fg,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.fillMaxWidth().background(
                                colors.surface
                            ).padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                is ListRowItem.Item ->
                    item(key = row.best.activityId) {
                        val b = row.best
                        val medal = medals[b.activityId]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onOpen(b) }
                                .drawBehind {
                                    drawLine(
                                        colors.line,
                                        Offset(0f, size.height),
                                        Offset(size.width, size.height),
                                        1.dp.toPx()
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                longDay(b.day, withYear = false),
                                color = colors.fg,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (medal != null) {
                                Row(
                                    Modifier.clip(
                                        RoundedCornerShape(6.dp)
                                    ).background(colors.surface2).padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Medal(medal, 22.dp)
                                    Text(
                                        b.day.take(4),
                                        color = colors.fg,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(value(b.value), color = colors.fg, fontSize = 15.sp, style = Numbers.tabular)
                            pace(b.value)?.let {
                                Text(
                                    it,
                                    color = colors.muted,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.size(width = 82.dp, height = 20.dp)
                                )
                            }
                        }
                    }
            }
        }
        item { Box(Modifier.height(32.dp)) }
    }
}

/** A Top 10 row: the place (a medal for the first three), the value, the pace and day. */
@Composable
private fun TopRow(place: Int, value: String, note: String, onClick: () -> Unit) {
    val colors = ZankupTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(
            role = Role.Button,
            onClick = onClick
        ).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (place <= 3) MEDAL_COLOURS[place - 1].copy(alpha = 0.2f) else colors.surface),
            contentAlignment = Alignment.Center
        ) {
            if (place <= 3) {
                Medal(place, 36.dp)
            } else {
                Text(place.toString(), color = colors.fg, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(value, color = colors.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = Numbers.tabular)
            Text(note, color = colors.muted, fontSize = 13.sp)
        }
        ZankupIcon(ZIcon.Chevron, null, tint = colors.muted, size = 22.dp)
    }
}
