package com.zankup.android.progress.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.CalendarActivity
import com.zankup.android.api.model.DeviceUse
import com.zankup.android.charts.ChartCursor
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.EffortStatus
import com.zankup.android.domain.Sport
import com.zankup.android.progress.ProgressModel
import com.zankup.android.progress.R
import com.zankup.android.progress.logic.Direction
import com.zankup.android.progress.logic.HR_ZONE_COLOURS
import com.zankup.android.progress.logic.Point
import com.zankup.android.progress.logic.RUN_KEYS
import com.zankup.android.progress.logic.Race
import com.zankup.android.progress.logic.VolumeMetric
import com.zankup.android.progress.logic.bestValue
import com.zankup.android.progress.logic.bucketValue
import com.zankup.android.progress.logic.cardMedals
import com.zankup.android.progress.logic.changeText
import com.zankup.android.progress.logic.day
import com.zankup.android.progress.logic.daysAgo
import com.zankup.android.progress.logic.dotSize
import com.zankup.android.progress.logic.durationLabel
import com.zankup.android.progress.logic.effortWeeks
import com.zankup.android.progress.logic.fitnessChange
import com.zankup.android.progress.logic.goalFraction
import com.zankup.android.progress.logic.hoursMinutes
import com.zankup.android.progress.logic.metricsFor
import com.zankup.android.progress.logic.monthLabels
import com.zankup.android.progress.logic.monthOf
import com.zankup.android.progress.logic.raceTime
import com.zankup.android.progress.logic.series
import com.zankup.android.progress.logic.shareChange
import com.zankup.android.progress.logic.sportsIn
import com.zankup.android.progress.logic.strongest
import com.zankup.android.progress.logic.topZone
import com.zankup.android.progress.logic.untilToday
import com.zankup.android.progress.logic.valueOn
import com.zankup.android.progress.logic.weekAt
import com.zankup.android.progress.logic.weekDays
import com.zankup.android.progress.logic.weekLoads
import com.zankup.android.progress.logic.weekTitle
import com.zankup.android.progress.logic.weekdayLetter
import com.zankup.android.progress.logic.zoneScale
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.sportOf
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Without a weight in the profile, skills are read for a rider of this much. */
internal const val REFERENCE_KG = 75.0

/** A status's colour: blue under the range, the accent in it, orange and red over. */
@Composable
internal fun statusColor(status: EffortStatus): Color {
    val colors = ZankupTheme.colors
    return when (status) {
        EffortStatus.IN -> colors.accent
        EffortStatus.BELOW -> colors.fitness
        EffortStatus.ABOVE -> colors.zones[3]
        EffortStatus.FAR_ABOVE -> colors.zones[4]
    }
}

/** Text on a status colour. */
@Composable
internal fun onStatus(status: EffortStatus): Color =
    if (status == EffortStatus.IN) ZankupTheme.colors.accentFg else Color.White

@Composable
internal fun statusWord(status: EffortStatus): String = stringResource(
    when (status) {
        EffortStatus.BELOW -> R.string.progress_effort_status_below
        EffortStatus.IN -> R.string.progress_effort_status_in
        EffortStatus.ABOVE -> R.string.progress_effort_status_above
        EffortStatus.FAR_ABOVE -> R.string.progress_effort_status_far
    }
)

