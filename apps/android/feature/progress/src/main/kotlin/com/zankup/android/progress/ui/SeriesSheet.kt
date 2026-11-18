package com.zankup.android.progress.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.CalendarActivity
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.progress.R
import com.zankup.android.progress.data.ProgressQueries
import com.zankup.android.progress.logic.DayRange
import com.zankup.android.progress.logic.WeekMark
import com.zankup.android.progress.logic.day
import com.zankup.android.progress.logic.monthGrid
import com.zankup.android.progress.logic.weekDays
import com.zankup.android.progress.logic.weekMark
import com.zankup.android.progress.logic.weekdayLetter
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.sportOf
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private const val ROW = 50

/**
 * Series' month, in its sheet: each day with its sport (a tap opens the activity) and, beside each
 * week, whether it kept the streak, on a band that runs through the streak to this week's flame.
 */
@Composable
internal fun SeriesMonth(queries: ProgressQueries, today: LocalDate, onOpenActivity: (Long) -> Unit) {
    val colors = ZankupTheme.colors
    val locale = LocalFormats.current.locale
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val (from, to) = monthGrid(month)
    val calendar by remember(month) { queries.calendar(DayRange(from, to)) }.collectAsStateWithLifecycle()
    val streak by remember { queries.streak() }.collectAsStateWithLifecycle()
    val days = calendar.data.orEmpty().associate { it.day.take(10) to it.activities }
    val weeks = streak.data?.weeks ?: 0
    val since = day(streak.data?.since)
    val mondays = generateSequence(from) { it.plusWeeks(1) }.takeWhile { it < to }.toList()
    val marks = mondays.map { m ->
        weekMark(m, today, weekDays(m).any { days[it.toString()].orEmpty().isNotEmpty() }, since, weeks)
    }
    val thisMonth = month == YearMonth.from(today)
    val title = DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month).replaceFirstChar { it.titlecase(locale) }
    fun band(i: Int) = marks.getOrNull(i).let { it == WeekMark.STREAK || it == WeekMark.CURRENT }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetTitle(title, Modifier.weight(1f))
            IconButton(onClick = { month = month.minusMonths(1) }) {
                ChevronLeft(colors.fg, stringResource(R.string.progress_prev_month))
            }
            IconButton(onClick = { month = month.plusMonths(1) }, enabled = !thisMonth) {
                ZankupIcon(
                    ZIcon.Chevron,
                    contentDescription = stringResource(R.string.progress_next_month),
                    tint = if (thisMonth) colors.line else colors.fg,
                    size = 30.dp
                )
            }
            InfoButton(stringResource(R.string.progress_streak_info))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column {
                Text(stringResource(R.string.progress_your_streak), fontSize = 13.sp, color = colors.fg)
                Figure(pluralStringResource(R.plurals.progress_streak_weeks, weeks, weeks))
            }
            Column {
                Text(stringResource(R.string.progress_streak_activities), fontSize = 13.sp, color = colors.fg)
                Figure((streak.data?.activities ?: 0).toString())
            }
        }
        if (weeks == 0) Muted(stringResource(R.string.progress_no_streak))
        Column {
            Row(Modifier.padding(bottom = 6.dp)) {
                weekDays(mondays.firstOrNull() ?: today).forEach {
                    Text(
                        weekdayLetter(it, locale),
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = colors.fg
                    )
                }
                Box(Modifier.width(52.dp))
            }
            mondays.forEachIndexed { w, monday ->
                Row(Modifier.height(ROW.dp), verticalAlignment = Alignment.CenterVertically) {
                    weekDays(monday).forEach { d ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            DayCell(d, today, d.month == month.month, days[d.toString()].orEmpty(), onOpenActivity)
                        }
                    }
                    Box(Modifier.width(52.dp).height(ROW.dp), contentAlignment = Alignment.Center) {
                        if (band(w)) {
                            val up = band(w - 1)
                            val down = band(w + 1)
                            Box(
                                Modifier
                                    .padding(horizontal = 8.dp)
                                    .padding(top = if (up) 0.dp else 4.dp, bottom = if (down) 0.dp else 4.dp)
                                    .fillMaxWidth()
                                    .height(ROW.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = if (up) 0.dp else 18.dp,
                                            topEnd = if (up) 0.dp else 18.dp,
                                            bottomStart = if (down) 0.dp else 18.dp,
                                            bottomEnd = if (down) 0.dp else 18.dp
                                        )
                                    ).background(colors.accent.copy(alpha = 0.25f))
                            )
                        }
                        WeekBadge(marks[w], weeks)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekBadge(mark: WeekMark, weeks: Int) {
    val colors = ZankupTheme.colors
    when (mark) {
        WeekMark.CURRENT -> Flame(weeks, 44)
        WeekMark.STREAK, WeekMark.DONE -> Box(
            Modifier.size(26.dp).clip(CircleShape).background(colors.accent),
            contentAlignment = Alignment.Center
        ) {
            ZankupIcon(ZIcon.Check, contentDescription = null, tint = colors.accentFg, size = 18.dp)
        }
        else -> Box(Modifier.size(26.dp).border(2.dp, colors.line, CircleShape))
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    today: LocalDate,
    inMonth: Boolean,
    activities: List<CalendarActivity>,
    onOpen: (Long) -> Unit
) {
    val colors = ZankupTheme.colors
    val number = day.dayOfMonth.toString()
    val first = activities.firstOrNull()
    val style = MaterialTheme.typography.bodyMedium.merge(Numbers.tabular)
    when {
        !inMonth -> Text(number, style = style, color = colors.muted)
        first != null -> {
            val name = first.name ?: first.sport
            Box {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.fg)
                        .then(if (day == today) Modifier.border(2.dp, colors.accent, CircleShape) else Modifier)
                        .clickable { onOpen(first.id.toLong()) }
                        .semantics { contentDescription = name },
                    contentAlignment = Alignment.Center
                ) {
                    ZankupIcon(
                        sportIcon(sportOf(first.sport)),
                        contentDescription = null,
                        tint = colors.bg,
                        size = 22.dp
                    )
                }
                if (activities.size > 1) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(2.dp, (-2).dp)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(colors.surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(colors.fg)
                    )
                }
            }
        }
        else -> {
            val past = day < today
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (past) colors.surface2 else colors.surface)
                    .then(
                        if (!past) {
                            Modifier.border(
                                if (day == today) 2.dp else 1.5.dp,
                                if (day == today) colors.fg else colors.line,
                                CircleShape
                            )
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(number, style = style, color = colors.fg, fontWeight = FontWeight.Normal)
            }
        }
    }
}
