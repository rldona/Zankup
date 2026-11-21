package com.zankup.android.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.Totals
import com.zankup.android.charts.Curve
import com.zankup.android.charts.CurveChart
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.domain.formatDuration
import com.zankup.android.domain.formatNumber
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** A section still loading: a bone of its height. */
@Composable
private fun Bone(height: Int = 200) {
    Skeleton(Modifier.fillMaxWidth(), height = height.dp)
}

// --- summary -----------------------------------------------------------------------------------

private class SummaryRow(val label: String, val value: String, val now: Double, val before: Double)

@Composable
internal fun SummarySection(model: StatsModel, range: Period) {
    val state by model.summary.collectAsStateWithLifecycle()
    val data = state.data ?: return if (state.loading) Bone() else Unit
    val f = LocalFormats.current
    val colors = ZankupTheme.colors
    val current = data.current
    val previous = data.previous
    val labels = when (val c = comparisonLabels(range.from, range.to, f.locale)) {
        Compared.None -> null
        Compared.Rolling -> stringResource(R.string.stats_compare_before) to stringResource(R.string.stats_compare_now)
        is Compared.Named -> c.before to c.now
    }
    val rows = listOf(
        SummaryRow(
            stringResource(R.string.stats_activities),
            formatNumber(current.n().toDouble(), f.locale),
            current.n().toDouble(),
            previous.n().toDouble()
        ),
        SummaryRow(stringResource(R.string.stats_distance), f.distance(current.d()), current.d(), previous.d()),
        SummaryRow(stringResource(R.string.stats_time), f.hoursMinutes(current.t()), current.t(), previous.t()),
        SummaryRow(stringResource(R.string.stats_ascent), f.elevation(current.a()), current.a(), previous.a()),
        SummaryRow(
            stringResource(R.string.stats_load),
            formatNumber(current.l().roundToInt().toDouble(), f.locale),
            current.l(),
            previous.l()
        )
    )
    val beforeColour = colors.muted.copy(alpha = 0.45f)
    StatCard(stringResource(R.string.stats_summary_title), info = stringResource(R.string.stats_summary_note)) {
        rows.forEach { row ->
            val cmp = compare(row.now, row.before)
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        row.label,
                        fontSize = 14.sp,
                        color = colors.muted,
                        modifier = Modifier.weight(1f)
                    )
                    Text(row.value, style = StatValueStyle, color = colors.fg)
                    val percent = cmp.percent
                    if (labels != null && percent != null && percent != 0) {
                        val tone = if (percent > 0) colors.formPositive else colors.formNegative
                        Text(
                            stringResource(R.string.stats_change, if (percent > 0) "+" else "", percent),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = lerp(tone, colors.fg, 0.4f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(tone.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 1.dp)
                        )
                    }
                }
                if (labels != null) {
                    ShareBar(cmp.before, beforeColour)
                    ShareBar(cmp.now, colors.accent)
                }
            }
        }
        if (labels != null) {
            Legend(
                listOf(labels.first to beforeColour, labels.second to colors.accent),
                Modifier.padding(top = 6.dp),
                bars = true
            )
        }
    }
}

@Composable
internal fun ShareBar(share: Float, colour: Color) {
    val width = if (share > 0f) share.coerceAtLeast(0.02f) else 0f
    if (width == 0f) {
        Spacer(Modifier.height(7.dp))
        return
    }
    Box(Modifier.fillMaxWidth(width).height(7.dp).clip(RoundedCornerShape(4.dp)).background(colour))
}

private fun Totals.n() = count ?: 0
private fun Totals.d() = distanceM ?: 0.0
private fun Totals.t() = movingS ?: 0.0
private fun Totals.a() = ascentM ?: 0.0
private fun Totals.l() = load ?: 0.0

// --- fitness -----------------------------------------------------------------------------------

