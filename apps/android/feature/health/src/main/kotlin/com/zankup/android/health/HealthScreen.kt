package com.zankup.android.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.HealthDay
import com.zankup.android.api.model.Night
import com.zankup.android.api.model.Value
import com.zankup.android.api.model.WeightOut
import com.zankup.android.designsystem.components.SectionHeader
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupPullToRefresh
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.formatNumber
import com.zankup.android.health.ui.BarSeries
import com.zankup.android.health.ui.BarsLayer
import com.zankup.android.health.ui.CardTitle
import com.zankup.android.health.ui.ErrorLine
import com.zankup.android.health.ui.HealthSkeleton
import com.zankup.android.health.ui.LineLayer
import com.zankup.android.health.ui.Muted
import com.zankup.android.health.ui.SleepTiles
import com.zankup.android.health.ui.SourceIcon
import com.zankup.android.health.ui.StageLanes
import com.zankup.android.health.ui.Tile
import com.zankup.android.health.ui.TrendChart
import com.zankup.android.health.ui.ZankupMark
import com.zankup.android.health.ui.dayText
import com.zankup.android.health.ui.dayTitle
import com.zankup.android.health.ui.fromText
import com.zankup.android.health.ui.laneName
import com.zankup.android.health.ui.longDay
import com.zankup.android.health.ui.one
import com.zankup.android.health.ui.signedWeight
import com.zankup.android.health.ui.weightText
import com.zankup.android.ui.LocalFormats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The Health tab (OS-1150), as the React Native app's: last night's sleep with its stages, the latest
 * day's readiness and figures with their sources, the weight, and the trends of the period chosen
 * (sleep, heart, readiness and form). Sync and settings are in the header with the period. With no
 * data at all it offers [onConnect]. [listState] lets the tab bar scroll it back to the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    onWeight: () -> Unit,
    onSettings: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenActivity: (Long) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    model: HealthModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { model.refresh() }
    val colors = ZankupTheme.colors
    val days by model.days.collectAsStateWithLifecycle()
    val weight by model.weight.collectAsStateWithLifecycle()
    val period by model.period.collectAsStateWithLifecycle()
    val syncing by model.syncing.collectAsStateWithLifecycle()
    val syncFailed by model.syncFailed.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        containerColor = colors.page,
        // The tab bar already holds the navigation inset; keep the content's end gap like Home's.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            ZankupTopBar(stringResource(R.string.health_title)) {
                IconButton(onClick = onSettings) {
                    ZankupIcon(ZIcon.Settings, contentDescription = stringResource(R.string.health_settings))
                }
                PeriodMenu(period, model::setPeriod)
            }
        }
    ) { padding ->
        val direction = LocalLayoutDirection.current
        val spacing = ZankupTheme.spacing
        val side = Modifier.padding(horizontal = spacing.gutter)
        val list = days.data.orEmpty()
        val range = healthRange(period, model.today)
        val lastNight = list.lastOrNull { it.sleep != null }
        val latest = list.lastOrNull()
        val nights = list.filter { it.sleep != null }
        // As Home and Activities: a pull from the top syncs the watch's health again.
        ZankupPullToRefresh(
            refreshing = syncing,
            onRefresh = model::sync,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(direction),
                    end = padding.calculateEndPadding(direction),
                    top = spacing.s,
                    bottom = padding.calculateBottomPadding() + spacing.l
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                if (syncFailed) item("sync-error") { ErrorLine(side) }
                when {
                    days.loading -> item("skeleton") { Box(side) { HealthSkeleton() } }
                    days.data == null -> item("error") { ErrorLine(side) }
                    list.isEmpty() -> item("none") {
                        ZankupCard(side) {
                            Text(stringResource(R.string.health_none), color = colors.fg)
                            Button(onClick = onConnect, Modifier.padding(top = spacing.sm)) {
                                Text(stringResource(R.string.health_connect))
                            }
                        }
                    }
                }
                lastNight?.sleep?.let { sleep ->
                    item("night") { Box(side) { LastNight(model, lastNight, sleep) } }
                }
                latest?.let { day -> item("today") { Box(side) { Today(day, model.today) } } }
                weight.data?.let { w -> item("weight") { Box(side) { WeightCard(w, model.today, onWeight) } } }
                if (list.isNotEmpty()) {
                    val span = healthSpan(list, range)
                    item("trends") { Box(side) { Trends(span, nights, latest) } }
                }
            }
        }
    }
}

