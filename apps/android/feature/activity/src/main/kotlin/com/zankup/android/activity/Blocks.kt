package com.zankup.android.activity

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.zankup.android.api.model.ActivityContext
import com.zankup.android.api.model.ActivityDetail
import com.zankup.android.api.model.EffortOut
import com.zankup.android.api.model.FitnessOut
import com.zankup.android.api.model.MatchOut
import com.zankup.android.api.model.SummaryOut
import com.zankup.android.api.model.ZankupApiInsightsGoalOut
import com.zankup.android.api.model.ZankupApiInsightsMedal
import com.zankup.android.charts.ChartSection
import com.zankup.android.charts.Curve
import com.zankup.android.charts.CurveChart
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupSheet
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.domain.Units
import com.zankup.android.domain.formatDuration
import com.zankup.android.domain.formatNumber
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.family
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** A card's title, as the React Native app's (17, bold). */
@Composable
internal fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = modifier)
}

/**
 * A card with the page's side margins: its title, its content and, at its foot, the link to its screen
 * in the accent (as Strava's orange line).
 */
@Composable
internal fun Block(
    title: String? = null,
    link: String? = null,
    onLink: (() -> Unit)? = null,
    gap: Int = 10,
    content: @Composable ColumnScope.() -> Unit
) {
    ZankupCard(Modifier.padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(gap.dp)) {
            if (title != null) CardTitle(title)
            content()
            if (link != null && onLink != null) Link(link, onLink, Modifier.align(Alignment.End))
        }
    }
}

/** A link in the accent, bold: a text button with its ripple. */
@Composable
internal fun Link(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 32.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = ZankupTheme.colors.accent)
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

/** A figure: its name small and muted over its value, large and bold. */
@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = ZankupTheme.colors.muted)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, style = Numbers.tabular)
    }
}

@Composable
internal fun GoalReached(onGoals: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ZankupTheme.colors.accentSoft)
            .clickable(onClick = onGoals)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ZankupIcon(ZIcon.Goal, null, tint = ZankupTheme.colors.accent)
        Text(
            stringResource(R.string.goal_reached_banner),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        ZankupIcon(ZIcon.Chevron, null, tint = ZankupTheme.colors.muted)
    }
}

private const val PREFS = "activity"
private const val COACH_FOLDED = "coach_folded"

/**
 * "Zankup Coach · Activity summary": the coach's summary with its highlights and "Tell me more", or
 * asking for one when the AI is on. Folded or open, remembered on this phone for every activity.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SummaryCard(summary: SummaryOut?, available: Boolean, model: ActivityModel) {
    val colors = ZankupTheme.colors
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var folded by remember { mutableStateOf(prefs.getBoolean(COACH_FOLDED, false)) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    ZankupCard(Modifier.padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) {
                        folded = !folded
                        prefs.edit { putBoolean(COACH_FOLDED, folded) }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(colors.accentSoft),
                    contentAlignment = Alignment.Center
                ) { ZankupIcon(ZIcon.Coach, null, tint = colors.accent, size = 20.dp) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.coach_title), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.coach_subtitle), fontSize = 13.sp, color = colors.muted)
                }
                ZankupIcon(if (folded) ZIcon.Expand else ZIcon.Collapse, null, tint = colors.muted, size = 26.dp)
            }
            if (!folded) {
                if (summary != null) {
                    Text(summary.summary, style = MaterialTheme.typography.bodyLarge)
                    if (summary.highlights.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            summary.highlights.forEach {
                                Text(
                                    it,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(colors.surface2)
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    if (available) {
                        Button(
                            onClick = {
                                more = true
                                model.loadReport()
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) { Text(stringResource(R.string.report_more)) }
                    }
                } else {
                    Text(stringResource(R.string.coach_pitch), color = colors.muted)
                    Button(
                        onClick = {
                            busy = true
                            failed = false
                            scope.launch {
                                failed = !model.writeSummary()
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ZankupIcon(ZIcon.Coach, null, size = 20.dp)
                        Text(
                            stringResource(if (busy) R.string.coach_writing else R.string.coach_write),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            if (failed) Text(stringResource(R.string.coach_failed), color = colors.muted)
        }
    }
    if (more) ReportSheet(model) { more = false }
}

/** "Tell me more": the coach's longer report, written the first time it is opened, then kept. */
@Composable
private fun ReportSheet(model: ActivityModel, onDone: () -> Unit) {
    val colors = ZankupTheme.colors
    val state by model.report.collectAsStateWithLifecycle()
    ZankupSheet(onDismiss = onDone) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (val s = state) {
                is ReportState.Ready -> {
                    val r = s.report
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ZankupIcon(ZIcon.Coach, null, tint = colors.accent, size = 22.dp)
                        Text(stringResource(R.string.coach_title), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(r.headline, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
                    listOf(r.effort, r.story, r.month, r.verdict).filter { it.isNotBlank() }.forEach {
                        Text(emphasis(it), fontSize = 15.sp, lineHeight = 23.sp)
                    }
                    if (r.next.isNotBlank()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface2)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ZankupIcon(ZIcon.Goal, null, tint = colors.accent, size = 20.dp)
                                Text(
                                    stringResource(R.string.report_next),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(emphasis(r.next), fontSize = 15.sp, lineHeight = 22.sp)
                        }
                    }
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.report_done))
                    }
                }
                ReportState.Failed ->
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(stringResource(R.string.report_failed), color = colors.muted)
                        OutlinedButton(onClick = model::loadReport) { Text(stringResource(R.string.retry)) }
                    }
                else ->
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(color = colors.accent)
                        Text(stringResource(R.string.report_writing), color = colors.muted)
                    }
            }
        }
    }
}