@Composable
internal fun FitnessSection(model: StatsModel) {
    val state by model.fitness.collectAsStateWithLifecycle()
    val f = LocalFormats.current
    val colors = ZankupTheme.colors
    val days = state.data.orEmpty()
    StatCard(stringResource(R.string.stats_fitness), info = stringResource(R.string.stats_fitness_note)) {
        when {
            state.loading -> Bone()
            days.size > 1 -> {
                val top = (days.maxOf { maxOf(it.ctl, it.atl) }.coerceAtLeast(1.0) * 1.05).toFloat()
                val ctl = stringResource(R.string.stats_ctl)
                val atl = stringResource(R.string.stats_atl)
                val tsb = stringResource(R.string.stats_tsb)
                val last = days.last()
                LinesChart(
                    lines = listOf(
                        ChartLine(ctl, days.map { it.ctl.toFloat() }, colors.accent),
                        ChartLine(atl, days.map { it.atl.toFloat() }, colors.zones.last(), width = 1.2.dp)
                    ),
                    xLabels = days.map { dayLabel(it.day, f) },
                    format = { it.roundToInt().toString() },
                    description = "$ctl ${last.ctl.roundToInt()}, $atl ${last.atl.roundToInt()}, " +
                        "$tsb ${last.tsb.roundToInt()}",
                    height = CHART_HEIGHT,
                    domain = 0f..top,
                    bars = SignedBars(days.map { it.tsb.toFloat() }, colors.formPositive, colors.formNegative, tsb)
                )
                // In the RN app's order: the form's two colours, then fitness and fatigue.
                Legend(
                    listOf(
                        tsb to colors.formPositive,
                        "$tsb < 0" to colors.formNegative,
                        ctl to colors.accent,
                        atl to colors.zones.last()
                    ),
                    Modifier.padding(top = 18.dp)
                )
            }
            else -> Muted(stringResource(R.string.stats_empty))
        }
    }
}

// --- volume ------------------------------------------------------------------------------------

private enum class VolumeMetric { TIME, DISTANCE }

@Composable
internal fun VolumeSection(model: StatsModel) {
    val state by model.volume.collectAsStateWithLifecycle()
    val period by model.period.collectAsStateWithLifecycle()
    var metric by rememberSaveable { mutableStateOf(VolumeMetric.TIME) }
    val f = LocalFormats.current
    val buckets = state.data.orEmpty()
    val unit = period.range.unit
    StatCard(stringResource(R.string.stats_volume), right = {
        ChipPicker(
            VolumeMetric.entries,
            metric,
            {
                stringResource(if (it == VolumeMetric.TIME) R.string.stats_hours else R.string.stats_distance_unit)
            },
            { metric = it },
            accent = true
        )
    }) {
        if (state.loading) {
            Bone()
            return@StatCard
        }
        if (buckets.isEmpty()) return@StatCard
        val sports = buckets.flatMap { it.bySport.keys }.distinct().sortedBy { Sport.entries.indexOf(sportOf(it)) }
        val colours = sports.associateWith { sportColour(it) }
        val scale = f.scale
        fun of(t: Totals) = if (metric == VolumeMetric.TIME) t.t() / 3600 else t.d() / scale
        val total = buckets.sumOf { if (metric == VolumeMetric.TIME) it.totals.t() else it.totals.d() }
        Text(
            if (metric == VolumeMetric.TIME) {
                f.hoursMinutes(total)
            } else {
                "${formatNumber((total / scale).roundToInt().toDouble(), f.locale)} ${f.unit.distanceUnit}"
            },
            style = StatValueStyle,
            color = ZankupTheme.colors.fg,
            modifier = Modifier.padding(top = ZankupTheme.spacing.xs, bottom = ZankupTheme.spacing.sm)
        )
        val pattern = when (unit) {
            VolumeUnit.YEAR -> "yyyy"
            VolumeUnit.MONTH -> "MMM yy"
            else -> "d MMM"
        }
        val format = DateTimeFormatter.ofPattern(pattern, f.locale)
        StackedBars(
            stacks = buckets.map { b ->
                Stack(sports.mapNotNull { s -> b.bySport[s]?.let { of(it).toFloat() to colours.getValue(s) } })
            },
            xLabels = buckets.map { runCatching { format.format(LocalDate.parse(it.start)) }.getOrDefault(it.start) },
            format = { formatNumber(it.toDouble(), f.locale, 0, if (it < 10) 1 else 0) },
            description = stringResource(R.string.stats_volume),
            height = CHART_HEIGHT
        )
        Legend(
            sports.map {
                stringResource(sportName(sportOf(it))) to colours.getValue(it)
            },
            Modifier.padding(top = 18.dp)
        )
    }
}

// --- aerobic efficiency ------------------------------------------------------------------------

