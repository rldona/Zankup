package com.zankup.android.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.FitnessDay
import com.zankup.android.charts.ChartCursor
import com.zankup.android.designsystem.components.SectionDivider
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.progress.logic.DayRange
import com.zankup.android.progress.logic.Direction
import com.zankup.android.progress.logic.FitnessRange
import com.zankup.android.progress.logic.day
import com.zankup.android.progress.logic.fitnessChange
import com.zankup.android.progress.logic.rangePeriod
import com.zankup.android.progress.logic.untilToday
import com.zankup.android.progress.ui.BigFigure
import com.zankup.android.progress.ui.ChartSkeleton
import com.zankup.android.progress.ui.FitnessChart
import com.zankup.android.progress.ui.InfoButton
import com.zankup.android.progress.ui.Label
import com.zankup.android.progress.ui.Muted
import com.zankup.android.progress.ui.SmallText
import com.zankup.android.progress.ui.SportTile
import com.zankup.android.progress.ui.activitiesCount
import com.zankup.android.progress.ui.date
import com.zankup.android.progress.ui.longDate
import com.zankup.android.ui.sportOf
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Training, from Progress's Fitness card: the fitness score over a month to two years (the range kept
 * for next time), how much it changed from the range's first day to the day picked (today unless
 * another is touched), and that day's activities. Not scrolled: the chart takes the room between.
 */
@Composable
fun FitnessScreen(
    onBack: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier,
    model: FitnessModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { model.refresh() }
    val colors = ZankupTheme.colors
    val today = model.today
    var range by rememberSaveable { mutableStateOf(model.range) }
    val fitness by remember(range) { model.queries.fitness(rangePeriod(range, today)) }.collectAsStateWithLifecycle()
    // The last range stays on screen while another loads, so its line can turn into the new one.
    var shown by remember { mutableStateOf<List<FitnessDay>?>(null) }
    LaunchedEffect(fitness.data) { fitness.data?.let { shown = it } }
    val days = untilToday(shown.orEmpty(), today)
    val cursor = remember(range) { ChartCursor() }
    // The picked day, for the words and the activities (the chart reads the cursor itself while drawing).
    var picked by remember(range) { mutableIntStateOf(-1) }

    Scaffold(
        modifier = modifier,
        containerColor = colors.page,
        topBar = { ZankupTopBar(stringResource(R.string.progress_training), onBack = onBack) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.progress_fitness),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.fg
                )
                InfoButton(stringResource(R.string.progress_fitness_info))
            }
            Text(
                stringResource(R.string.progress_fitness_intro),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = colors.fg
            )
            RangeTabs(range) {
                range = it
                model.range = it
            }
            val last = days.size - 1
            val index = (if (picked < 0) last else picked).coerceAtMost(last)
            val chosen = days.getOrNull(index)
            when {
                shown == null && fitness.loading -> ChartSkeleton(360)
                chosen == null -> Muted(stringResource(R.string.progress_no_fitness))
                else -> {
                    val change = fitnessChange(days.first().ctl, chosen.ctl)
                    Column {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val arrow = when (change.direction) {
                                Direction.UP -> "▴"
                                Direction.DOWN -> "▾"
                                Direction.NONE -> ""
                            }
                            BigFigure("$arrow${change.percent} %")
                            Text(
                                if (change.direction == Direction.NONE) {
                                    stringResource(R.string.progress_no_change)
                                } else {
                                    stringResource(
                                        R.string.progress_points,
                                        if (change.points > 0) "+${change.points}" else change.points.toString()
                                    )
                                },
                                Modifier.padding(bottom = 6.dp),
                                color = if (change.direction == Direction.UP) colors.formPositive else colors.fg,
                                fontSize = 14.sp
                            )
                        }
                        val first = day(days.first().day) ?: today
                        val at = day(chosen.day) ?: today
                        Label(
                            if (index == last) {
                                lastWords(range)
                            } else {
                                stringResource(R.string.progress_between, date(first, "d MMM"), date(at, "d MMM"))
                            }
                        )
                    }
                    val short = range == FitnessRange.M1 || range == FitnessRange.M3
                    val todayWord = stringResource(R.string.progress_today)
                    val locale = com.zankup.android.ui.LocalFormats.current.locale
                    val labels = remember(days, today, short, todayWord, locale) {
                        val pattern = if (short) "d MMM" else "MMM"
                        val formatter = java.time.format.DateTimeFormatter.ofPattern(pattern, locale)
                        days.map { d ->
                            val at = day(d.day) ?: today
                            if (at == today) todayWord else formatter.format(at)
                        }
                    }
                    val values = remember(days) { days.map { it.ctl } }
                    val loads = remember(days) { days.map { it.load } }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("fitness_chart")) {
                        FitnessChart(
                            values = values,
                            loads = loads,
                            labels = labels,
                            cursor = cursor,
                            showLoads = range == FitnessRange.M1,
                            description = "${stringResource(R.string.progress_fitness)} ${chosen.ctl.roundToInt()}, " +
                                longDate(day(chosen.day) ?: today),
                            onPick = { picked = it },
                            height = maxOf(240, maxHeight.value.toInt())
                        )
                    }
                    // Pinned to the bottom with just the room one activity needs: the chart keeps the rest.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .heightIn(min = 72.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        DayActivities(model, day(chosen.day) ?: today, onOpenActivity)
                    }
                }
            }
        }
    }
}

