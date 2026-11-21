package com.zankup.android.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.EffortBand
import com.zankup.android.domain.EffortStatus
import com.zankup.android.domain.effortBand
import com.zankup.android.progress.logic.DayRange
import com.zankup.android.progress.logic.day
import com.zankup.android.progress.logic.effortWeeks
import com.zankup.android.progress.logic.monthLabels
import com.zankup.android.progress.logic.weekDays
import com.zankup.android.progress.logic.weekdayLetter
import com.zankup.android.progress.ui.CardSkeleton
import com.zankup.android.progress.ui.ChevronLeft
import com.zankup.android.progress.ui.EffortWeeksChart
import com.zankup.android.progress.ui.InfoButton
import com.zankup.android.progress.ui.Muted
import com.zankup.android.progress.ui.longDate
import com.zankup.android.progress.ui.onStatus
import com.zankup.android.progress.ui.sportIcon
import com.zankup.android.progress.ui.statusColor
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.max
import kotlin.math.roundToInt

private const val WINDOW = 12

/**
 * Relative effort, from Progress (as Strava's): a week on a panel of its status's colour (its score,
 * what it means and its days as bars), the weeks around it with the range each was judged against,
 * and the week's activities with their effort. Swiping the panel, the arrows or touching the chart
 * changes the week.
 */
@Composable
fun EffortScreen(
    onBack: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier,
    model: EffortModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { model.refresh() }
    val colors = ZankupTheme.colors
    val haptics = LocalHapticFeedback.current
    val volume by model.weeks.collectAsStateWithLifecycle()
    val weeks = remember(volume.data) { effortWeeks(volume.data.orEmpty()) }
    var picked by rememberSaveable { mutableStateOf<Int?>(null) }
    // The last week the chart shows (none: the latest). Only the arrows and the swipe move it.
    var end by rememberSaveable { mutableStateOf<Int?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = colors.page,
        topBar = { ZankupTopBar(stringResource(R.string.progress_effort), onBack = onBack) }
    ) { padding ->
        if (volume.loading) {
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardSkeleton(2)
                CardSkeleton(3)
            }
            return@Scaffold
        }
        if (weeks.isEmpty()) {
            Muted(stringResource(R.string.progress_no_effort), Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        val index = (picked ?: weeks.lastIndex).coerceAtMost(weeks.lastIndex)
        val week = weeks[index]
        val last = (end ?: weeks.lastIndex).coerceAtMost(weeks.lastIndex)
        val first = max(0, last - WINDOW + 1)
        fun pick(to: Int) {
            if (to != index) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                picked = to
            }
        }
        fun go(to: Int) {
            val next = to.coerceIn(0, weeks.lastIndex)
            if (next == index) return
            pick(next)
            if (next < first) {
                end = next + WINDOW - 1
            } else if (next > last) {
                end = next
            }
        }
        val bg = statusColor(week.status)
        val fg = onStatus(week.status)
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .pointerInput(index) {
                        var moved = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { moved = 0f },
                            onDragEnd = {
                                if (kotlin.math.abs(moved) >
                                    24.dp.toPx()
                                ) {
                                    go(index + if (moved > 0) -1 else 1)
                                }
                            }
                        ) { change, amount ->
                            moved += amount
                            change.consume()
                        }
                    }.padding(20.dp)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("${longDate(week.monday)} - ${longDate(week.monday.plusDays(6))}", color = fg, fontSize = 14.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        week.score.toString(),
                        color = fg,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        style = Numbers.tabular
                    )
                    InfoButton(stringResource(R.string.progress_effort_info), tint = fg)
                }
                Text(effortTitle(week.status), color = fg, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(effortText(week.status), color = fg, fontSize = 14.sp, lineHeight = 22.sp)
                DayBars(model, week.monday, fg)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { go(index - 1) }, enabled = index > 0) {
                        ChevronLeft(fg, stringResource(R.string.progress_prev_week))
                    }
                    Text(
                        stringResource(R.string.progress_swipe_weeks),
                        Modifier.weight(1f),
                        color = fg,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { go(index + 1) }, enabled = index < weeks.lastIndex) {
                        ZankupIcon(
                            ZIcon.Chevron,
                            stringResource(R.string.progress_next_week),
                            tint = if (index < weeks.lastIndex) fg else Color.Transparent,
                            size = 30.dp
                        )
                    }
                }
            }
            val shown = weeks.subList(first, (first + WINDOW).coerceAtMost(weeks.size))
            val locale = LocalFormats.current.locale
            EffortWeeksChart(
                weeks = shown,
                picked = index - first,
                color = bg,
                labels = monthLabels(shown.map { it.monday }, locale),
                onPick = { pick(first + it) },
                modifier = Modifier.padding(16.dp)
            )
            WeekActivities(model, week.monday, onOpenActivity)
        }
    }
}

