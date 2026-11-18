package com.zankup.android.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.CalendarActivity
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupSheet
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.progress.logic.DayRange
import com.zankup.android.progress.logic.LogMetric
import com.zankup.android.progress.logic.bubbleLabel
import com.zankup.android.progress.logic.bubbleSize
import com.zankup.android.progress.logic.day
import com.zankup.android.progress.logic.indexOfWeek
import com.zankup.android.progress.logic.monthsBack
import com.zankup.android.progress.logic.total
import com.zankup.android.progress.logic.weekAt
import com.zankup.android.progress.logic.weekCount
import com.zankup.android.progress.logic.weekDays
import com.zankup.android.progress.logic.weekRange
import com.zankup.android.progress.logic.weekdayLetter
import com.zankup.android.progress.ui.CentredTitle
import com.zankup.android.progress.ui.DayList
import com.zankup.android.progress.ui.dayColor
import com.zankup.android.progress.ui.imperial
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Weeks fetched together, so scrolling asks the server once every eight weeks. */
private const val CHUNK = 8
private const val ROW_HEIGHT = 124

private enum class LogPanel {
    FILTERS,
    MONTHS
}

/**
 * The training log, from Progress (as Strava's): every week from this one back to the first activity,
 * a bubble per day sized and labelled by time, distance or elevation, coloured by sport. The header has
 * the filters, the months to jump to and the search ([onSearch], the Activities tab). A bubble opens its
 * activity, or lists the day's.
 */
@Composable
fun TrainingLogScreen(
    onBack: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    model: TrainingLogModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { model.refresh() }
    val colors = ZankupTheme.colors
    val locale = LocalFormats.current.locale
    val today = model.today
    val streak by model.streak.collectAsStateWithLifecycle()
    val first = day(streak.data?.firstActivity)
    val count = weekCount(first, today)
    var metric by rememberSaveable { mutableStateOf(LogMetric.TIME) }
    var sport by rememberSaveable { mutableStateOf<String?>(null) }
    var panel by remember { mutableStateOf<LogPanel?>(null) }
    var dayList by remember { mutableStateOf<List<CalendarActivity>?>(null) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val open = { activities: List<CalendarActivity> ->
        if (activities.size == 1) onOpenActivity(activities.first().id.toLong()) else dayList = activities
    }

    Scaffold(
        modifier = modifier,
        containerColor = colors.page,
        topBar = {
            ZankupTopBar(stringResource(R.string.progress_log), onBack = onBack) {
                IconButton(onClick = { panel = LogPanel.FILTERS }) {
                    ZankupIcon(ZIcon.Options, stringResource(R.string.progress_filters))
                }
                IconButton(onClick = { panel = LogPanel.MONTHS }) {
                    ZankupIcon(ZIcon.Calendar, stringResource(R.string.progress_go_to_month))
                }
                IconButton(onClick = onSearch) {
                    ZankupIcon(ZIcon.Search, stringResource(R.string.progress_nav_search))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                weekDays(weekAt(0, today)).forEach {
                    Text(
                        weekdayLetter(it, locale),
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = colors.fg
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                items(count, key = { it }) { index ->
                    val chunk = index / CHUNK
                    val range =
                        DayRange(weekAt(chunk * CHUNK + CHUNK - 1, today), weekAt(chunk * CHUNK, today).plusDays(7))
                    val calendar by remember(chunk) { model.queries.calendar(range) }.collectAsStateWithLifecycle()
                    val days = calendar.data?.associate { it.day.take(10) to it.activities }
                    WeekRow(weekAt(index, today), days, metric, sport?.let(::sportOf), today, open)
                }
            }
        }
    }

    when (panel) {
        LogPanel.FILTERS -> ZankupSheet(onDismiss = { panel = null }) {
            Column(
                Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CentredTitle(stringResource(R.string.progress_filters))
                Text(
                    stringResource(R.string.progress_activity_type),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.fg
                )
                val all = stringResource(R.string.progress_all)
                ChipPicker(
                    options = listOf<Sport?>(null) + Sport.entries,
                    selected = sport?.let(::sportOf),
                    label = { s -> if (s == null) all else stringResource(sportName(s)) },
                    onSelect = { sport = it?.key }
                )
                Text(stringResource(R.string.progress_data), fontWeight = FontWeight.SemiBold, color = colors.fg)
                ChipPicker(
                    options = LogMetric.entries,
                    selected = metric,
                    label = { m ->
                        stringResource(
                            when (m) {
                                LogMetric.TIME -> R.string.progress_metric_time
                                LogMetric.DISTANCE -> R.string.progress_metric_distance
                                LogMetric.ASCENT -> R.string.progress_metric_ascent
                            }
                        )
                    },
                    onSelect = { metric = it }
                )
            }
        }
        LogPanel.MONTHS -> SidePanel(onDismiss = { panel = null }) {
            Months(first ?: today, today) { start ->
                panel = null
                scope.launch { list.animateScrollToItem(indexOfWeek(start, today).coerceIn(0, count - 1)) }
            }
        }
        null -> Unit
    }
    dayList?.let { activities ->
        ZankupSheet(onDismiss = { dayList = null }) {
            DayList(activities) { id ->
                dayList = null
                onOpenActivity(id)
            }
        }
    }
}

/** A panel from the right, two thirds wide, over the dimmed screen; a tap outside or Back closes it. */
@Composable
private fun SidePanel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.32f).fillMaxHeight().clickable(onClick = onDismiss))
            Box(Modifier.weight(0.68f).fillMaxHeight().background(ZankupTheme.colors.surface).systemBarsPadding()) {
                content()
            }
        }
    }
}