@Composable
private fun periodName(period: HealthPeriod) = stringResource(
    when (period) {
        HealthPeriod.D30 -> R.string.health_period_30
        HealthPeriod.D90 -> R.string.health_period_90
        HealthPeriod.D365 -> R.string.health_period_365
    }
)

/** The period: 30, 90 or 365 days. */
@Composable
private fun PeriodMenu(period: HealthPeriod, onChange: (HealthPeriod) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.health_period)
    Box {
        TextButton(onClick = { open = true }, Modifier.semantics { contentDescription = label }) {
            Text(periodName(period))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            HealthPeriod.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(periodName(p)) },
                    onClick = {
                        open = false
                        onChange(p)
                    },
                    trailingIcon = if (p == period) {
                        { ZankupIcon(ZIcon.Check, contentDescription = null) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

private fun sources(v: Value) = HealthSource.of(v.source.value) to v.others.map { HealthSource.of(it.value) }

private fun sources(n: Night) = HealthSource.of(n.source.value) to n.others.map { HealthSource.of(it.value) }

/** A block's heading, with the same gap as the widgets: the list's spacing already separates it. */
@Composable
private fun HealthHeader(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
        color = ZankupTheme.colors.fg
    )
}

/** Last night (or the night of the last day with one): the score and hours, then the stages. */
@Composable
private fun LastNight(model: HealthModel, day: HealthDay, sleep: Night) {
    val date = LocalDate.parse(day.day)
    val night by remember(day.day) { model.night(day.day) }.collectAsStateWithLifecycle()
    val (source, others) = sources(sleep)
    val from = fromText(source, others)
    Column(verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.s)) {
        // The current night is always the last one recorded; the card already dates it. Only an older
        // night needs a heading saying which one it is.
        if (relativeDay(date, model.today) != RelativeDay.TODAY) {
            HealthHeader(stringResource(R.string.health_night_of, longDay(date)))
        }
        SleepTiles(sleep, from) { SourceIcon(source) }
        ZankupCard {
            Box(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val detail = night.data
                    if (detail != null && detail.stages.isNotEmpty()) {
                        StageLanes(detail, sleep)
                    } else {
                        val clock = DateTimeFormatter.ofPattern("HH:mm")
                        val start = nightClock(sleep.startUtc, sleep.utcOffsetS)?.format(clock).orEmpty()
                        val end = nightClock(sleep.endUtc, sleep.utcOffsetS)?.format(clock).orEmpty()
                        Muted("${stringResource(R.string.health_asleep)} · $start–$end", Modifier.padding(end = 28.dp))
                    }
                    Muted(longDay(date))
                }
                // The mark sits on the awake lane's label row, at the right.
                Box(Modifier.align(Alignment.TopEnd).semantics { contentDescription = from }) { SourceIcon(source) }
            }
        }
    }
}