@Composable
private fun lastWords(range: FitnessRange): String = stringResource(
    when (range) {
        FitnessRange.M1 -> R.string.progress_last_1m
        FitnessRange.M3 -> R.string.progress_last_3m
        FitnessRange.M6 -> R.string.progress_last_6m
        FitnessRange.Y1 -> R.string.progress_last_1y
        FitnessRange.Y2 -> R.string.progress_last_2y
    }
)

/** The ranges as tabs under a line, the chosen one underlined in the accent. */
@Composable
private fun RangeTabs(range: FitnessRange, onPick: (FitnessRange) -> Unit) {
    val colors = ZankupTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            FitnessRange.entries.forEach { r ->
                val on = r == range
                Column(
                    Modifier.weight(1f).clickable(role = Role.Tab) { onPick(r) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(
                            when (r) {
                                FitnessRange.M1 -> R.string.progress_range_1m
                                FitnessRange.M3 -> R.string.progress_range_3m
                                FitnessRange.M6 -> R.string.progress_range_6m
                                FitnessRange.Y1 -> R.string.progress_range_1y
                                FitnessRange.Y2 -> R.string.progress_range_2y
                            }
                        ),
                        Modifier.padding(vertical = 12.dp),
                        color = if (on) colors.fg else colors.muted,
                        fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(if (on) colors.accent else androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }
        }
        SectionDivider()
    }
}

/** The picked day's activities, each opening its page; more than one, how many until tapped. */
@Composable
private fun DayActivities(model: FitnessModel, day: LocalDate, onOpen: (Long) -> Unit) {
    val colors = ZankupTheme.colors
    val calendar by remember(day) {
        model.queries.calendar(DayRange(day, day.plusDays(1)))
    }.collectAsStateWithLifecycle()
    var expanded by remember(day) { mutableStateOf(false) }
    val activities = calendar.data.orEmpty().firstOrNull { it.day.take(10) == day.toString() }?.activities.orEmpty()
    val whenText = longDate(day)
    when {
        activities.isEmpty() -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ZankupIcon(ZIcon.Timeline, contentDescription = null, tint = colors.muted, size = 30.dp)
            Column {
                Text(stringResource(R.string.progress_no_activities), color = colors.muted, fontSize = 15.sp)
                Muted(whenText, style = SmallText)
            }
        }
        activities.size > 1 && !expanded -> Row(
            Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ZankupIcon(ZIcon.Timeline, contentDescription = null, tint = colors.fg, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(activitiesCount(activities.size), color = colors.fg, fontSize = 15.sp)
                Muted(whenText, style = SmallText)
            }
            ZankupIcon(ZIcon.ArrowDown, contentDescription = null, tint = colors.muted)
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            activities.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(a.id.toLong()) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SportTile(sportOf(a.sport), 36)
                    Column(Modifier.weight(1f)) {
                        Text(a.name ?: a.sport, color = colors.fg, fontSize = 15.sp, maxLines = 1)
                        Muted(whenText, style = SmallText)
                    }
                }
            }
        }
    }
}