/** Volume (as Strava's first card): a chip per sport, this week's totals and the last 12 weeks. */
@Composable
internal fun VolumeCard(model: ProgressModel, onVolume: (String?) -> Unit) {
    val volume by model.volume.collectAsStateWithLifecycle()
    if (volume.loading) return CardSkeleton(6)
    val buckets = volume.data.orEmpty()
    val format = volumeFormat()
    val locale = LocalFormats.current.locale
    var sport by rememberSaveable { mutableStateOf<String?>(null) }
    val picked = sport?.let(::sportOf)
    val cursor = remember(sport, buckets.size) { ChartCursor() }
    val metrics = metricsFor(picked)
    val chartMetric = metrics.first()
    val values = buckets.map { bucketValue(it, chartMetric, picked) }
    val ticks = format.ticks(values.maxOrNull() ?: 0.0, chartMetric, 2)
    val starts = buckets.mapNotNull { day(it.start) }
    val last12 = stringResource(R.string.progress_last12_weeks)
    ProgressCard {
        Stack(gap = 14) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<Sport?>(null) + sportsIn(buckets)).forEach { s ->
                    ProgressChip(sportLabel(s), on = s == picked, icon = sportIcon(s), onClick = { sport = s?.key })
                }
            }
            WeekTotals(cursor, buckets.size) { index ->
                val week = buckets.getOrNull(index)
                Text(
                    if (index == buckets.size - 1) {
                        stringResource(R.string.progress_this_week)
                    } else {
                        week?.let { day(it.start) }?.let { weekTitle(it, locale) }.orEmpty()
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = ZankupTheme.colors.fg
                )
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    metrics.forEach { m ->
                        Column {
                            Text(metricShort(m), fontSize = 13.sp, color = ZankupTheme.colors.fg)
                            Figure(format.value(m, week?.let { bucketValue(it, m, picked) } ?: 0.0))
                        }
                    }
                }
            }
            Text(last12, fontSize = 13.sp, color = ZankupTheme.colors.fg)
            VolumeChart(
                values = values,
                ticks = ticks,
                tickLabel = { format.tick(chartMetric, it) },
                labels = monthLabels(starts, locale),
                description = "$last12: ${values.joinToString { format.value(chartMetric, it) }}",
                cursor = cursor,
                height = 190
            )
            OutlineButton(onClick = { onVolume(sport) }) {
                Text(
                    stringResource(R.string.progress_more_detail),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = ZankupTheme.colors.fg
                )
            }
        }
    }
}

/** The picked week's title and totals: the only part that recomposes as the finger moves. */
@Composable
private fun WeekTotals(cursor: ChartCursor, count: Int, content: @Composable (Int) -> Unit) {
    val index = cursor.index.takeIf { it in 0 until count } ?: (count - 1)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { content(index) }
}

/** Series and the Training log, side by side and as tall as each other. */
@Composable
internal fun SeriesAndLog(model: ProgressModel, onSeries: () -> Unit, onLog: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight()) { SeriesCard(model, onSeries) }
        Box(Modifier.weight(1f).fillMaxHeight()) { LogCard(model, onLog) }
    }
}