@Composable
internal fun EfficiencySection(model: StatsModel) {
    val state by model.efficiency.collectAsStateWithLifecycle()
    val points = state.data ?: return
    val values = points.map { it.efficiency }
    val change = efficiencyChange(values) ?: return
    val f = LocalFormats.current
    val title = stringResource(R.string.stats_efficiency)
    StatCard(title, right = {
        Text(
            "${if (change > 0) "+" else ""}${formatNumber(change, f.locale, 1, 1)} %",
            style = StatValueStyle,
            color = ZankupTheme.colors.fg
        )
    }) {
        LinesChart(
            lines = listOf(ChartLine(title, values.map { it.toFloat() }, ZankupTheme.colors.sport.run)),
            xLabels = points.map { dayLabel(it.day, f) },
            format = { formatNumber(it.toDouble(), f.locale, 0, 2) },
            description = title,
            height = CHART_HEIGHT
        )
        Muted(stringResource(R.string.stats_efficiency_note), Modifier.padding(top = 12.dp))
    }
}

// --- best-effort curves ------------------------------------------------------------------------

@Composable
internal fun CurvesSection(model: StatsModel) {
    val kind by model.curve.collectAsStateWithLifecycle()
    val period by model.curves.collectAsStateWithLifecycle()
    val all by model.allTimeCurves.collectAsStateWithLifecycle()
    val f = LocalFormats.current
    val colors = ZankupTheme.colors
    val mine = period.data.orEmpty().associate { it.durationS to it.value }
    val best = all.data.orEmpty().associate { it.durationS to it.value }
    val show: (Double) -> String = { v -> if (kind == CurveKind.POWER) "${v.roundToInt()} W" else f.unit.pace(v) }
    StatCard(stringResource(R.string.stats_curves), right = {
        ChipPicker(
            CurveKind.entries,
            kind,
            { stringResource(if (it == CurveKind.POWER) R.string.stats_power else R.string.stats_pace) },
            model::setCurve,
            accent = true
        )
    }) {
        val durations = curveDurations(best, mine)
        when {
            period.loading -> Bone()
            durations.size > 1 -> {
                CurveChart(
                    curves = listOfNotNull(
                        best.takeIf { it.isNotEmpty() }?.let { points ->
                            Curve(
                                stringResource(R.string.stats_all_time),
                                points.entries.sortedBy { it.key }.map { it.key.toFloat() to it.value.toFloat() },
                                colors.muted
                            )
                        },
                        mine.takeIf { it.isNotEmpty() }?.let { points ->
                            Curve(
                                stringResource(R.string.stats_this_period),
                                points.entries.sortedBy { it.key }.map { it.key.toFloat() to it.value.toFloat() },
                                if (kind == CurveKind.POWER) colors.sport.ride else colors.sport.run
                            )
                        }
                    ),
                    format = { show(it.toDouble()) },
                    durationLabel = { durationLabel(it.roundToInt()) },
                    height = CHART_HEIGHT
                )
                val periodColour = if (kind == CurveKind.POWER) colors.sport.ride else colors.sport.run
                Legend(
                    listOf(
                        stringResource(R.string.stats_all_time) to colors.muted,
                        stringResource(R.string.stats_this_period) to periodColour
                    ),
                    Modifier.padding(top = 18.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    durations.filter { it in listOf(60, 300, 1200, 3600) }.forEach { d ->
                        Stat(durationLabel(d), mine[d]?.let(show) ?: "–")
                    }
                }
            }
            else -> Muted(stringResource(R.string.stats_empty))
        }
    }
}

// --- records -----------------------------------------------------------------------------------

@Composable
internal fun RecordsSection(model: StatsModel, onBests: (String?) -> Unit, onOpenActivity: (Long) -> Unit) {
    val state by model.records.collectAsStateWithLifecycle()
    val data = state.data ?: return
    if (data.allTime.isEmpty()) return
    val f = LocalFormats.current
    val bests = data.period.filter { effortShown(it.distanceKey, f.imperial) }
    StatCard(stringResource(R.string.stats_period_bests)) {
        if (bests.isEmpty()) Muted(stringResource(R.string.stats_no_period_bests))
        bests.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    effortName(r.distanceKey),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).clickable(role = Role.Button) { onBests(r.distanceKey) }
                )
                Link(formatDuration(r.timeS), { onOpenActivity(r.activityId.toLong()) })
                Text(
                    " ${shortDay(r.day, f)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZankupTheme.colors.muted
                )
            }
        }
        Row(
            Modifier.padding(top = 12.dp).clickable(role = Role.Button) { onBests(null) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.stats_all_bests),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = ZankupTheme.colors.accent
            )
            ZankupIcon(ZIcon.Chevron, contentDescription = null, tint = ZankupTheme.colors.accent, size = 20.dp)
        }
    }
}