/** A coach text with its **double asterisks** in bold. */
internal fun emphasis(text: String): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { i, run ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(run) } else append(run)
    }
}

/** "Fitness up": the points the day added and the score, with the weeks' line. */
@Composable
internal fun FitnessBlock(f: FitnessOut, onOpen: () -> Unit) {
    val points = (f.after - f.before).roundToInt()
    val title =
        when {
            points > 0 -> R.string.fitness_up
            points < 0 -> R.string.fitness_down
            else -> R.string.fitness_same
        }
    Block(stringResource(title), stringResource(R.string.fitness_link), onOpen) {
        Row(verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Figure(stringResource(R.string.points), if (points > 0) "+$points" else "$points")
                Figure(stringResource(R.string.fitness_score), "${f.after.roundToInt()}")
            }
            if (f.points.size > 1) Sparkline(f.points.map { it.ctl }, first = true)
        }
    }
}

/** The efforts on the same route: this one's pace or speed, and the line of all of them. */
@Composable
internal fun MatchesBlock(matches: List<MatchOut>, sport: Sport, onOpen: () -> Unit) {
    val formats = LocalFormats.current
    val mine = matches.firstOrNull { it.`this` } ?: return
    val pace = sport.usesPace
    val others = matches.size - 1
    Block(
        stringResource(if (pace) R.string.matched_runs else R.string.matches),
        pluralStringResource(R.plurals.matched_link, others, others),
        onOpen
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Figure(
                stringResource(if (pace) R.string.this_run else R.string.this_activity),
                mine.speedMps?.takeIf { it > 0 }?.let { formats.unit.speedFor(it, sport) } ?: "–",
                Modifier.weight(1f)
            )
            Sparkline(
                matches.map { m -> if (pace) -(1 / (m.speedMps?.takeIf { it > 0 } ?: 1.0)) else m.speedMps ?: 0.0 },
                first = false
            )
        }
    }
}

/** A relative effort's colour: the accent in the usual range, blue below it and orange above. */
@Composable
private fun statusColour(status: EffortOut.Status): Color = when (status) {
    EffortOut.Status.IN -> ZankupTheme.colors.accent
    EffortOut.Status.BELOW -> ZankupTheme.colors.fitness
    else -> ZankupTheme.colors.zones[3]
}

/** The relative effort against the average of the three weeks before, on three bands. */
@Composable
internal fun EffortBlock(e: EffortOut, onOpen: () -> Unit) {
    val colors = ZankupTheme.colors
    val status = e.status ?: EffortOut.Status.IN
    val says =
        when (status) {
            EffortOut.Status.ABOVE -> R.string.effort_says_above
            EffortOut.Status.BELOW -> R.string.effort_says_below
            else -> R.string.effort_says_in
        }
    val bands =
        listOf(
            EffortOut.Status.ABOVE to R.string.effort_band_above,
            EffortOut.Status.IN to R.string.effort_band_in,
            EffortOut.Status.BELOW to R.string.effort_band_below
        )
    Block(stringResource(R.string.relative_effort), stringResource(R.string.effort_link), onOpen) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(says),
                fontSize = 14.sp,
                color = colors.muted,
                modifier = Modifier.weight(1f).padding(bottom = 4.dp)
            )
            Text("${e.score.roundToInt()}", fontSize = 26.sp, fontWeight = FontWeight.Light, style = Numbers.tabular)
        }
        Column(Modifier.clip(RoundedCornerShape(10.dp))) {
            bands.forEach { (band, label) ->
                val on = band == status
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (on) statusColour(band) else colors.surface2)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        stringResource(label),
                        fontSize = 13.sp,
                        color = if (on) Color.White else colors.fg,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (on) {
                        Text(
                            "${e.score.roundToInt()}",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
        e.average?.takeIf { it > 0 }?.let {
            Text(stringResource(R.string.effort_average, it.roundToInt()), fontSize = 12.sp, color = colors.muted)
        }
    }
}

/** A goal's amount as the goals write it: "10,0 km", "4 h 17 min", "1.421 m", "3". */
@Composable
private fun goalValue(metric: String, v: Double, digits: Int): String {
    val formats = LocalFormats.current
    val imperial = formats.units == Units.IMPERIAL
    return when (metric) {
        "time" -> formats.hoursMinutes(v)
        "distance" ->
            "${formatNumber(
                v / (if (imperial) 1609.344 else 1000.0),
                formats.locale,
                if (v != 0.0) digits else 0,
                if (v != 0.0) digits else 0
            )} ${if (imperial) "mi" else "km"}"
        "ascent" -> "${formatNumber(
            (if (imperial) v * 3.28084 else v).roundToInt().toDouble(),
            formats.locale
        )} ${if (imperial) "ft" else "m"}"
        else -> formatNumber(v.roundToInt().toDouble(), formats.locale)
    }
}

