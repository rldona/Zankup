package com.zankup.android.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.Bucket
import com.zankup.android.charts.ChartCursor
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupSheet
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.progress.logic.DayRange
import com.zankup.android.progress.logic.Glance
import com.zankup.android.progress.logic.ProgressRange
import com.zankup.android.progress.logic.VolumeFormat
import com.zankup.android.progress.logic.VolumeMetric
import com.zankup.android.progress.logic.bucketValue
import com.zankup.android.progress.logic.comparableYears
import com.zankup.android.progress.logic.day
import com.zankup.android.progress.logic.glance
import com.zankup.android.progress.logic.metricsFor
import com.zankup.android.progress.logic.mondayOf
import com.zankup.android.progress.logic.percentChange
import com.zankup.android.progress.logic.rangeLabels
import com.zankup.android.progress.logic.sportsIn
import com.zankup.android.progress.logic.volumeRange
import com.zankup.android.progress.logic.weekTitle
import com.zankup.android.progress.logic.yearsBack
import com.zankup.android.progress.ui.BigFigure
import com.zankup.android.progress.ui.BucketSheet
import com.zankup.android.progress.ui.ChartSkeleton
import com.zankup.android.progress.ui.DatedActivity
import com.zankup.android.progress.ui.Dot
import com.zankup.android.progress.ui.Figure
import com.zankup.android.progress.ui.InfoButton
import com.zankup.android.progress.ui.Muted
import com.zankup.android.progress.ui.OutlineButton
import com.zankup.android.progress.ui.ProgressChip
import com.zankup.android.progress.ui.RadioRow
import com.zankup.android.progress.ui.SheetTitle
import com.zankup.android.progress.ui.SportSheet
import com.zankup.android.progress.ui.VolumeChart
import com.zankup.android.progress.ui.date
import com.zankup.android.progress.ui.longDate
import com.zankup.android.progress.ui.metricShort
import com.zankup.android.progress.ui.metricTotal
import com.zankup.android.progress.ui.periodWords
import com.zankup.android.progress.ui.rangeName
import com.zankup.android.progress.ui.sportIcon
import com.zankup.android.progress.ui.sportLabel
import com.zankup.android.progress.ui.versus
import com.zankup.android.progress.ui.volumeFormat
import com.zankup.android.progress.ui.yearsLabel
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.sportOf
import kotlin.math.abs

private enum class DetailSheet {
    SPORT,
    COMPARE,
    BUCKET
}

/**
 * Progress in detail, from Volume's card (as Strava's): a sport (a sheet with a search), what to
 * measure, a range from 7 days to a year, its total against the span before or the same span some
 * years back, the chart and a sentence on how it went. Touching the chart picks a day or week; its box
 * opens what was done in it. [sport] is the API's key ("run"), or null for all sports.
 */
