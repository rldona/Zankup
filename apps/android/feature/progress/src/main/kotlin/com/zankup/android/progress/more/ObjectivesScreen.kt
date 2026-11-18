package com.zankup.android.progress.more

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.Bucket
import com.zankup.android.api.model.ZankupApiTrainingGoalOut
import com.zankup.android.designsystem.components.EmptyState
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupSheet
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Units
import com.zankup.android.network.ApiResult
import com.zankup.android.progress.R
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Goals, from Progress (objectives.tsx): each goal with its ring, so far, to go or extra, and for months
 * and years whether it is behind an even pace; a menu to edit or delete it; a fold, remembered per goal,
 * with the period's totals and its chart. The + adds one.
 */
@Composable
fun ObjectivesScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (Long) -> Unit,
    model: ObjectivesModel = hiltViewModel()
) {
    val goals by model.goals.state.collectAsStateWithLifecycle(initialValue = null)
    val open by model.open.collectAsStateWithLifecycle()
    Scaffold(containerColor = ZankupTheme.colors.page, topBar = {
        ZankupTopBar(stringResource(R.string.more_goals), onBack = onBack) {
            IconButton(onClick = onNew) {
                ZankupIcon(ZIcon.Add, stringResource(R.string.more_new_goal), size = 30.dp)
            }
        }
    }) { padding ->
        val list = goals?.data
        when {
            list == null && goals?.failure == null ->
                Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { Skeleton(Modifier.fillMaxWidth(), height = 140.dp) }
                }
            list == null ->
                ErrorState(
                    onRetry = model::refresh,
                    modifier = Modifier.padding(padding),
                    offline = goals?.failure is ApiResult.Offline
                )
            list.isEmpty() ->
                EmptyState(stringResource(R.string.more_no_goals), Modifier.padding(padding), icon = ZIcon.Goal)
            else ->
                LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                    items(list, key = { it.id }) { goal ->
                        GoalBlock(goal, goal.id.toString() in open, model, onEdit)
                    }
                }
        }
    }
}

@Composable
private fun GoalBlock(goal: ZankupApiTrainingGoalOut, open: Boolean, model: ObjectivesModel, onEdit: (Long) -> Unit) {
    val colors = ZankupTheme.colors
    val formats = LocalFormats.current
    val imperial = formats.units == Units.IMPERIAL
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val figures = goal.figures()
    val rest = left(figures)
    val late = behind(figures, LocalDate.now())
    fun value(v: Double) = volumeValue(goal.metric, v, imperial, formats.locale, 1)

    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GoalRing(goal, 52.dp)
                Column(Modifier.weight(1f)) {
                    Text(goalTitle(goal), color = colors.fg, fontSize = 16.sp)
                    Text(goalNote(goal), color = colors.fg, fontSize = 14.sp)
                }
                IconButton(onClick = { menu = true }) {
                    ZankupIcon(ZIcon.MoreHoriz, stringResource(R.string.more_goal_menu), tint = colors.fg)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Stat(stringResource(R.string.more_so_far), value(goal.current))
                if (rest >= 0) {
                    Stat(stringResource(R.string.more_to_go), value(rest))
                } else {
                    Stat(stringResource(R.string.more_extra), value(-rest))
                }
                if (goal.period != "week" && rest > 0) {
                    if (late > 0) {
                        Stat(stringResource(R.string.more_behind), value(late))
                    } else {
                        Stat(stringResource(R.string.more_ahead), value(-late))
                    }
                }
            }
        }
        val foldLabel =
            when (goal.period) {
                "week" -> R.string.more_last_12_weeks
                "month" -> R.string.more_last_12_months
                else -> R.string.more_see_progress
            }
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (open) colors.surface2 else colors.surface)
                .clickable(role = Role.Button) { model.setOpen(goal.id, !open) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (open) "" else stringResource(foldLabel),
                color = colors.fg,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            ZankupIcon(if (open) ZIcon.ArrowUp else ZIcon.ArrowDown, null, tint = colors.fg, size = 26.dp)
        }
        if (open) GoalFold(goal, model)
        Spacer(Modifier.fillMaxWidth().height(6.dp).background(colors.bg))
    }

    if (menu) {
        ZankupSheet(onDismiss = { menu = false }) {
            Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 8.dp)) {
                MenuLine(ZIcon.Pencil, stringResource(R.string.more_edit_goal), colors.fg) {
                    menu = false
                    onEdit(goal.id.toLong())
                }
                MenuLine(ZIcon.Delete, stringResource(R.string.more_delete_goal), colors.formNegative) {
                    menu = false
                    confirm = true
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.more_delete_goal)) },
            text = { Text(stringResource(R.string.more_delete_goal_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch { model.delete(goal.id) }
                }) { Text(stringResource(R.string.more_delete_goal), color = colors.formNegative) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.more_cancel)) }
            }
        )
    }
}