/** The goals the activity counts for, each with its ring and what is left. */
@Composable
internal fun GoalsBlock(goals: List<ZankupApiInsightsGoalOut>, onOpen: () -> Unit) {
    val colors = ZankupTheme.colors
    Block(stringResource(R.string.goals), stringResource(R.string.goals_link), onOpen) {
        goals.forEach { g ->
            val left = g.target - g.current
            val share = if (g.target > 0) (g.current / g.target).toFloat().coerceIn(0f, 1f) else 0f
            val sport =
                g.sport?.let { stringResource(sportName(sportOf(it))) } ?: stringResource(R.string.goal_all_sports)
            val note =
                if (g.metric == "count") {
                    pluralStringResource(R.plurals.goal_count_note, g.target.roundToInt(), sport, g.target.roundToInt())
                } else {
                    sport
                }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GoalRing(share, g.sport?.let { sportOf(it).family.icon } ?: ZIcon.Progress)
                Column(Modifier.weight(1f)) {
                    val period =
                        when (g.period) {
                            "month" -> R.string.goal_period_month
                            "year" -> R.string.goal_period_year
                            else -> R.string.goal_period_week
                        }
                    val status =
                        if (left > 0) {
                            stringResource(R.string.goal_left, goalValue(g.metric, left, 1))
                        } else {
                            stringResource(R.string.goal_done)
                        }
                    Text("${stringResource(period)} · $status", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${goalValue(g.metric, g.current, 1)} / ${goalValue(g.metric, g.target, 0)} · $note",
                        fontSize = 12.sp,
                        color = colors.muted
                    )
                }
            }
        }
    }
}

/** A best's name: "5 km", "Power: 5 min", "Longest ride". */
@Composable
private fun bestName(key: String): String = when {
    key.startsWith("power_") ->
        stringResource(R.string.best_power, durationLabel(key.removePrefix("power_").toFloatOrNull() ?: 0f))
    key == "longest" -> stringResource(R.string.best_longest)
    else -> effortName(key) ?: key
}

/** A best effort's distance by its key, when it has a name. */
@Composable
internal fun effortName(key: String): String? = when (key) {
    "400m" -> R.string.effort_400m
    "half_mile" -> R.string.effort_half_mile
    "1k" -> R.string.effort_1k
    "1mile" -> R.string.effort_1mile
    "2mile" -> R.string.effort_2mile
    "5k" -> R.string.effort_5k
    "10k" -> R.string.effort_10k
    "15k" -> R.string.effort_15k
    "10mile" -> R.string.effort_10mile
    "half_marathon" -> R.string.effort_half_marathon
    "marathon" -> R.string.effort_marathon
    else -> null
}?.let { stringResource(it) }

/** 57:05 or 1:26:59. */
internal fun clock(seconds: Double): String {
    val s = seconds.roundToInt()
    val h = s / 3600
    val m = s % 3600 / 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s % 60) else "%d:%02d".format(m, s % 60)
}

private fun medalValue(m: ZankupApiInsightsMedal, imperial: Boolean, locale: java.util.Locale): String = when {
    m.key.startsWith("power_") -> "${m.value.roundToInt()} W"
    m.key == "longest" -> "${formatNumber(
        m.value / if (imperial) 1609.344 else 1000.0,
        locale,
        0,
        1
    )} ${if (imperial) "mi" else "km"}"
    else -> clock(m.value)
}