@Composable
private fun SeriesCard(model: ProgressModel, onOpen: () -> Unit) {
    val streak by model.streak.collectAsStateWithLifecycle()
    if (streak.loading) return CardSkeleton(4)
    val weeks = streak.data?.weeks ?: 0
    val title = stringResource(R.string.progress_series)
    val words = pluralStringResource(R.plurals.progress_streak_weeks, weeks, weeks)
    ProgressCard(Modifier.fillMaxHeight(), onClick = onOpen, description = "$title: $words") {
        CardHeader(title)
        Column(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Flame(weeks, 88)
            Text(
                pluralStringResource(R.plurals.progress_weeks_word, weeks),
                color = ZankupTheme.colors.accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/** A day's colour: its sport's, or a neutral grey when it has more than one activity. */
@Composable
internal fun dayColor(activities: List<CalendarActivity>): Color = when {
    activities.size > 1 -> ZankupTheme.colors.muted
    activities.isNotEmpty() -> sportColor(sportOf(activities.first().sport))
    else -> ZankupTheme.colors.muted
}

@Composable
private fun LogCard(model: ProgressModel, onOpen: () -> Unit) {
    val calendar by model.log.collectAsStateWithLifecycle()
    if (calendar.loading) return CardSkeleton(4)
    val formats = LocalFormats.current
    val today = model.today
    val days = calendar.data.orEmpty().associate { it.day.take(10) to it.activities }
    fun week(monday: LocalDate) = weekDays(monday).map { it to days[it.toString()].orEmpty() }
    val thisWeek = week(weekAt(0, today))
    val lastWeek = week(weekAt(1, today))
    fun time(w: List<Pair<LocalDate, List<CalendarActivity>>>) =
        formats.hoursMinutes(w.sumOf { (_, a) -> a.sumOf { it.movingS } })
    val title = stringResource(R.string.progress_log)
    val thisLabel = stringResource(R.string.progress_this_week)
    ProgressCard(Modifier.fillMaxHeight(), onClick = onOpen, description = "$title: $thisLabel ${time(thisWeek)}") {
        Stack(gap = 12) {
            CardHeader(title, icon = ZIcon.Calendar, iconSize = 22)
            LogLine(thisLabel, time(thisWeek))
            Dots(thisWeek, today)
            Dots(lastWeek, today)
            LogLine(stringResource(R.string.progress_last_week), time(lastWeek))
        }
    }
}

@Composable
private fun LogLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        // The label gives way to the time, which is never cut and keeps a gap from it.
        Text(
            label,
            Modifier.weight(1f, fill = false).padding(end = 8.dp),
            fontSize = 14.sp,
            color = ZankupTheme.colors.fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(value, fontSize = 14.sp, color = ZankupTheme.colors.fg, maxLines = 1, softWrap = false)
    }
}

/** A week as seven dots for the card: a speck on a rest day, a ring today if nothing yet. */
@Composable
private fun Dots(days: List<Pair<LocalDate, List<CalendarActivity>>>, today: LocalDate) {
    val colors = ZankupTheme.colors
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        days.forEach { (d, activities) ->
            val seconds = activities.sumOf { it.movingS }
            val size = dotSize(seconds, d == today)
            val color = dayColor(activities)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val dot = Modifier.size(size.dp).clip(CircleShape)
                Box(
                    when {
                        seconds > 0 -> dot.background(color)
                        d == today -> dot.border(2.dp, colors.muted, CircleShape)
                        else -> dot.background(colors.muted)
                    }
                )
            }
        }
    }
}

/** Fitness: today's score, its change over the month and the month's line. */
@Composable
internal fun FitnessCard(model: ProgressModel, onOpen: () -> Unit) {
    val fitness by model.fitness.collectAsStateWithLifecycle()
    if (fitness.loading) return CardSkeleton(3)
    val values = untilToday(fitness.data.orEmpty(), model.today).map { it.ctl }
    val now = values.lastOrNull()
    val title = stringResource(R.string.progress_fitness)
    val score = stringResource(R.string.progress_score)
    ProgressCard(onClick = onOpen, description = "$title, $score ${now?.roundToInt() ?: "–"}") {
        CardHeader(title, icon = ZIcon.Progress, note = stringResource(R.string.progress_last_month))
        if (now == null) {
            Muted(stringResource(R.string.progress_no_fitness), Modifier.padding(top = 12.dp))
            return@ProgressCard
        }
        val change = fitnessChange(values.first(), now)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Label(score)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BigFigure(now.roundToInt().toString())
                    if (change.direction != Direction.NONE) {
                        ChangePill(change.direction == Direction.UP, abs(change.points).toString(), good = true)
                    }
                }
            }
            if (values.size > 1) Sparkline(values, first = values.first().roundToInt().toString())
        }
    }
}

/** Relative effort: this week's score against its range, last week's, both built up day by day. */
@Composable
internal fun EffortCard(model: ProgressModel, onOpen: () -> Unit) {
    val volume by model.effortWeeks.collectAsStateWithLifecycle()
    val daily by model.effortDays.collectAsStateWithLifecycle()
    if (volume.loading || daily.loading) return CardSkeleton(4)
    val weeks = effortWeeks(volume.data.orEmpty())
    val now = weeks.lastOrNull()
    val before = weeks.getOrNull(weeks.size - 2)
    val title = stringResource(R.string.progress_effort)
    ProgressCard(onClick = onOpen, description = "$title: ${now?.score ?: 0}") {
        CardHeader(title, icon = ZIcon.Gauge, note = stringResource(R.string.progress_this_week))
        if (now == null) {
            Muted(stringResource(R.string.progress_no_effort), Modifier.padding(top = 12.dp))
            return@ProgressCard
        }
        val locale = LocalFormats.current.locale
        val today = model.today
        val monday = weekAt(0, today)
        Row(
            Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(stringResource(R.string.progress_score))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    BigFigure(now.score.toString())
                    if (now.range != null) {
                        Text(
                            statusWord(now.status),
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor(now.status))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            color = onStatus(now.status),
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }
                if (before != null) Label(stringResource(R.string.progress_last_week_score, before.score))
            }
            WeekSoFar(
                thisWeek = weekLoads(daily.data.orEmpty(), monday),
                lastWeek = weekLoads(daily.data.orEmpty(), weekAt(1, today)),
                range = now.range,
                color = statusColor(now.status),
                letters = weekDays(monday).map { weekdayLetter(it, locale) },
                today = today.dayOfWeek.value - 1
            )
        }
    }
}