@Composable
fun ProgressDetailScreen(
    sport: String?,
    onBack: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier,
    model: ProgressDetailModel = hiltViewModel(key = "progress-detail-${sport.orEmpty()}")
) {
    LaunchedEffect(Unit) { model.refresh() }
    val colors = ZankupTheme.colors
    val format = volumeFormat()
    val locale = LocalFormats.current.locale
    val today = model.today
    var picked by rememberSaveable { mutableStateOf(sport?.let(::sportOf)?.key) }
    val chosen = picked?.let(::sportOf)
    var metric by rememberSaveable { mutableStateOf(metricsFor(chosen).first()) }
    var range by rememberSaveable { mutableStateOf(ProgressRange.M3) }
    var compare by rememberSaveable { mutableStateOf<Int?>(null) }
    var sheet by remember { mutableStateOf<DetailSheet?>(null) }
    val cursor = remember(range, metric, picked, compare) { ChartCursor() }

    val span = volumeRange(range, today)
    val against = compare?.let { yearsBack(span.current, it) } ?: span.previous
    val now by remember(span) { model.queries.volume(span.current, span.byDay) }.collectAsStateWithLifecycle()
    val before by remember(against, span.byDay) {
        model.queries.volume(against, span.byDay)
    }.collectAsStateWithLifecycle()
    val streak by model.streak.collectAsStateWithLifecycle()
    val buckets = now.data.orEmpty()
    val earlier = before.data.orEmpty()
    val values = buckets.map { bucketValue(it, metric, chosen) }
    val previousValues = earlier.map { bucketValue(it, metric, chosen) }
    val total = values.sum()
    val previousTotal = previousValues.sum()
    val pct = percentChange(total, previousTotal)
    val metrics = metricsFor(chosen) + VolumeMetric.COUNT
    val years = comparableYears(span.current, day(streak.data?.firstActivity))
    val dayFormat = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    fun periodOf(b: Bucket): String {
        val start = day(b.start) ?: return b.start
        return if (span.byDay) dayFormat.format(start) else weekTitle(start, locale)
    }

    Scaffold(
        modifier = modifier,
        containerColor = colors.page,
        topBar = {
            ZankupTopBar(stringResource(R.string.progress_nav_progress), onBack = onBack) {
                InfoButton(stringResource(R.string.progress_volume_info))
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressChip(sportLabel(chosen), on = true, icon = sportIcon(chosen), onClick = {
                    sheet =
                        DetailSheet.SPORT
                })
                metrics.forEach { m ->
                    val label = if (m ==
                        VolumeMetric.ASCENT
                    ) {
                        stringResource(R.string.progress_metric_long_ascent)
                    } else {
                        metricShort(m)
                    }
                    ProgressChip(label, on = m == metric, onClick = { metric = m })
                }
            }
            Text(metricTotal(metric), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.fg)
            if (compare != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BigFigure(format.value(metric, total, 1), size = 22)
                            ChangeText(pct)
                        }
                        SpanLine(colors.accent, span.current)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        BigFigure(format.value(metric, previousTotal, 1), size = 22)
                        SpanLine(colors.fatigue, against)
                    }
                }
            } else {
                val end = if (span.byDay || range == ProgressRange.YTD) {
                    stringResource(R.string.progress_today)
                } else {
                    longDate(mondayOf(today).plusDays(6))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(Modifier.weight(1f, fill = false)) {
                        BigFigure(format.value(metric, total, 1))
                        Muted("${longDate(span.current.from)} - $end", style = MaterialTheme.typography.bodySmall)
                    }
                    if (pct != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            ChangeText(pct)
                            Muted(versus(range), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (now.loading) {
                ChartSkeleton(260)
            } else {
                PickedBox(cursor, buckets.size) { index ->
                    val bucket = buckets[index]
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface2)
                            .border(1.dp, colors.line, RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) { sheet = DetailSheet.BUCKET }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Figure(format.value(metric, bucketValue(bucket, metric, chosen), 1))
                            Muted(periodOf(bucket), style = MaterialTheme.typography.bodySmall)
                        }
                        ZankupIcon(ZIcon.Chevron, stringResource(R.string.progress_week_detail), tint = colors.muted)
                    }
                }
                VolumeChart(
                    values = values,
                    previous = if (compare != null) previousValues else null,
                    ticks = format.ticks(
                        (values + if (compare != null) previousValues else emptyList()).maxOrNull() ?: 0.0,
                        metric,
                        3
                    ),
                    tickLabel = { format.tick(metric, it) },
                    labels = rangeLabels(range, buckets.mapNotNull { day(it.start) }, locale),
                    description = "${metricTotal(metric)}: ${format.value(metric, total, 1)}",
                    cursor = cursor,
                    height = 260
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProgressRange.entries.forEach { r ->
                    val on = r == range
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (on) colors.accent else colors.page)
                            .clickable(role = Role.RadioButton) { range = r }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            rangeName(r),
                            color = if (on) colors.accentFg else colors.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            OutlineButton(onClick = { sheet = DetailSheet.COMPARE }) {
                Box(Modifier.width(22.dp).height(5.dp).clip(CircleShape).background(colors.fatigue))
                Text(
                    compare?.let { yearsLabel(it) } ?: stringResource(R.string.progress_compare),
                    color = colors.fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (compare != null) {
                    Box(Modifier.clip(CircleShape).clickable { compare = null }) {
                        ZankupIcon(
                            ZIcon.Close,
                            stringResource(R.string.progress_clear_compare),
                            tint = colors.fg,
                            size = 22.dp
                        )
                    }
                } else {
                    ZankupIcon(ZIcon.Expand, contentDescription = null, tint = colors.fg, size = 22.dp)
                }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ZankupIcon(sportIcon(chosen), contentDescription = null, tint = colors.fg, size = 22.dp)
                    Text(stringResource(R.string.progress_glance_title), fontSize = 14.sp, color = colors.fg)
                }
                Text(
                    glanceText(
                        glance(compare, total, previousTotal),
                        range,
                        compare,
                        total,
                        previousTotal,
                        metric,
                        format
                    ),
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                    color = colors.fg
                )
            }
        }
    }

    when (sheet) {
        DetailSheet.SPORT -> ZankupSheet(onDismiss = { sheet = null }) {
            SportSheet(chosen, sportsIn(buckets + earlier)) { s ->
                picked = s?.key
                if (metric != VolumeMetric.COUNT && metric !in metricsFor(s)) metric = metricsFor(s).first()
                sheet = null
            }
        }
        DetailSheet.COMPARE -> ZankupSheet(onDismiss = { sheet = null }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
                SheetTitle(stringResource(R.string.progress_compare_with), Modifier.padding(bottom = 8.dp))
                (listOf<Int?>(null) + years).forEach { k ->
                    val note = k?.let {
                        val back = yearsBack(span.current, it)
                        "${longDate(back.from)} - ${longDate(back.to.minusDays(1))}"
                    }
                    RadioRow(
                        k?.let { yearsLabel(it) } ?: stringResource(R.string.progress_compare_none),
                        on = compare == k,
                        onClick = {
                            compare = k
                            sheet = null
                        },
                        note = note
                    )
                }
            }
        }
        DetailSheet.BUCKET -> {
            val bucket = buckets.getOrNull(cursor.index)
            val start = bucket?.let { day(it.start) }
            if (bucket == null || start == null) {
                LaunchedEffect(Unit) { sheet = null }
            } else {
                ZankupSheet(onDismiss = { sheet = null }) {
                    val range = DayRange(start, start.plusDays(if (span.byDay) 1 else 7))
                    val calendar by remember(range) { model.queries.calendar(range) }.collectAsStateWithLifecycle()
                    val activities = calendar.data.orEmpty()
                        .flatMap { d -> day(d.day)?.let { at -> d.activities.map { DatedActivity(at, it) } }.orEmpty() }
                        .filter { chosen == null || sportOf(it.activity.sport) == chosen }
                        .reversed()
                    BucketSheet(periodOf(bucket), activities) { id ->
                        sheet = null
                        onOpenActivity(id)
                    }
                }
            }
        }
        null -> Unit
    }
}