/** The activity's results: its medals in best times or power, and its places on segments. */
@Composable
internal fun ResultsBlock(context: ActivityContext, ride: Boolean, onBests: (String?) -> Unit) {
    val colors = ZankupTheme.colors
    val formats = LocalFormats.current
    val imperial = formats.units == Units.IMPERIAL
    Block(stringResource(R.string.results), stringResource(R.string.results_link), { onBests(null) }) {
        Row {
            listOf(
                stringResource(if (ride) R.string.results_skills else R.string.results_best_times) to
                    context.medals.size,
                stringResource(R.string.segments) to context.segments.size
            ).forEach { (label, n) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontSize = 12.sp, color = colors.muted)
                    Text("$n", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        context.medals.take(3).forEach { m ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onBests(m.key) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Medal(m.rank)
                Column(Modifier.weight(1f)) {
                    Text(bestName(m.key), fontSize = 15.sp)
                    Text(
                        "${medalValue(m, imperial, formats.locale)} · ${
                            stringResource(
                                if (m.scope.value == "all") R.string.medal_all_time else R.string.medal_this_year
                            )
                        }",
                        fontSize = 13.sp,
                        color = colors.muted
                    )
                }
            }
        }
        context.segments.take(3).forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.rank <= 3) {
                    Medal(s.rank)
                } else {
                    Text(
                        "${s.rank}",
                        color = colors.muted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(36.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            "${formatNumber(
                                s.distanceM / if (imperial) 1609.344 else 1000.0,
                                formats.locale,
                                0,
                                2
                            )} ${if (imperial) "mi" else "km"}",
                            clock(s.elapsedS),
                            s.avgPower?.let { "$it W" }
                        ).joinToString(" · "),
                        fontSize = 13.sp,
                        color = colors.muted
                    )
                }
            }
        }
    }
}

/** "Training analysis" on a run's page: the splits over the elevation, the average and the fastest. */
@Composable
internal fun AnalysisCard(a: ActivityDetail, drawn: Drawn?, onOpen: () -> Unit) {
    val formats = LocalFormats.current
    if (a.splits.size < 2) return
    val speeds = a.splits.map { if (it.timeS > 0) it.distanceM / it.timeS else 0.0 }
    val total = a.splits.sumOf { it.distanceM }
    val time = a.splits.sumOf { it.timeS }
    val sport = sportOf(a.sport)
    Block(stringResource(R.string.training_analysis), stringResource(R.string.analysis_open), onOpen, gap = 8) {
        SplitsChart(
            speeds = speeds,
            widths = a.splits.map { it.distanceM },
            elevation = drawn?.let { elevationProfile(it.distance, it.altitude, a.distanceM ?: 0.0) },
            axis = { formats.unit.speedFor(it, sport).substringBefore(' ') },
            unitLabel = if (sport.usesPace) {
                "/${formats.unit.distanceUnit}"
            } else {
                formats.unit.speed(
                    1.0
                ).substringAfter(' ')
            }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(
                    R.string.analysis_average,
                    if (time >
                        0
                    ) {
                        formats.unit.speedFor(total / time, sport)
                    } else {
                        "–"
                    }
                ),
                fontSize = 12.sp,
                color = ZankupTheme.colors.muted
            )
            Text(
                stringResource(R.string.analysis_fastest, formats.unit.speedFor(speeds.max(), sport)),
                fontSize = 12.sp,
                color = ZankupTheme.colors.muted
            )
        }
    }
}

/** The altitude in [steps] equal steps over the distance, for the analysis' grey area. */
internal fun elevationProfile(dist: FloatArray, alt: FloatArray?, total: Double, steps: Int = 120): FloatArray? {
    alt ?: return null
    if (total <= 0 || dist.isEmpty()) return null
    var j = 0
    return FloatArray(steps + 1) { k ->
        val at = total * k / steps
        while (j < dist.lastIndex && dist[j + 1] <= at) j++
        alt[j]
    }
}