/** The latest day: readiness with what went into it, then each figure with its source. */
@Composable
private fun Today(day: HealthDay, today: LocalDate) {
    val colors = ZankupTheme.colors
    val locale = LocalFormats.current.locale
    val r = day.readiness
    data class Figure(val label: String, val value: String, val source: Pair<HealthSource, List<HealthSource>>)
    val hrv = stringResource(R.string.health_hrv)
    val resting = stringResource(R.string.health_resting_hr)
    val figures = buildList {
        day.hrv?.let { add(Figure(hrv, "${it.value.roundToInt()} ms", sources(it))) }
        day.restingHr?.let { add(Figure(resting, "${it.value.roundToInt()} bpm", sources(it))) }
        day.spo2?.let { add(Figure(stringResource(R.string.health_spo2), "${one(it.value)} %", sources(it))) }
        day.respiration?.let {
            add(Figure(stringResource(R.string.health_respiration), "${one(it.value)} rpm", sources(it)))
        }
        day.steps?.let {
            add(Figure(stringResource(R.string.health_steps), formatNumber(it.value, locale, 0, 0), sources(it)))
        }
    }
    val meaning = r?.let {
        listOfNotNull(
            it.hrv?.let { v -> "$hrv $v" },
            it.restingHr?.let { v -> "$resting $v" },
            it.sleep?.let { v -> "${stringResource(R.string.health_sleep)} $v" },
            it.form?.let { v -> "${stringResource(R.string.health_form)} $v" }
        ).joinToString(" · ")
    } ?: stringResource(R.string.health_no_readiness)
    val zankup = stringResource(R.string.health_zankup_score)
    Column {
        SectionHeader(dayTitle(LocalDate.parse(day.day), today))
        Spacer(Modifier.height(ZankupTheme.spacing.s))
        ZankupCard {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(end = 24.dp)) {
                    Text(
                        stringResource(R.string.health_readiness),
                        color = colors.fg,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        r?.score?.toString() ?: "–",
                        style = Numbers.tabular,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.fg
                    )
                    Muted(meaning)
                }
                Box(Modifier.align(Alignment.TopEnd).semantics { contentDescription = zankup }) { ZankupMark() }
            }
        }
        figures.chunked(2).forEachIndexed { index, pair ->
            // After the full-width readiness card the grid starts at 8 dp; its own rows stay 12 dp apart.
            Spacer(Modifier.height(if (index == 0) ZankupTheme.spacing.s else ZankupTheme.spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { f ->
                    val label = fromText(f.source.first, f.source.second)
                    Tile(
                        f.label,
                        f.value,
                        Modifier.weight(1f),
                        corner = { SourceIcon(f.source.first) },
                        cornerLabel = label,
                        valueSize = 22,
                        upper = true
                    )
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/**
 * The weight: the latest weighing, how it moved in 30 days and the last 90 days as a line. It opens
 * the weight page; with no weighing yet it asks for the first one.
 */
@Composable
private fun WeightCard(weighings: List<WeightOut>, today: LocalDate, onOpen: () -> Unit) {
    val colors = ZankupTheme.colors
    val units = LocalFormats.current.units
    val latest = weighings.lastOrNull()
    val moved = weightChange(weighings, today.minusDays(30))
    val values = remember(weighings, units) {
        dailyWeights(weighings, today.minusDays(90), today).map { v -> v?.let { toUnit(it, units) } }
    }
    val shown = values.filterNotNull()
    val title = stringResource(R.string.health_weight_title)
    val hint = stringResource(R.string.health_weight_open)
    ZankupCard(Modifier.semantics { contentDescription = hint }, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = colors.fg)
            ZankupIcon(ZIcon.Chevron, contentDescription = null, tint = colors.muted)
        }
        if (latest == null) {
            Muted(stringResource(R.string.health_weight_empty))
            return@ZankupCard
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                weightText(latest.weightKg),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                style = Numbers.tabular,
                color = colors.fg
            )
            if (moved != null) {
                Text(
                    stringResource(R.string.health_weight_change30, signedWeight(moved)),
                    Modifier.padding(bottom = 4.dp),
                    fontSize = 13.sp,
                    color = colors.muted
                )
            }
        }
        if (shown.size > 1) {
            TrendChart(
                listOf(
                    LineLayer(
                        title,
                        colors.accent,
                        values,
                        domain = shown.min() - 1 to shown.max() + 1,
                        hideLegend = true
                    )
                ),
                days = values.map { "" },
                description = title,
                height = 200.dp,
                legend = false
            )
        }
    }
}

/** The trends over the period: sleep (with its score), heart, and readiness with the form. */
@Composable
private fun Trends(span: List<Pair<LocalDate, HealthDay?>>, nights: List<HealthDay>, latest: HealthDay?) {
    val colors = ZankupTheme.colors
    val locale = LocalFormats.current.locale
    val labels = remember(span, locale) { span.map { dayText(it.first, "dMMM", locale) } }
    val days = span.map { it.second }
    val format: (Double) -> String = { formatNumber(it, locale, 0, 1) }
    Column(verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.s)) {
        if (nights.size > 1) {
            val last = nights.last().sleep
            val sleepColors = colors.sleep
            val deep = days.map { hours(it?.sleep?.deepS) }
            val rem = days.map { hours(it?.sleep?.remS) }
            val sleep = stringResource(R.string.health_sleep)
            ZankupCard(clip = false) {
                CardTitle(sleep)
                TrendChart(
                    listOf(
                        BarsLayer(
                            listOf(
                                BarSeries(laneName(SleepLane.DEEP), sleepColors.deep, deep),
                                BarSeries(laneName(SleepLane.REM), sleepColors.rem, rem),
                                // A night without stages shows its whole sleep as light, as in the PWA.
                                BarSeries(
                                    laneName(SleepLane.LIGHT),
                                    colors.sleep.light,
                                    days.map { d ->
                                        val s = d?.sleep
                                        if (s?.deepS != null) hours(s.lightS) else hours(s?.asleepS)
                                    }
                                ),
                                BarSeries(
                                    laneName(SleepLane.AWAKE),
                                    colors.sleep.awake,
                                    days.map { hours(it?.sleep?.awakeS) }
                                )
                            )
                        ),
                        LineLayer(
                            stringResource(R.string.health_score),
                            colors.fg,
                            days.map { d -> d?.sleep?.score?.toDouble() },
                            domain = 0.0 to 100.0
                        )
                    ),
                    labels,
                    stringResource(
                        R.string.health_sleep_summary,
                        format(hours(last?.asleepS)),
                        last?.score?.toString() ?: "–"
                    ),
                    format = format
                )
            }
        }
        if (days.count { it != null } > 1) {
            val hrv = stringResource(R.string.health_hrv)
            val resting = stringResource(R.string.health_resting_hr)
            ZankupCard(clip = false) {
                CardTitle(stringResource(R.string.health_heart))
                val lastHrv = latest?.hrv?.value?.roundToInt()?.toString() ?: "–"
                val lastHr = latest?.restingHr?.value?.roundToInt()?.toString() ?: "–"
                TrendChart(
                    listOf(
                        LineLayer("$hrv (ms)", colors.hrv, days.map { it?.hrv?.value }),
                        LineLayer("$resting (bpm)", colors.zones.last(), days.map { it?.restingHr?.value })
                    ),
                    labels,
                    "$hrv $lastHrv ms, $resting $lastHr bpm",
                    format = format
                )
            }
            val form = stringResource(R.string.health_form)
            val readiness = stringResource(R.string.health_readiness)
            ZankupCard(clip = false) {
                CardTitle(
                    stringResource(R.string.health_readiness_and_form),
                    stringResource(R.string.health_readiness_note)
                )
                TrendChart(
                    listOf(
                        BarsLayer(
                            listOf(
                                BarSeries(form, colors.formPositive, days.map { d -> d?.tsb?.takeIf { it >= 0 } }),
                                BarSeries("$form < 0", colors.formNegative, days.map { d -> d?.tsb?.takeIf { it < 0 } })
                            )
                        ),
                        LineLayer(
                            readiness,
                            colors.accent,
                            days.map { d -> d?.readiness?.score?.toDouble() },
                            domain = 0.0 to 100.0
                        )
                    ),
                    labels,
                    latest?.readiness?.let { "$readiness ${it.score}" } ?: stringResource(R.string.health_no_readiness),
                    format = format
                )
            }
        }
    }
}