/** The picked bucket's box over the chart: the only part that recomposes as the finger moves. */
@Composable
private fun PickedBox(cursor: ChartCursor, count: Int, content: @Composable (Int) -> Unit) {
    val index = cursor.index
    Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
        if (index in 0 until count) content(index)
    }
}

/** ▲ 12 % in green, ▼ 4 %. */
@Composable
private fun ChangeText(pct: Int?) {
    pct ?: return
    val arrow = when {
        pct > 0 -> "▲"
        pct < 0 -> "▼"
        else -> ""
    }
    Text(
        "$arrow ${abs(pct)} %",
        color = if (pct > 0) ZankupTheme.colors.formPositive else ZankupTheme.colors.fg,
        fontSize = 17.sp,
        fontWeight = FontWeight.ExtraBold
    )
}

/** A span's dates beside the dot of its line. */
@Composable
private fun SpanLine(color: androidx.compose.ui.graphics.Color, range: DayRange) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Dot(color)
        Muted(
            "${date(range.from, "dd/MM/yy")} - ${date(range.to.minusDays(1), "dd/MM/yy")}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun glanceText(
    glance: Glance,
    range: ProgressRange,
    compare: Int?,
    total: Double,
    previousTotal: Double,
    metric: VolumeMetric,
    format: VolumeFormat
): String {
    val value = format.value(metric, total, 1)
    val previous = format.value(metric, previousTotal, 1)
    val period = periodWords(range)
    val label = compare?.let { pluralStringResource(R.plurals.progress_glance_compare_label, it, it) }.orEmpty()
    return when (glance) {
        Glance.None -> stringResource(R.string.progress_glance_none, period)
        Glance.New -> stringResource(R.string.progress_glance_new, value, period)
        Glance.Same -> stringResource(R.string.progress_glance_same, value, period)
        is Glance.Up -> stringResource(R.string.progress_glance_up, value, period, glance.pct, previous)
        is Glance.Down -> stringResource(R.string.progress_glance_down, value, period, glance.pct, previous)
        is Glance.CompareUp -> stringResource(R.string.progress_glance_compare_up, glance.pct, label, previous)
        is Glance.CompareDown -> stringResource(R.string.progress_glance_compare_down, glance.pct, label, previous)
    }
}