/** The splits as Strava's table: the split, its pace, a bar as long as its speed, the climb and HR. */
@Composable
internal fun SplitsTable(a: ActivityDetail) {
    if (a.splits.size < 2) return
    val formats = LocalFormats.current
    val colors = ZankupTheme.colors
    val sport = sportOf(a.sport)
    val imperial = formats.units == Units.IMPERIAL
    val unit = if (imperial) 1609.344 else 1000.0
    val speeds = a.splits.map { if (it.timeS > 0) it.distanceM / it.timeS else 0.0 }
    val usable = speeds.filter { it > 0 }
    // As Strava's: the bars start a quarter below the slowest split, so the differences show.
    val lo = (usable.minOrNull() ?: 0.0) * 0.75
    val hi = usable.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Block(stringResource(R.string.splits), gap = 0) {
        Box(Modifier.height(12.dp))
        val head = MaterialTheme.typography.bodyMedium
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(if (imperial) R.string.split_mi else R.string.split_km),
                style = head,
                modifier = Modifier.width(34.dp)
            )
            Text(
                stringResource(if (sport.usesPace) R.string.split_pace else R.string.split_speed),
                style = head,
                modifier = Modifier.width(48.dp)
            )
            Box(Modifier.weight(1f))
            Text(
                stringResource(R.string.split_elev),
                style = head,
                textAlign = TextAlign.End,
                modifier = Modifier.width(48.dp)
            )
            Text(
                stringResource(R.string.split_hr),
                style = head,
                textAlign = TextAlign.End,
                modifier = Modifier.width(40.dp)
            )
        }
        HorizontalDivider(color = colors.line, modifier = Modifier.padding(bottom = 6.dp))
        val cell = MaterialTheme.typography.bodyLarge.merge(Numbers.tabular).copy(fontSize = 15.sp)
        a.splits.forEachIndexed { i, s ->
            val v = speeds[i]
            // A last, shorter split shows how much of a km (or mile) it was: 0,6.
            val label = if (s.distanceM <
                unit * 0.95
            ) {
                formatNumber(s.distanceM / unit, formats.locale, 0, 1)
            } else {
                "${i + 1}"
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(label, style = cell, modifier = Modifier.width(34.dp))
                Text(
                    if (v > 0) formats.unit.speedFor(v, sport).substringBefore(' ') else "–",
                    style = cell,
                    modifier = Modifier.width(48.dp)
                )
                Box(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(
                                (
                                    (v - lo) / (hi - lo).takeIf {
                                        it > 0
                                    }!!.coerceAtLeast(1e-9)
                                    ).toFloat().coerceIn(0.04f, 1f)
                            )
                            .height(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(PACE_SCALE[3])
                    )
                }
                Text(
                    s.elevationChangeM?.let { "${(it * if (imperial) 3.28084 else 1.0).roundToInt()}" } ?: "",
                    style = cell,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp)
                )
                Text(
                    s.avgHr?.toString() ?: "",
                    style = cell,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp)
                )
            }
        }
    }
}

/** The best power for each duration in this activity, against the best of all time. */
@Composable
internal fun PowerCurveCard(a: ActivityDetail, model: ActivityModel) {
    val mine = a.metrics?.powerCurve.orEmpty()
    if (mine.size < 2) return
    val all by model.allTimePower.state.collectAsStateWithLifecycle(initialValue = null)
    val curves =
        listOfNotNull(
            all?.data?.takeIf { it.isNotEmpty() }?.let { points ->
                Curve(
                    stringResource(R.string.all_time),
                    points.map { it.durationS.toFloat() to it.value.toFloat() },
                    ZankupTheme.colors.muted
                )
            },
            Curve(
                stringResource(R.string.this_activity),
                mine.map { it.durationS.toFloat() to it.value.toFloat() },
                androidx.compose.ui.graphics.Color(0xFFA63DE6)
            )
        )
    Block(stringResource(R.string.power_curve)) {
        // Room above for the bubble, which rises over the title while the curve is touched.
        androidx.compose.foundation.layout.Spacer(Modifier.height(32.dp))
        CurveChart(
            curves,
            format = { "%.0f W".format(it) },
            durationLabel = ::durationLabel,
            axisFormat = { "%.0f".format(it) },
            unitLabel = "W",
            height = 230.dp
        )
    }
}

internal fun durationLabel(seconds: Float): String = when {
    seconds < 60 -> "${seconds.roundToInt()} s"
    seconds < 3600 -> "${(seconds / 60).roundToInt()} min"
    else -> "${"%.1f".format(seconds / 3600)} h"
}

/** Heart-rate or power zones: the time in each, its bar in the kind's scale and its range. */
@Composable
internal fun ZonesCard(title: String, seconds: List<Double>, bounds: List<Double>, colours: List<Color>, unit: String) {
    Block(title, gap = 0) {
        Box(Modifier.height(12.dp))
        val limits = bounds.map { it.roundToInt() }
        ZoneRows(
            seconds,
            limits.mapIndexed { i, lo -> limits.getOrNull(i + 1)?.let { "$lo–${it - 1} $unit" } ?: "$lo+ $unit" },
            scale(colours, seconds.size)
        ) { formatDuration(it) }
    }
}

/** Heart-rate zones, or how to get them. */
@Composable
internal fun HrZonesCard(a: ActivityDetail) {
    val m = a.metrics
    val seconds = m?.hrZones
    val bounds = m?.hrZoneBounds
    if (seconds.isNullOrEmpty() || bounds.isNullOrEmpty()) {
        Block(stringResource(R.string.hr_zones)) {
            Text(stringResource(R.string.no_zones), color = ZankupTheme.colors.muted)
        }
        return
    }
    ZonesCard(stringResource(R.string.hr_zones), seconds, bounds, HR_SCALE, "bpm")
}