@Composable
private fun effortTitle(status: EffortStatus) = stringResource(
    when (status) {
        EffortStatus.BELOW -> R.string.progress_effort_title_below
        EffortStatus.IN -> R.string.progress_effort_title_in
        EffortStatus.ABOVE -> R.string.progress_effort_title_above
        EffortStatus.FAR_ABOVE -> R.string.progress_effort_title_far
    }
)

@Composable
private fun effortText(status: EffortStatus) = stringResource(
    when (status) {
        EffortStatus.BELOW -> R.string.progress_effort_text_below
        EffortStatus.IN -> R.string.progress_effort_text_in
        EffortStatus.ABOVE -> R.string.progress_effort_text_above
        EffortStatus.FAR_ABOVE -> R.string.progress_effort_text_far
    }
)

/** The week's days as bars of their effort, a dash on a day without any. */
@Composable
private fun DayBars(model: EffortModel, monday: LocalDate, color: Color) {
    val locale = LocalFormats.current.locale
    val calendar by remember(monday) { model.queries.calendar(DayRange(monday, monday.plusDays(7))) }
        .collectAsStateWithLifecycle()
    val byDay = calendar.data.orEmpty().associate { d -> d.day.take(10) to d.activities.sumOf { it.load ?: 0.0 } }
    val days = weekDays(monday)
    val values = days.map { byDay[it.toString()] ?: 0.0 }
    val most = max(1.0, values.maxOrNull() ?: 0.0)
    Row(
        Modifier.height(150.dp).padding(top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEachIndexed { i, d ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val h = if (values[i] > 0) max(8.0, values[i] / most * 110).dp else 2.dp
                androidx.compose.foundation.layout.Box(Modifier.width(14.dp).height(h).background(color))
                Text(weekdayLetter(d, locale), color = color, fontSize = 13.sp)
            }
        }
    }
}

/** The week's activities: when, what, how long, and their effort in its band's colour. */
@Composable
private fun WeekActivities(model: EffortModel, monday: LocalDate, onOpen: (Long) -> Unit) {
    val colors = ZankupTheme.colors
    val locale = LocalFormats.current.locale
    val calendar by remember(monday) { model.queries.calendar(DayRange(monday, monday.plusDays(7))) }
        .collectAsStateWithLifecycle()
    val activities = calendar.data.orEmpty().flatMap { it.activities }
    if (activities.isEmpty()) return
    val dayFormat = DateTimeFormatter.ofPattern("d MMMM", locale)
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    Column(
        Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(R.string.progress_week_activities),
            Modifier.padding(bottom = 8.dp),
            color = colors.fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        activities.forEach { a ->
            val sport = sportOf(a.sport)
            val start = runCatching {
                OffsetDateTime.parse(a.start).atZoneSameInstant(ZoneId.systemDefault())
            }.getOrNull()
            val whenText = start?.let {
                stringResource(R.string.progress_activity_when, dayFormat.format(it), timeFormat.format(it))
            } ?: day(a.start)?.let { longDate(it) }.orEmpty()
            val minutes = (a.movingS / 60).roundToInt()
            val effort = (a.load ?: 0.0).roundToInt()
            val bandColor = when (effortBand(effort.toDouble())) {
                EffortBand.LIGHT -> colors.fitness
                EffortBand.MODERATE -> colors.accent
                EffortBand.HARD -> colors.zones[4]
            }
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(a.id.toLong()) }.padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ZankupIcon(sportIcon(sport), contentDescription = null, tint = colors.fg, size = 26.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(whenText, color = colors.fg, fontSize = 13.sp)
                    Text(a.name ?: stringResource(sportName(sport)), color = colors.fg, fontSize = 16.sp)
                    Text("${minutes / 60} h ${minutes % 60} min", color = colors.fg, fontSize = 14.sp)
                }
                Text(
                    effort.toString(),
                    Modifier.align(Alignment.CenterVertically),
                    color = bandColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