/** Every month from this one back to the first activity's, under its year; a tap goes to its first week. */
@Composable
private fun Months(first: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val colors = ZankupTheme.colors
    val locale = LocalFormats.current.locale
    val months = monthsBack(first, today)
    val name = DateTimeFormatter.ofPattern("LLLL", locale)
    LazyColumn {
        months.forEachIndexed { i, month ->
            if (i == 0 || months[i - 1].year != month.year) {
                item("y${month.year}") {
                    Text(
                        month.year.toString(),
                        Modifier.padding(16.dp).padding(bottom = 0.dp),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.fg
                    )
                }
            }
            item(month.toString()) {
                HorizontalDivider(color = colors.line)
                val start = month.atDay(1)
                Text(
                    name.format(month).replaceFirstChar { it.titlecase(locale) },
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(if (start < first) first else start) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    fontSize = 15.sp,
                    color = colors.fg
                )
            }
        }
    }
}

/** One week: its days and total, a bubble per day with activity, ▲ under today. */
@Composable
private fun WeekRow(
    monday: LocalDate,
    days: Map<String, List<CalendarActivity>>?,
    metric: LogMetric,
    sport: Sport?,
    today: LocalDate,
    onDay: (List<CalendarActivity>) -> Unit
) {
    val colors = ZankupTheme.colors
    val formats = LocalFormats.current
    val locale = formats.locale
    val imperial = imperial()
    val week = weekDays(monday).map { d ->
        d to days?.get(d.toString()).orEmpty().filter { sport == null || sportOf(it.sport) == sport }
    }
    val sum = week.sumOf { (_, a) -> total(a, metric) }
    val label = when (metric) {
        LogMetric.TIME -> formats.hoursMinutes(sum)
        LogMetric.DISTANCE -> formats.distance(sum)
        LogMetric.ASCENT -> formats.elevation(sum)
    }
    Column(Modifier.height(ROW_HEIGHT.dp).padding(horizontal = 16.dp).padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(weekRange(monday, locale), fontSize = 14.sp, color = colors.fg)
            Text(
                if (days !=
                    null
                ) {
                    label
                } else {
                    ""
                },
                style = MaterialTheme.typography.bodyMedium.merge(Numbers.tabular),
                color = colors.fg
            )
        }
        Row(Modifier.weight(1f)) {
            week.forEach { (d, activities) ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    val v = total(activities, metric)
                    val size = bubbleSize(v, metric)
                    if (activities.isNotEmpty() && size > 0) {
                        val text = bubbleLabel(v, metric, locale, imperial)
                        val several = activities.size > 1
                        Box(
                            Modifier
                                .size(size.dp)
                                .clip(CircleShape)
                                .background(dayColor(activities))
                                .clickable { onDay(activities) }
                                .semantics { contentDescription = "$d: $text" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text, color = if (several) colors.bg else Color.White, fontSize = 11.sp, maxLines = 1)
                        }
                        if (several) {
                            Box(
                                Modifier
                                    .offset((size / 2).dp, (-size / 2).dp)
                                    .widthIn(min = 20.dp)
                                    .height(20.dp)
                                    .clip(CircleShape)
                                    .background(colors.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    activities.size.toString(),
                                    color = colors.fg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(colors.muted))
                    }
                    if (d == today) {
                        Text(
                            "▲",
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                            color = colors.fg,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = colors.line)
    }
}
