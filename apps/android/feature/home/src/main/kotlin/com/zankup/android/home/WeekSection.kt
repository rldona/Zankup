package com.zankup.android.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.color
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.family
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateStyle
import java.util.Locale

/**
 * This week: its title and streak, the seven days as circles (the first activity's sport, a dot for a
 * second, today ringed, the days to come dimmed), the week's figures and how it compares with a usual one.
 */
@Composable
internal fun WeekSection(
    week: WeekCard,
    onOpen: (Long) -> Unit,
    onPress: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZankupTheme.colors
    val formats = LocalFormats.current
    ZankupCard(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.home_week), style = MaterialTheme.typography.titleLarge)
            if (week.streak > 0) {
                Pill(pluralStringResource(R.plurals.home_streak, week.streak, week.streak), colors.formPositive)
            }
        }
        Spacer(Modifier.height(ZankupTheme.spacing.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            week.days.forEach { DayCircle(it, formats.locale, onOpen, onPress) }
        }
        CardDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.s)) {
            val figure = Modifier.weight(1f)
            WeekFigure(stringResource(R.string.home_stat_activities), week.count.toString(), figure)
            WeekFigure(stringResource(R.string.home_stat_distance), formats.unit.distance(week.distanceM), figure)
            WeekFigure(stringResource(R.string.home_stat_time), formats.hoursMinutes(week.movingS), figure)
            WeekFigure(stringResource(R.string.home_stat_ascent), formats.elevation(week.ascentM), figure)
        }
        week.usual?.let {
            CardDivider()
            UsualWeekBar(it)
        }
    }
}

@Composable
private fun DayCircle(day: WeekDay, locale: Locale, onOpen: (Long) -> Unit, onPress: (Long) -> Unit) {
    val colors = ZankupTheme.colors
    val fill = day.first?.family?.color
    val ring =
        when {
            day.isToday -> colors.accent
            fill != null -> fill
            else -> colors.line
        }
    val text = if (day.isToday) colors.accent else colors.muted
    val spoken = remember(day.date, locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(day.date) }
    val narrow = remember(day.date, locale) { day.date.dayOfWeek.getDisplayName(DateStyle.NARROW, locale) }
    val id = day.firstId
    val open =
        if (id != null) {
            Modifier.onPressDown(id) { onPress(id) }.clickable(role = Role.Button) { onOpen(id) }
        } else {
            Modifier
        }
    Column(
        Modifier
            .alpha(if (day.isFuture) 0.4f else 1f)
            .clip(MaterialTheme.shapes.small)
            .then(open)
            .widthIn(min = 40.dp)
            .padding(vertical = ZankupTheme.spacing.xs)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.xs)
    ) {
        Box(Modifier.size(36.dp).clearAndSetSemantics {}) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .then(if (fill != null) Modifier.background(fill) else Modifier)
                    .border(2.dp, ring, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val sport = day.first
                if (sport != null) {
                    ZankupIcon(sport.family.icon, contentDescription = null, tint = colors.page, size = 18.dp)
                } else {
                    Text(
                        day.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = text
                    )
                }
            }
            day.second?.let { second ->
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(second.family.color)
                )
            }
        }
        Text(
            if (day.isToday) stringResource(R.string.home_today) else narrow,
            style = MaterialTheme.typography.labelSmall,
            color = text,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

@Composable
private fun WeekFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = ZankupTheme.colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.merge(Numbers.tabular),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The time trained so far against a usual week: ahead or behind as a pill, and a bar with the usual week marked. */
@Composable
private fun UsualWeekBar(usual: UsualWeek) {
    val colors = ZankupTheme.colors
    val formats = LocalFormats.current
    val title = stringResource(R.string.home_vs_usual)
    Column(verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (usual.share != 100) {
                val ahead = usual.share > 100
                Pill(
                    stringResource(R.string.home_change, if (ahead) "+" else "", usual.share - 100),
                    if (ahead) colors.formPositive else colors.formNegative
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .semantics {
                    contentDescription = title
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(
                            usual.share.toFloat().coerceAtMost(UsualWeek.BAR_MAX),
                            0f..UsualWeek.BAR_MAX
                        )
                }
        ) {
            val bar = Modifier.align(Alignment.CenterStart).height(8.dp).clip(CircleShape)
            Box(bar.fillMaxWidth().background(colors.line))
            if (usual.fill > 0f) Box(bar.fillMaxWidth(usual.fill).background(colors.accent))
            Box(
                Modifier
                    .fillMaxWidth(usual.mark)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(width = 2.dp, height = 14.dp)
                        .background(colors.fg)
                )
            }
        }
        UnderMark(usual.mark) {
            Text(
                stringResource(R.string.home_usual_week, formats.hoursMinutes(usual.averageS)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                maxLines = 1
            )
        }
    }
}

/** Places its one child centred under [at] (a share of the width), kept inside the width. */
@Composable
private fun UnderMark(at: Float, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0))
        val width = constraints.maxWidth
        val x = (width * at - placeable.width / 2f).toInt().coerceIn(0, (width - placeable.width).coerceAtLeast(0))
        layout(width, placeable.height) { placeable.place(x, 0) }
    }
}

/** This week's shape while it loads. */
@Composable
internal fun WeekSkeleton(modifier: Modifier = Modifier) {
    ZankupCard(modifier) {
        Skeleton(Modifier.fillMaxWidth(0.4f), height = 22.dp)
        Spacer(Modifier.height(ZankupTheme.spacing.m))
        Skeleton(Modifier.fillMaxWidth(), height = 52.dp)
        Spacer(Modifier.height(ZankupTheme.spacing.m))
        Skeleton(Modifier.fillMaxWidth(), height = 36.dp)
        Spacer(Modifier.height(ZankupTheme.spacing.m))
        Skeleton(Modifier.fillMaxWidth(), height = 28.dp)
    }
}