/** A run's pace zones: the share of moving time in each, its range as paces, the fastest on top. */
@Composable
internal fun PaceZonesCard(seconds: List<Double>, threshold: Double, bounds: List<Double>) {
    val formats = LocalFormats.current
    val colors = ZankupTheme.colors
    val total = seconds.sum()
    if (total <= 0) return
    val shades = scale(PACE_SCALE, seconds.size)
    fun pace(mps: Double) = formats.unit.paceValue(mps)
    fun range(i: Int): String {
        val lo = bounds.getOrNull(i)
        val hi = bounds.getOrNull(i + 1)
        return when {
            lo == null || lo <= 0 -> hi?.let { "> ${pace(it)}" } ?: ""
            hi == null -> "< ${pace(lo)}"
            else -> "${pace(hi)} - ${pace(lo)}"
        }
    }
    Block(stringResource(R.string.pace_zones)) {
        Text(
            stringResource(R.string.pace_zones_note, formats.unit.pace(threshold)),
            fontSize = 13.sp,
            color = colors.muted
        )
        seconds.indices.reversed().forEach { i ->
            val share = (seconds[i] / total * 100).roundToInt()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Z${i + 1}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(24.dp))
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((share * 0.0062f).coerceAtLeast(0.02f))
                            .widthIn(min = 8.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(shades[i])
                    )
                    Text("$share%", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Text(range(i), fontSize = 13.sp, color = colors.muted, style = Numbers.tabular)
            }
        }
    }
}

/** The picked section's figures and, with a route, saving it as a segment. */
@Composable
internal fun SectionCard(drawn: Drawn, section: ChartSection, sport: Sport, hasGps: Boolean, model: ActivityModel) {
    val range = section.range ?: return
    val formats = LocalFormats.current
    val from = range.first
    val to = range.last
    val metres = (drawn.distance.getOrElse(to) { 0f } - drawn.distance.getOrElse(from) { 0f }).toDouble()
    val seconds = (drawn.time.getOrElse(to) { 0f } - drawn.time.getOrElse(from) { 0f }).toDouble()
    val ascent =
        drawn.altitude?.let { alt ->
            (from until to).sumOf { i -> (alt[i + 1] - alt[i]).takeIf { it.isFinite() && it > 0 }?.toDouble() ?: 0.0 }
        }
    val hr = drawn.heartRate?.slice(from..to)?.filter { it.isFinite() }?.takeIf { it.isNotEmpty() }?.average()
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Block {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat(stringResource(R.string.col_distance), formats.distance(metres))
            Stat(stringResource(R.string.col_time), formats.duration(seconds))
            Stat(
                stringResource(if (sport.usesPace) R.string.col_pace_full else R.string.col_speed),
                formats.speedFor(sport, metres, seconds)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ascent?.let { Stat(stringResource(R.string.fig_ascent), formats.elevation(it)) }
            hr?.let { Stat(stringResource(R.string.col_avg_hr), "%.0f bpm".format(it)) }
        }
        if (hasGps && !saved) {
            if (naming) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text(stringResource(R.string.segment_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { scope.launch { saved = model.saveSegment(from, to, name.trim()) } },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.save)) }
            } else {
                OutlinedButton(onClick = { naming = true }) { Text(stringResource(R.string.save_segment)) }
            }
        }
        if (saved) Text(stringResource(R.string.segment_saved), color = ZankupTheme.colors.formPositive)
    }
}

/** A small figure: the name in capitals, small and muted, under its value. */
@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, style = Numbers.tabular)
        Text(label.uppercase(), fontSize = 11.sp, color = ZankupTheme.colors.muted)
    }
}

/** A strength workout's kind by its key (Hyrox, GRIT…), or the key itself. */
@Composable
internal fun workoutName(key: String): String = when (key) {
    "rowing" -> R.string.workout_rowing
    "hyrox" -> R.string.workout_hyrox
    "grit" -> R.string.workout_grit
    "bodypump" -> R.string.workout_bodypump
    "functional" -> R.string.workout_functional
    "upper_body" -> R.string.workout_upper_body
    "lower_body" -> R.string.workout_lower_body
    "core" -> R.string.workout_core
    else -> null
}?.let { stringResource(it) } ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }

/** An exercise's name by its category; an unknown one reads as its key. */
@Composable
internal fun exerciseName(category: String?): String = when (category ?: "unknown") {
    "bench_press" -> R.string.exercise_bench_press
    "calf_raise" -> R.string.exercise_calf_raise
    "cardio" -> R.string.exercise_cardio
    "carry" -> R.string.exercise_carry
    "chop" -> R.string.exercise_chop
    "core" -> R.string.exercise_core
    "crunch" -> R.string.exercise_crunch
    "curl" -> R.string.exercise_curl
    "deadlift" -> R.string.exercise_deadlift
    "flye" -> R.string.exercise_flye
    "hip_raise" -> R.string.exercise_hip_raise
    "hip_stability" -> R.string.exercise_hip_stability
    "hip_swing" -> R.string.exercise_hip_swing
    "hyperextension" -> R.string.exercise_hyperextension
    "lateral_raise" -> R.string.exercise_lateral_raise
    "leg_curl" -> R.string.exercise_leg_curl
    "leg_raise" -> R.string.exercise_leg_raise
    "lunge" -> R.string.exercise_lunge
    "olympic_lift" -> R.string.exercise_olympic_lift
    "plank" -> R.string.exercise_plank
    "plyo" -> R.string.exercise_plyo
    "pull_up" -> R.string.exercise_pull_up
    "push_up" -> R.string.exercise_push_up
    "row" -> R.string.exercise_row
    "shoulder_press" -> R.string.exercise_shoulder_press
    "shoulder_stability" -> R.string.exercise_shoulder_stability
    "shrug" -> R.string.exercise_shrug
    "sit_up" -> R.string.exercise_sit_up
    "squat" -> R.string.exercise_squat
    "total_body" -> R.string.exercise_total_body
    "triceps_extension" -> R.string.exercise_triceps_extension
    "warm_up" -> R.string.exercise_warm_up
    "run" -> R.string.exercise_run
    "unknown" -> R.string.exercise_unknown
    else -> null
}?.let { stringResource(it) } ?: category.orEmpty().replace('_', ' ').replaceFirstChar { it.uppercase() }

/** A strength workout's exercises at a glance: each with its sets, reps and volume, the watch on the right. */
@Composable
internal fun ExercisesGlance(a: ActivityDetail) {
    val colors = ZankupTheme.colors
    val exercises = a.sets.filter { it.kind == "active" }.groupBy { it.category ?: "unknown" }
    if (exercises.isEmpty()) return
    ZankupCard(Modifier.padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle(stringResource(R.string.exercises), Modifier.weight(1f))
                a.device?.let { Text(it, fontSize = 13.sp, color = colors.muted) }
            }
            exercises.forEach { (category, sets) ->
                val reps = sets.sumOf { it.reps ?: 0 }
                val volume = sets.sumOf { (it.reps ?: 0) * (it.weightKg ?: 0.0) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(colors.surface2),
                        contentAlignment = Alignment.Center
                    ) { ZankupIcon(ZIcon.Strength, null, tint = colors.sport.strength, size = 22.dp) }
                    Column(Modifier.weight(1f)) {
                        Text(exerciseName(category), fontSize = 15.sp)
                        Text(
                            pluralStringResource(R.plurals.exercise_sets, sets.size, sets.size) + " · " +
                                pluralStringResource(R.plurals.exercise_reps, reps, reps) +
                                if (volume >
                                    0
                                ) {
                                    " · ${formatNumber(
                                        volume.roundToInt().toDouble(),
                                        LocalFormats.current.locale
                                    )} kg"
                                } else {
                                    ""
                                },
                            fontSize = 13.sp,
                            color = colors.muted
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SetsBlock(a: ActivityDetail) {
    val formats = LocalFormats.current
    val active = a.sets.filter { it.kind == "active" }
    val reps = active.sumOf { it.reps ?: 0 }
    val volume = active.sumOf { (it.reps ?: 0) * (it.weightKg ?: 0.0) }
    val rest = a.sets.filter { it.kind != "active" }.sumOf { it.durationS }
    Block(stringResource(R.string.sets)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat(stringResource(R.string.sets), "${active.size}")
            Stat(stringResource(R.string.reps), "$reps")
            Stat(stringResource(R.string.volume), "%.0f kg".format(volume))
            Stat(stringResource(R.string.rest), formats.duration(rest))
        }
        active.forEachIndexed { i, set ->
            HorizontalDivider(color = ZankupTheme.colors.line)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
                Text(
                    exerciseName(set.category),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    listOfNotNull(
                        set.reps?.let { "$it ×" },
                        set.weightKg?.let { "%.1f kg".format(it) } ?: stringResource(R.string.bodyweight)
                    ).joinToString(" "),
                    style = MaterialTheme.typography.bodyMedium.merge(Numbers.tabular)
                )
                Text(
                    formats.duration(set.durationS),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZankupTheme.colors.muted,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

/** The activity's photos, read with the session's client, each opening full size in a sheet. */
@Composable
internal fun Photos(a: ActivityDetail, model: ActivityModel) {
    val context = LocalContext.current
    val http = model.http ?: return
    val loader =
        remember(http) {
            ImageLoader.Builder(context).components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }.build()
        }
    var open by remember { mutableStateOf<Int?>(null) }
    Block(stringResource(R.string.photos)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(a.photoList, key = { it.id }) { photo ->
                AsyncImage(
                    model = model.photoUrl(photo.id, thumb = true),
                    imageLoader = loader,
                    contentDescription = photo.caption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(10.dp)).clickable { open = photo.id }
                )
            }
        }
    }
    open?.let { id ->
        val photo = a.photoList.first { it.id == id }
        ZankupSheet(onDismiss = { open = null }) {
            AsyncImage(
                model = model.photoUrl(id, thumb = false),
                imageLoader = loader,
                contentDescription = photo.caption,
                modifier = Modifier.fillMaxWidth().aspectRatio(photo.width.toFloat() / photo.height.coerceAtLeast(1))
            )
            Box(Modifier.height(24.dp))
        }
    }
}

/** "Route done 9 times: this is your #1", under the cards. */
@Composable
internal fun RouteRank(a: ActivityDetail) {
    val group = a.routeGroup ?: return
    Text(
        pluralStringResource(R.plurals.route_rank, group.count, group.count, group.rank),
        color = ZankupTheme.colors.muted,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
internal fun SegmentsBlock(a: ActivityDetail) {
    Block(stringResource(R.string.segments), gap = 8) {
        a.segments.forEach { s ->
            Column {
                Text(s.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatDuration(s.elapsedS)} · ${if (s.rank == 1) "🏆 " else ""}${
                        stringResource(R.string.segment_rank, s.rank, s.count, formatDuration(s.bestS))
                    }",
                    color = ZankupTheme.colors.muted
                )
            }
        }
    }
}

/** A table's row: cells spread across, the first one on the left; [head] muted and small. */
@Composable
private fun TableRow(cells: List<String>, head: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                color = if (head) ZankupTheme.colors.muted else ZankupTheme.colors.fg,
                fontSize = if (head) 12.sp else 14.sp,
                style = Numbers.tabular,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(if (i == 0) 1.2f else 1f)
            )
        }
    }
}