/** Each race's history, oldest first. */
internal fun histories(estimates: List<com.zankup.android.api.model.EstimateOut>): Map<String, List<Point>> =
    estimates.associate { e -> e.kind to e.history.mapNotNull { h -> day(h.day)?.let { Point(it, h.value) } } }

/** How much a prediction changed: ▼ faster (in green), ▲ slower. */
@Composable
internal fun RaceChange(seconds: Double) = ChangePill(up = seconds >= 0, text = changeText(seconds), good = seconds < 0)

/** Race predictions: the 5 km prediction and its last month. */
@Composable
internal fun PredictionsCard(model: ProgressModel, onOpen: () -> Unit) {
    val estimates by model.estimates.collectAsStateWithLifecycle()
    if (estimates.loading) return CardSkeleton(3)
    val today = model.today
    val history = histories(estimates.data.orEmpty())[Race.R5K.kind].orEmpty()
    val line = series(history, today.minusDays(30), today)
    val now = valueOn(history, today)
    val before = line.firstOrNull()?.value
    val title = stringResource(R.string.progress_predictions)
    val race = stringResource(R.string.progress_race_race_5k)
    ProgressCard(onClick = onOpen, description = "$title: $race ${now?.let(::raceTime) ?: "–"}") {
        CardHeader(title, icon = ZIcon.Timer, note = stringResource(R.string.progress_last_month))
        if (now == null || before == null) {
            Muted(stringResource(R.string.progress_no_predictions), Modifier.padding(top = 12.dp))
            return@ProgressCard
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Label(race)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BigFigure(raceTime(now))
                    RaceChange(now - before)
                }
            }
            val values = line.map { it.value }
            Sparkline(
                values,
                first = raceTime(values.first()),
                inverted = true,
                height = 70,
                fill = false,
                guide = false,
                left = 56
            )
        }
    }
}

internal fun goalMetric(metric: String): VolumeMetric = when (metric) {
    "distance" -> VolumeMetric.DISTANCE
    "ascent" -> VolumeMetric.ASCENT
    "count" -> VolumeMetric.COUNT
    else -> VolumeMetric.TIME
}

/** Goals: up to three as rings. */
@Composable
internal fun GoalsCard(model: ProgressModel, onOpen: () -> Unit) {
    val goals by model.goals.collectAsStateWithLifecycle()
    if (goals.loading) return CardSkeleton(4)
    val list = goals.data.orEmpty()
    val format = volumeFormat()
    val title = stringResource(R.string.progress_goals)
    ProgressCard(onClick = onOpen, description = title) {
        CardHeader(title, icon = ZIcon.Goal)
        if (list.isEmpty()) {
            Muted(stringResource(R.string.progress_no_goals), Modifier.padding(top = 8.dp))
            return@ProgressCard
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            list.take(3).forEach { g ->
                val metric = goalMetric(g.metric)
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GoalRing(goalFraction(g.current, g.target), sportIcon(g.sport?.let(::sportOf)))
                    BigFigure(format.value(metric, g.current, 1), size = 17)
                    val target = format.value(metric, g.target, 0)
                    Text(
                        stringResource(
                            when (g.period) {
                                "week" -> R.string.progress_goal_per_week
                                "month" -> R.string.progress_goal_per_month
                                else -> R.string.progress_goal_per_year
                            },
                            target
                        ),
                        fontSize = 13.sp,
                        color = ZankupTheme.colors.fg,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Training zones: where most of the last month's heart rate time went, a bar per zone. */
@Composable
internal fun ZonesCard(model: ProgressModel, onOpen: () -> Unit) {
    val zones by model.zones.collectAsStateWithLifecycle()
    if (zones.loading) return CardSkeleton(3)
    val colors = ZankupTheme.colors
    val seconds = zones.data?.seconds.orEmpty()
    val top = topZone(seconds)
    val change = top?.let { shareChange(seconds, zones.data?.previous.orEmpty(), it.index) }
    val most = max(1.0, seconds.maxOrNull() ?: 0.0)
    val title = stringResource(R.string.progress_zones)
    val headline = top?.let { stringResource(R.string.progress_zones_top, it.share, it.index + 1) }
    ProgressCard(onClick = onOpen, description = "$title: ${headline ?: stringResource(R.string.progress_no_zones)}") {
        CardHeader(title, icon = ZIcon.Hrv, note = stringResource(R.string.progress_last_month))
        if (top == null || headline == null) {
            Muted(stringResource(R.string.progress_no_zones), Modifier.padding(top = 12.dp))
            return@ProgressCard
        }
        val palette = zoneScale(HR_ZONE_COLOURS, seconds.size)
        Row(
            Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Label(stringResource(R.string.progress_most_time))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigFigure(headline)
                    if (change != null && change != 0) {
                        Box(Modifier.align(Alignment.CenterVertically)) {
                            ChangePill(change >= 0, "${abs(change)} %", good = false)
                        }
                    }
                }
            }
            Column(Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                seconds.indices.reversed().forEach { i ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            if (i == top.index) "Z${i + 1}" else "",
                            modifier = Modifier.width(18.dp),
                            color = colors.muted,
                            fontSize = 11.sp
                        )
                        val share = (max(12.0, seconds[i] / most * 100) * 0.8 / 100).toFloat()
                        Box(
                            Modifier
                                .fillMaxWidth(share)
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (i == top.index) Color(palette[i]) else colors.surface2)
                        )
                    }
                }
            }
        }
    }
}

