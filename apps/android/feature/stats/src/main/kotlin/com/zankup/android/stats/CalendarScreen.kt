package com.zankup.android.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.CalendarActivity
import com.zankup.android.api.model.CalendarDay
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.formatDuration
import com.zankup.android.ui.Formats
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.family
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The training calendar (OS-1141), from Activities: a month of weeks, one round sport icon per
 * activity sized by its effort, each day's form and each week's totals. A bubble opens the activity;
 * tapping two days sums the days between them.
 */
@Composable
fun CalendarScreen(onBack: () -> Unit, onOpenActivity: (Long) -> Unit, model: CalendarModel = hiltViewModel()) {
    val month by model.month.collectAsStateWithLifecycle()
    val state by model.days.collectAsStateWithLifecycle()
    val f = LocalFormats.current
    var selection by rememberSaveable(month) { mutableStateOf<Pair<Int, Int>?>(null) }
    val range = selection?.let { it.first..it.second }
    Scaffold(containerColor = ZankupTheme.colors.page, topBar = {
        ZankupTopBar(stringResource(R.string.stats_calendar_title), onBack = onBack)
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZankupTheme.spacing.gutter, vertical = ZankupTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.s)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Arrow(false, true, R.string.stats_calendar_prev) { model.shift(-1) }
                Text(
                    DateTimeFormatter.ofPattern("LLLL yyyy", f.locale).format(month)
                        .replaceFirstChar { it.titlecase(f.locale) },
                    style = HeadingStyle,
                    color = ZankupTheme.colors.fg,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Arrow(true, true, R.string.stats_calendar_next) { model.shift(1) }
            }
            val days = state.data
            if (days == null) {
                if (state.loading) Skeleton(Modifier.fillMaxWidth(), height = 360.dp)
                return@Column
            }
            if (range != null && range.last > range.first) {
                val span = days.subList(range.first, (range.last + 1).coerceAtMost(days.size))
                Muted(
                    stringResource(R.string.stats_calendar_selection, span.size, totalText(span, f)),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            Row {
                weekdayLetters(f.locale).forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = ZankupTheme.colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            val heaviest = days.flatMap { d -> d.activities.map { it.load ?: 0.0 } }.maxOrNull()?.coerceAtLeast(1.0)
                ?: 1.0
            weeksOf(days).forEachIndexed { w, week ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        week.forEachIndexed { i, day ->
                            val index = w * 7 + i
                            DayCell(
                                day = day,
                                month = month,
                                selected = range?.contains(index) == true,
                                heaviest = heaviest,
                                onPick = {
                                    val next = pick(selection?.let { it.first..it.second }, index)
                                    selection = next.first to next.last
                                },
                                onOpenActivity = onOpenActivity,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        "${stringResource(R.string.stats_calendar_week)}: ${totalText(week, f)}",
                        style = MaterialTheme.typography.labelSmall.merge(Numbers.tabular),
                        color = ZankupTheme.colors.muted,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** "3 · 32.10 km · 2:45": activities, distance and moving time of some days. */
private fun totalText(days: List<CalendarDay>, f: Formats): String {
    val t = spanTotal(days.flatMap { it.activities }, { it.distanceM }, { it.movingS })
    return "${t.count} · ${f.distance(t.distanceM)} · ${f.hoursMinutes(t.movingS)}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayCell(
    day: CalendarDay,
    month: YearMonth,
    selected: Boolean,
    heaviest: Double,
    onPick: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZankupTheme.colors
    val f = LocalFormats.current
    val date = runCatching { LocalDate.parse(day.day) }.getOrNull()
    val inMonth = date != null && YearMonth.from(date) == month
    val label = date?.let { DateTimeFormatter.ofPattern("d MMMM", f.locale).format(it) } ?: day.day
    val count = pluralStringResource(R.plurals.stats_calendar_activities, day.activities.size, day.activities.size)
    Column(
        modifier
            .heightIn(min = 64.dp)
            .alpha(if (inMonth) 1f else 0.45f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.surface2 else colors.surface)
            .clickable(role = Role.Button, onClick = onPick)
            .semantics { contentDescription = "$label, $count" }
            .padding(3.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                date?.dayOfMonth?.toString().orEmpty(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            day.tsb?.let { tsb ->
                val rounded = tsb.roundToInt()
                Text(
                    (if (rounded > 0) "+" else "") + rounded,
                    fontSize = 9.sp,
                    color = if (tsb >= 0) colors.formPositive else colors.formNegative
                )
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            day.activities.forEach { a -> Bubble(a, heaviest) { onOpenActivity(a.id.toLong()) } }
        }
    }
}

@Composable
private fun Bubble(a: CalendarActivity, heaviest: Double, onOpen: () -> Unit) {
    val sport = sportOf(a.sport)
    val size = bubbleSize(a.load, heaviest).dp
    val name = a.name ?: stringResource(sportName(sport))
    val description = "$name · ${formatDuration(a.movingS)}"
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(sportColour(a.sport))
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        ZankupIcon(sport.family.icon, contentDescription = null, tint = Color.White, size = size * 0.62f)
    }
}