@Composable
internal fun LapsBlock(a: ActivityDetail, sport: Sport) {
    val formats = LocalFormats.current
    val power = a.laps.any { (it.avgPower ?: 0) > 0 }
    Block(stringResource(R.string.laps), gap = 0) {
        Box(Modifier.height(12.dp))
        TableRow(
            listOfNotNull(
                "#",
                stringResource(R.string.col_distance),
                stringResource(R.string.col_time),
                stringResource(if (sport.usesPace) R.string.col_pace_full else R.string.col_speed),
                stringResource(R.string.col_avg_hr),
                if (power) stringResource(R.string.col_power) else null
            ),
            head = true
        )
        a.laps.forEach { lap ->
            TableRow(
                listOfNotNull(
                    "${lap.idx + 1}",
                    lap.distanceM?.takeIf { it > 0 }?.let { formats.distance(it) } ?: "—",
                    formatDuration(lap.durationS),
                    if ((lap.distanceM ?: 0.0) > 0 &&
                        lap.durationS > 0
                    ) {
                        formats.speedFor(sport, lap.distanceM, lap.durationS)
                    } else {
                        "—"
                    },
                    lap.avgHr?.takeIf { it > 0 }?.toString() ?: "—",
                    if (power) lap.avgPower?.takeIf { it > 0 }?.let { "$it W" } ?: "—" else null
                )
            )
        }
    }
}

@Composable
internal fun Description(text: String) {
    ZankupCard(Modifier.padding(horizontal = 16.dp)) { Text(text) }
}

/** Mile efforts are shown only with miles. */
private val MILE_EFFORTS = setOf("half_mile", "1mile", "2mile", "10mile")

@Composable
internal fun BestEfforts(a: ActivityDetail) {
    val formats = LocalFormats.current
    val shown = a.bestEfforts.filter { formats.units == Units.IMPERIAL || it.distanceKey !in MILE_EFFORTS }
    if (shown.isEmpty()) return
    Block(stringResource(R.string.best_efforts), gap = 0) {
        Box(Modifier.height(12.dp))
        TableRow(
            listOf(
                stringResource(R.string.col_distance),
                stringResource(R.string.col_time),
                stringResource(R.string.col_pace_full)
            ),
            head = true
        )
        shown.forEach { best ->
            TableRow(
                listOf(
                    effortName(best.distanceKey) ?: formats.distance(best.distanceM),
                    formatDuration(best.timeS),
                    formats.unit.pace(best.distanceM / best.timeS)
                )
            )
        }
    }
}

@Composable
internal fun SheetButtons(onCancel: () -> Unit, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        content()
    }
}