/** Skills: the strongest skill beside the radars of the last eight weeks over all time. */
@Composable
internal fun SkillsCard(model: ProgressModel, onOpen: () -> Unit) {
    val recent by model.recentPower.collectAsStateWithLifecycle()
    val all by model.allPower.collectAsStateWithLifecycle()
    val me by model.me.collectAsStateWithLifecycle()
    if (recent.loading || all.loading) return CardSkeleton(3)
    val weight = me.data?.weightKg ?: REFERENCE_KG
    val recentBests = recent.data.orEmpty().associate { it.durationS to it.`value` }
    val allBests = all.data.orEmpty().associate { it.durationS to it.`value` }
    val best = strongest(recentBests, weight)
    val title = stringResource(R.string.progress_skills)
    val skillName = best?.let { skillName(it.skill) }
    val description = if (best !=
        null
    ) {
        "$title: $skillName ${durationLabel(best.duration)} ${best.watts.roundToInt()} W"
    } else {
        title
    }
    ProgressCard(onClick = onOpen, description = description) {
        CardHeader(title, icon = ZIcon.Bolt)
        if (best == null) {
            Muted(stringResource(R.string.progress_no_skills), Modifier.padding(top = 12.dp))
            return@ProgressCard
        }
        Row(
            Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label("$skillName · ${durationLabel(best.duration)}")
                BigFigure("${best.watts.roundToInt()} W")
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Legend(RadarRecent, stringResource(R.string.progress_skill_span_recent))
                    Legend(RadarAll, stringResource(R.string.progress_skill_span_all))
                }
            }
            SkillsRadar(
                150,
                listOf(RadarShape(allBests, RadarAll, weight), RadarShape(recentBests, RadarRecent, weight))
            )
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Dot(color)
        Muted(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun skillName(skill: com.zankup.android.domain.Skill): String = stringResource(
    when (skill) {
        com.zankup.android.domain.Skill.SPRINT -> R.string.progress_skill_sprint
        com.zankup.android.domain.Skill.ATTACK -> R.string.progress_skill_attack
        com.zankup.android.domain.Skill.CLIMB -> R.string.progress_skill_climb
    }
)

/** Best times: the medals of the last twelve months, one per measure and up to three. */
@Composable
internal fun BestsCard(model: ProgressModel, onOpen: () -> Unit) {
    val medals by model.medals.collectAsStateWithLifecycle()
    if (medals.loading) return CardSkeleton(3)
    val imperial = imperial()
    val locale = LocalFormats.current.locale
    val shown = cardMedals(medals.data.orEmpty(), { it.key }, imperial)
    val title = stringResource(R.string.progress_bests)
    ProgressCard(onClick = onOpen, description = title) {
        CardHeader(title, icon = ZIcon.Trophy)
        if (shown.isEmpty()) {
            Muted(stringResource(R.string.progress_no_recent_bests), Modifier.padding(top = 12.dp))
            return@ProgressCard
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            shown.forEach { m ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Medal(m.rank, 44)
                    Text(bestName(m.key), fontSize = 13.sp, color = ZankupTheme.colors.fg, maxLines = 1)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val sport = if (m.key in RUN_KEYS) Sport.RUN else Sport.RIDE
                        ZankupIcon(
                            sportIcon(sport),
                            contentDescription = null,
                            tint = ZankupTheme.colors.fg,
                            size = 18.dp
                        )
                        BigFigure(bestValue(m.key, m.`value`, locale, imperial), size = 17)
                    }
                    Muted(
                        stringResource(
                            if (m.scope.value == "all") R.string.progress_all_time_best else R.string.progress_year_best
                        ),
                        style = SmallText
                    )
                }
            }
        }
    }
}

