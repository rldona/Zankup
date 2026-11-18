package com.zankup.android.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.zankup.android.api.model.ActivityContext
import com.zankup.android.api.model.ActivityDetail
import com.zankup.android.api.model.EffortOut
import com.zankup.android.api.model.FitnessOut
import com.zankup.android.api.model.MatchOut
import com.zankup.android.api.model.PowerOut
import com.zankup.android.api.model.SummaryOut
import com.zankup.android.api.model.ZankupApiInsightsGoalOut
import com.zankup.android.charts.Bar
import com.zankup.android.charts.BarChart
import com.zankup.android.charts.ChartSection
import com.zankup.android.charts.Curve
import com.zankup.android.charts.CurveChart
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupSheet
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.domain.formatDuration
import com.zankup.android.ui.LocalFormats
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
private fun Pair(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ZankupTheme.colors.muted)
        Text(value, style = MaterialTheme.typography.titleMedium.merge(Numbers.tabular))
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

/** The assistant's summary of the activity, or "Write it" when the AI is on and there is none yet. */
@Composable
internal fun SummaryCard(summary: SummaryOut?, model: ActivityModel) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    Block(title = stringResource(R.string.summary)) {
        if (summary == null) {
            Text(
                stringResource(R.string.summary_none),
                style = MaterialTheme.typography.bodyMedium,
                color = ZankupTheme.colors.muted
            )
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        model.writeSummary()
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.padding(top = 8.dp)
            ) { Text(stringResource(R.string.summary_write)) }
        } else {
            Text(
                summary.summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (open) Int.MAX_VALUE else 4,
                modifier = Modifier.padding(top = 4.dp).clickable { open = !open }
            )
            if (open) summary.highlights.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
internal fun FitnessBlock(f: FitnessOut, onOpen: () -> Unit) {
    Block(title = stringResource(R.string.fitness), onClick = onOpen) {
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Pair(stringResource(R.string.before), "%.0f".format(f.before))
            Pair(stringResource(R.string.after), "%.0f".format(f.after))
            Pair(stringResource(R.string.change), "%+.1f".format(f.after - f.before))
        }
    }
}