@Composable
private fun MenuLine(icon: ZIcon, label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ZankupIcon(icon, null, tint = tint)
        Text(label, color = tint, fontSize = 15.sp)
    }
}

/** The fold: this period's totals for the goal's sport, then the goal over its weeks, months or year. */
@Composable
private fun GoalFold(goal: ZankupApiTrainingGoalOut, model: ObjectivesModel) {
    val colors = ZankupTheme.colors
    val formats = LocalFormats.current
    val imperial = formats.units == Units.IMPERIAL
    val span = remember(goal.period) { chartPeriods(goal.period, LocalDate.now()) }
    val query = remember(span) { model.volume(span) }
    val volume by query.state.collectAsStateWithLifecycle(initialValue = null)
    val buckets = volume?.data.orEmpty()
    fun of(b: Bucket) = if (goal.sport != null) b.bySport[goal.sport] else b.totals
    val totals = if (goal.period == "year") buckets.map(::of) else listOf(buckets.lastOrNull()?.let(::of))
    fun sum(m: String) = totals.sumOf { metricOf(it, m) }
    val values = buckets.map { metricOf(of(it), goal.metric) }
    Column(
        Modifier.fillMaxWidth().background(colors.surface2).padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            listOf(
                volumeValue("distance", sum("distance"), imperial, formats.locale, 1),
                volumeValue("time", sum("time"), imperial, formats.locale),
                volumeValue("ascent", sum("ascent"), imperial, formats.locale),
                pluralStringResource(R.plurals.more_activities_count, sum("count").toInt(), sum("count").toInt())
            ).joinToString("  "),
            color = colors.fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(metricShort(goal.metric)),
                color = colors.muted,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            LegendDot(colors.muted, stringResource(R.string.more_goal_target))
            LegendDot(colors.accent, stringResource(R.string.more_goal_actual))
        }
        if (buckets.isNotEmpty()) {
            GoalChart(goal.period, goal.target, buckets.map { LocalDate.parse(it.start.take(10)) }, values)
        } else if (volume?.data == null) {
            Skeleton(Modifier.fillMaxWidth(), height = 150.dp)
        }
    }
}

internal fun metricShort(metric: String) = when (metric) {
    "time" -> R.string.more_metric_short_time
    "distance" -> R.string.more_metric_short_distance
    "ascent" -> R.string.more_metric_short_ascent
    else -> R.string.more_metric_short_count
}

internal fun periodName(period: String) = when (period) {
    "month" -> R.string.more_goal_period_month
    "year" -> R.string.more_goal_period_year
    else -> R.string.more_goal_period_week
}

private fun kindName(metric: String) = when (metric) {
    "time" -> R.string.more_goal_kind_time
    "distance" -> R.string.more_goal_kind_distance
    "ascent" -> R.string.more_goal_kind_ascent
    else -> R.string.more_goal_kind_count
}

/** "Weekly: Distance goal". */
@Composable
private fun goalTitle(g: ZankupApiTrainingGoalOut) =
    stringResource(R.string.more_goal_title, stringResource(periodName(g.period)), stringResource(kindName(g.metric)))

/** The sport (and the count, for activities). */
@Composable
private fun goalNote(g: ZankupApiTrainingGoalOut): String {
    val sport = goalSport(g.sport)
    if (g.metric != "count") return sport
    val n = g.target.roundToInt()
    return pluralStringResource(R.plurals.more_goal_count_note, n, sport, n)
}