/** Monthly activities: this month's time, last month's, and the two lines small. */
@Composable
internal fun MonthlyCard(model: ProgressModel, onOpen: () -> Unit) {
    val calendar by model.months.collectAsStateWithLifecycle()
    if (calendar.loading) return CardSkeleton(3)
    val today = model.today
    val days = calendar.data.orEmpty()
    val current = monthOf(days, YearMonth.from(today), today)
    val previous = monthOf(days, YearMonth.from(today).minusMonths(1), today)
    ProgressCard(onClick = onOpen) {
        Stack(gap = 12) {
            CardHeader(
                stringResource(R.string.progress_monthly_title),
                icon = ZIcon.Calendar,
                note = stringResource(R.string.progress_monthly_this_month)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(0.58f)) {
                    Muted(stringResource(R.string.progress_monthly_time))
                    BigFigure(hoursMinutes(current.time))
                    Muted(stringResource(R.string.progress_monthly_last_month, hoursMinutes(previous.time)))
                }
                MonthChart(current, previous, Modifier.weight(0.42f))
            }
        }
    }
}

/** The day a device was last used, in the phone's time zone. */
private fun lastUsedDay(iso: String): LocalDate? = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
}.recoverCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
}.getOrNull() ?: day(iso)

@Composable
private fun deviceName(d: DeviceUse): String = d.name ?: when (d.source) {
    "garmin" -> "Garmin"
    "strava" -> "Strava"
    "google_health" -> "Google Health"
    "health_connect" -> "Health Connect"
    else -> stringResource(R.string.progress_sources_file)
}

/** "today", "1 day ago", "2 weeks ago". */
@Composable
private fun lastUsed(iso: String, today: LocalDate): String {
    val days = lastUsedDay(iso)?.let { daysAgo(it, today) } ?: 0
    return when {
        days == 0 -> stringResource(R.string.progress_sources_today)
        days < 7 -> pluralStringResource(R.plurals.progress_sources_days_ago, days, days)
        else -> pluralStringResource(R.plurals.progress_sources_weeks_ago, days / 7, days / 7)
    }
}

/** Data sources: the devices and services that recorded in the last 30 days, and a way into them. */
@Composable
internal fun DataSourcesCard(model: ProgressModel, onOpen: () -> Unit) {
    val devices by model.devices.collectAsStateWithLifecycle()
    if (devices.loading) return CardSkeleton(4)
    val colors = ZankupTheme.colors
    ProgressCard {
        Text(
            stringResource(R.string.progress_sources_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.fg
        )
        Muted(stringResource(R.string.progress_sources_note))
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            devices.data.orEmpty().forEach { d ->
                SourceRow(
                    ZIcon.Devices,
                    deviceName(d),
                    stringResource(R.string.progress_sources_last_used, lastUsed(d.lastUsed, model.today))
                )
            }
            SourceRow(
                ZIcon.Link,
                stringResource(R.string.progress_sources_connect),
                stringResource(R.string.progress_sources_connect_note),
                onOpen
            )
        }
        Text(
            stringResource(R.string.progress_sources_manage),
            modifier = Modifier
                .align(Alignment.End)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpen)
                .padding(top = 8.dp, start = 4.dp, end = 4.dp),
            color = colors.accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SourceRow(icon: ZIcon, title: String, note: String, onClick: (() -> Unit)? = null) {
    val colors = ZankupTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ZankupIcon(icon, contentDescription = null, tint = colors.fg, size = 26.dp)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = colors.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(note, fontSize = 13.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