@Composable
internal fun MatchesBlock(matches: List<MatchOut>, sport: Sport, onOpen: () -> Unit) {
    val formats = LocalFormats.current
    val ranked = matches.filter { it.speedMps != null }.sortedByDescending { it.speedMps }
    val rank = ranked.indexOfFirst { it.`this` } + 1
    Block(title = stringResource(R.string.matches), onClick = onOpen) {
        Text(
            pluralStringResource(R.plurals.matches_summary, matches.size, matches.size, rank.coerceAtLeast(1)),
            style = MaterialTheme.typography.bodyMedium,
            color = ZankupTheme.colors.muted
        )
        ranked.firstOrNull()?.speedMps?.let {
            Text(
                stringResource(R.string.matches_best, formats.unit.speedFor(it, sport)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun EffortBlock(e: EffortOut, onOpen: () -> Unit) {
    val status =
        when (e.status) {
            EffortOut.Status.BELOW -> R.string.effort_below
            EffortOut.Status.ABOVE -> R.string.effort_above
            else -> R.string.effort_in
        }
    Block(title = stringResource(R.string.relative_effort), onClick = onOpen) {
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                "%.0f".format(e.score),
                style = MaterialTheme.typography.displaySmall.merge(Numbers.tabular),
                color = ZankupTheme.colors.fatigue
            )
            Text(stringResource(status), style = MaterialTheme.typography.bodyMedium, color = ZankupTheme.colors.muted)
        }
    }
}

@Composable
internal fun GoalsBlock(goals: List<ZankupApiInsightsGoalOut>, onOpen: () -> Unit) {
    Block(title = stringResource(R.string.goals), onClick = onOpen) {
        goals.forEach { goal ->
            val share = (goal.current / goal.target).toFloat().coerceIn(0f, 1f)
            Column(Modifier.padding(top = 8.dp)) {
                Text(
                    "${stringResource(goalMetric(goal.metric))} · ${stringResource(goalPeriod(goal.period))}",
                    style = MaterialTheme.typography.labelLarge
                )
                LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                Text(
                    "%.0f %%".format(share * 100),
                    style = MaterialTheme.typography.labelSmall,
                    color = ZankupTheme.colors.muted
                )
            }
        }
    }
}

@Composable
internal fun ResultsBlock(context: ActivityContext, onBests: (String?) -> Unit) {
    val formats = LocalFormats.current
    Block(title = stringResource(R.string.results), onClick = { onBests(null) }) {
        context.medals.forEach { medal ->
            Row(
                Modifier.fillMaxWidth().clickable { onBests(medal.key) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ZankupIcon(ZIcon.Medal, null, filled = true, tint = medalColour(medal.rank))
                Text(medal.key, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    stringResource(
                        if (medal.scope.value ==
                            "all"
                        ) {
                            R.string.medal_all
                        } else {
                            R.string.medal_year
                        },
                        medal.rank
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = ZankupTheme.colors.muted
                )
            }
        }
        context.segments.forEach { segment ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZankupIcon(ZIcon.Route, null, tint = ZankupTheme.colors.muted)
                Text(segment.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    "${formats.duration(segment.elapsedS)} · #${segment.rank}",
                    style = MaterialTheme.typography.labelMedium.merge(Numbers.tabular)
                )
            }
        }
    }
}

@Composable
private fun medalColour(rank: Int) = when (rank) {
    1 -> ZankupTheme.colors.zones[2]
    2 -> ZankupTheme.colors.muted
    else -> ZankupTheme.colors.zones[3]
}

/** The run's splits as bars over its elevation, opening the training analysis. */
@Composable
internal fun AnalysisCard(a: ActivityDetail, drawn: Drawn?, onOpen: () -> Unit) {
    val formats = LocalFormats.current
    val speeds = a.splits.map { if (it.timeS > 0) it.distanceM / it.timeS else 0.0 }
    val fastest = speeds.maxOrNull() ?: 0.0
    val average = (a.distanceM ?: 0.0) / (a.movingS ?: a.durationS)
    Block(title = stringResource(R.string.training_analysis), onClick = onOpen) {
        BarChart(
            bars = a.splits.mapIndexed { i, split ->
                Bar(
                    split.timeS.toFloat() / (split.distanceM / 1000).toFloat(),
                    "${i + 1}",
                    weight = split.distanceM.toFloat()
                )
            },
            selected = null,
            onSelect = {},
            format = { formatDuration(it.toDouble()) },
            color = ZankupTheme.colors.sport.ride,
            inverted = true,
            height = 140.dp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Pair(stringResource(R.string.average_split), formats.unit.pace(average))
            Pair(stringResource(R.string.fastest_split), formats.unit.pace(fastest))
        }
    }
}

@Composable
internal fun SplitsTable(a: ActivityDetail) {
    val formats = LocalFormats.current
    Block(title = stringResource(R.string.splits)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            listOf(R.string.col_km, R.string.col_pace, R.string.col_elev, R.string.col_hr).forEachIndexed { i, label ->
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = ZankupTheme.colors.muted,
                    modifier = Modifier.weight(
                        if (i ==
                            1
                        ) {
                            1.4f
                        } else {
                            1f
                        }
                    )
                )
            }
        }
        a.splits.forEachIndexed { i, split ->
            HorizontalDivider(color = ZankupTheme.colors.line)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                val style = MaterialTheme.typography.bodyMedium.merge(Numbers.tabular)
                Text("${i + 1}", style = style, modifier = Modifier.weight(1f))
                Text(
                    if (split.timeS >
                        0
                    ) {
                        formats.unit.pace(split.distanceM / split.timeS)
                    } else {
                        "—"
                    },
                    style = style,
                    modifier = Modifier.weight(1.4f)
                )
                Text(
                    split.elevationChangeM?.let {
                        "%+.0f".format(it)
                    } ?: "—",
                    style = style,
                    modifier = Modifier.weight(1f)
                )
                Text(split.avgHr?.toString() ?: "—", style = style, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun PowerCurveCard(a: ActivityDetail, model: ActivityModel) {
    val mine = a.metrics?.powerCurve.orEmpty()
    if (mine.isEmpty()) return
    val all by model.allTimePower.state.collectAsStateWithLifecycle(initialValue = null)
    val curves =
        listOfNotNull(
            Curve(
                stringResource(R.string.this_activity),
                mine.map {
                    it.durationS.toFloat() to it.value.toFloat()
                },
                ZankupTheme.colors.sport.strength
            ),
            all?.data?.takeIf { it.isNotEmpty() }?.let { points ->
                Curve(
                    stringResource(R.string.all_time),
                    points.map {
                        it.durationS.toFloat() to it.value.toFloat()
                    },
                    ZankupTheme.colors.muted
                )
            }
        )
    Block(title = stringResource(R.string.power_curve)) {
        CurveChart(curves, format = { "%.0f W".format(it) }, durationLabel = ::durationLabel)
    }
}

internal fun durationLabel(seconds: Float): String = when {
    seconds < 60 -> "${seconds.roundToInt()} s"
    seconds < 3600 -> "${(seconds / 60).roundToInt()} min"
    else -> "${"%.1f".format(seconds / 3600)} h"
}

@Composable
internal fun PowerFigures(power: PowerOut) {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Pair(stringResource(R.string.work), "%.0f kJ".format(power.workKj))
        power.intensity?.let { Pair(stringResource(R.string.intensity), "%.2f".format(it)) }
        power.load?.let { Pair(stringResource(R.string.load), "%.0f".format(it)) }
    }
}

@Composable
internal fun PaceFigures(a: ActivityDetail) {
    val formats = LocalFormats.current
    val fastest = a.splits.filter { it.timeS > 0 }.maxOfOrNull { it.distanceM / it.timeS }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Pair(stringResource(R.string.fig_moving), formats.duration(a.movingS ?: a.durationS))
        Pair(stringResource(R.string.fig_elapsed), formats.duration(a.durationS))
        fastest?.let { Pair(stringResource(R.string.fastest_split), formats.unit.pace(it)) }
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
    val ascent = drawn.altitude?.let { alt ->
        (from until to).sumOf { i ->
            (alt[i + 1] - alt[i]).takeIf {
                it.isFinite() &&
                    it > 0
            }?.toDouble()
                ?: 0.0
        }
    }
    val hr = drawn.heartRate?.slice(from..to)?.filter { it.isFinite() }?.takeIf { it.isNotEmpty() }?.average()
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Block(title = stringResource(R.string.section)) {
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Pair(stringResource(R.string.fig_distance), formats.distance(metres))
            Pair(stringResource(R.string.fig_moving), formats.duration(seconds))
            Pair(
                stringResource(if (sport.usesPace) R.string.fig_pace else R.string.fig_speed),
                formats.speedFor(sport, metres, seconds)
            )
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ascent?.let { Pair(stringResource(R.string.fig_ascent), formats.elevation(it)) }
            hr?.let { Pair(stringResource(R.string.fig_hr), "%.0f bpm".format(it)) }
        }
        if (hasGps && !saved) {
            if (naming) {
                OutlinedTextField(name, {
                    name = it
                }, label = {
                    Text(stringResource(R.string.segment_name))
                }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { scope.launch { saved = model.saveSegment(from, to, name.trim()) } },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.save)) }
            } else {
                OutlinedButton(onClick = {
                    naming = true
                }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.save_segment)) }
            }
        }
        if (saved) Text(stringResource(R.string.segment_saved), color = ZankupTheme.colors.formPositive)
    }
}