@Composable
internal fun goalSport(key: String?) =
    if (key == null) stringResource(R.string.more_all_sports) else stringResource(sportName(sportOf(key)))

/** A goal's ring, filling clockwise from twelve o'clock, with its sport in the middle. */
@Composable
internal fun GoalRing(goal: ZankupApiTrainingGoalOut, size: Dp) {
    val colors = ZankupTheme.colors
    val done = fraction(goal.target, goal.current)
    Box(
        Modifier.size(size).semantics { contentDescription = "${(done * 100).roundToInt()} %" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.width * 0.09f
            val inset = stroke / 2 + 1
            val arcSize = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            val topLeft = Offset(inset, inset)
            drawArc(colors.muted.copy(alpha = 0.35f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (done > 0f) {
                drawArc(
                    colors.accent,
                    -90f,
                    360f * done,
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        ZankupIcon(sportIconOf(goal.sport), null, tint = colors.fg, size = size * 0.4f)
    }
}

/**
 * A goal over time (src/progress/GoalsCard.tsx GoalChart). Weeks or months: each one's value (accent)
 * against the goal (grey) on its own line. The year: the running total by month against the goal's diagonal.
 */
@Composable
internal fun GoalChart(period: String, target: Double, starts: List<LocalDate>, values: List<Double>) {
    val colors = ZankupTheme.colors
    val locale = LocalFormats.current.locale
    val height = 150.dp
    val year = period == "year"
    val shown = if (year) listOf(0.0) + runningTotal(values) else values
    val slots = if (year) 13 else max(1, shown.size)
    val most = maxOf(target, shown.maxOrNull() ?: 0.0, 1.0)
    fun month(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, locale).replace(".", "")
    val labels: List<Pair<Int, String>> =
        if (year) {
            val y = starts.firstOrNull()?.year ?: LocalDate.now().year
            listOf(0, 3, 6, 9).map { it to month(LocalDate.of(y, it + 1, 15)) }
        } else {
            starts.mapIndexedNotNull { i, s -> if (i == 0 || s.month != starts[i - 1].month) i to month(s) else null }
        }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val pad = with(density) { 16.dp.toPx() }
        val topPx = with(density) { 16.dp.toPx() }
        val basePx = with(density) { (height - 30.dp).toPx() }
        fun x(i: Int) = pad + i.toFloat() / max(1, slots - 1) * (widthPx - 2 * pad)
        fun y(v: Double) = basePx - (min(v, most) / most).toFloat() * (basePx - topPx)
        Canvas(Modifier.fillMaxSize()) {
            val hair = 1.dp.toPx()
            drawLine(colors.line, Offset(0f, topPx), Offset(size.width, topPx), hair)
            drawLine(colors.line, Offset(0f, basePx), Offset(size.width, basePx), hair)
            for (i in 0 until slots) drawLine(colors.line, Offset(x(i), topPx), Offset(x(i), basePx), hair)
            val dot = 6.dp.toPx()
            if (year) {
                drawLine(colors.muted, Offset(x(0), y(0.0)), Offset(x(12), y(target)), 2.dp.toPx())
                val path = Path()
                shown.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                drawPath(path, colors.accent, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
            } else {
                shown.forEachIndexed { i, v ->
                    drawCircle(colors.muted, dot, Offset(x(i), y(target)))
                    drawCircle(colors.accent, dot, Offset(x(i), y(v)))
                }
            }
            val last = shown.lastIndex
            if (last >= 0) {
                val at = Offset(x(last), y(shown[last]))
                drawCircle(colors.accent.copy(alpha = 0.3f), 14.dp.toPx(), at)
                drawCircle(colors.accent, 7.dp.toPx(), at)
            }
        }
        labels.forEach { (i, text) ->
            Text(
                text,
                color = colors.fg,
                fontSize = 13.sp,
                modifier =
                Modifier.padding(
                    start = with(density) { (x(i) + 6.dp.toPx()).toDp() },
                    top = height - 22.dp
                )
            )
        }
    }
}