@Composable
internal fun ExercisesGlance(a: ActivityDetail) {
    val exercises = a.sets.filter { it.kind == "active" }.groupBy { it.category ?: "—" }
    Block(title = stringResource(R.string.exercises)) {
        exercises.forEach { (name, sets) ->
            val reps = sets.mapNotNull { it.reps }
            val weight = sets.mapNotNull { it.weightKg }.maxOrNull()
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    name.replace('_', ' ').replaceFirstChar {
                        it.uppercase()
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    listOfNotNull(
                        "${sets.size} × ${reps.maxOrNull() ?: "—"}",
                        weight?.let {
                            "%.1f kg".format(it)
                        }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium.merge(Numbers.tabular),
                    color = ZankupTheme.colors.muted
                )
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
    Block(title = stringResource(R.string.sets)) {
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Pair(stringResource(R.string.sets), "${active.size}")
            Pair(stringResource(R.string.reps), "$reps")
            Pair(stringResource(R.string.volume), "%.0f kg".format(volume))
            Pair(stringResource(R.string.rest), formats.duration(rest))
        }
        active.forEachIndexed { i, set ->
            HorizontalDivider(color = ZankupTheme.colors.line, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
                Text(
                    (set.category ?: "—").replace('_', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    listOfNotNull(
                        set.reps?.let { "$it ×" },
                        set.weightKg?.let {
                            "%.1f kg".format(it)
                        } ?: stringResource(R.string.bodyweight)
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
    Block(title = stringResource(R.string.photos)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
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

@Composable
internal fun SegmentsBlock(a: ActivityDetail) {
    val formats = LocalFormats.current
    Block(title = stringResource(R.string.segments)) {
        a.segments.forEach { pass ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pass.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    "${formats.duration(pass.elapsedS)} · ${pass.rank}/${pass.count}",
                    style = MaterialTheme.typography.bodyMedium.merge(Numbers.tabular),
                    color = if (pass.rank == 1) ZankupTheme.colors.accent else ZankupTheme.colors.muted
                )
            }
        }
    }
}

@Composable
internal fun LapsBlock(a: ActivityDetail, sport: Sport) {
    val formats = LocalFormats.current
    Block(title = stringResource(R.string.laps)) {
        a.laps.forEach { lap ->
            HorizontalDivider(color = ZankupTheme.colors.line)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                val style = MaterialTheme.typography.bodyMedium.merge(Numbers.tabular)
                Text("${lap.idx + 1}", style = style, modifier = Modifier.width(28.dp))
                Text(formats.distance(lap.distanceM), style = style, modifier = Modifier.weight(1f))
                Text(formats.duration(lap.durationS), style = style, modifier = Modifier.weight(1f))
                Text(
                    formats.speedFor(sport, lap.distanceM, lap.durationS),
                    style = style,
                    modifier = Modifier.weight(1.2f)
                )
                Text(
                    lap.avgPower?.let {
                        "$it W"
                    } ?: lap.avgHr?.let { "$it bpm" } ?: "—",
                    style = style,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun Description(text: String) {
    Block(title = stringResource(R.string.description)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun BestEfforts(a: ActivityDetail) {
    val formats = LocalFormats.current
    Block(title = stringResource(R.string.best_efforts)) {
        a.bestEfforts.forEach { best ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                val style = MaterialTheme.typography.bodyMedium.merge(Numbers.tabular)
                Text(formats.distance(best.distanceM), style = style, modifier = Modifier.weight(1f))
                Text(formats.duration(best.timeS), style = style, modifier = Modifier.weight(1f))
                Text(
                    formats.unit.pace(best.distanceM / best.timeS),
                    style = style,
                    color = ZankupTheme.colors.muted,
                    modifier = Modifier.weight(1f)
                )
            }
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

/** A goal's metric and period as words; anything new reads as the generic one. */
internal fun goalMetric(key: String) = when (key) {
    "distance" -> R.string.goal_distance
    "time" -> R.string.goal_time
    "ascent" -> R.string.goal_ascent
    else -> R.string.goal_count
}

internal fun goalPeriod(key: String) = when (key) {
    "month" -> R.string.goal_month
    "year" -> R.string.goal_year
    else -> R.string.goal_week
}
