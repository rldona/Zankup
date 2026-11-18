package com.zankup.android.progress.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.api.model.CalendarActivity
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.progress.R
import com.zankup.android.progress.logic.VolumeMetric
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf
import java.time.LocalDate

/** "Filter by sport": every sport, or one, with a search over their names. */
@Composable
internal fun SportSheet(sport: Sport?, found: List<Sport>, onPick: (Sport?) -> Unit) {
    val colors = ZankupTheme.colors
    val locale = LocalFormats.current.locale
    var query by remember { mutableStateOf("") }
    val names = (found.ifEmpty { Sport.entries }).map { it to stringResource(sportName(it)) }
    val list = names.filter { (_, name) -> name.lowercase(locale).contains(query.trim().lowercase(locale)) }
    LazyColumn(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
        item {
            SheetTitle(stringResource(R.string.progress_filter_sport), Modifier.padding(bottom = 12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                placeholder = { Text(stringResource(R.string.progress_nav_search)) },
                leadingIcon = { ZankupIcon(ZIcon.Search, contentDescription = null, tint = colors.muted) },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface2,
                    unfocusedContainerColor = colors.surface2,
                    unfocusedBorderColor = colors.surface2
                )
            )
        }
        if (query.isBlank()) {
            item {
                SportRow(
                    null,
                    sport == null,
                    stringResource(R.string.progress_all_sports),
                    stringResource(R.string.progress_all_sports_note),
                    onPick
                )
            }
        }
        items(list, key = { it.first.key }) { (s, name) -> SportRow(s, s == sport, name, null, onPick) }
    }
}

@Composable
private fun SportRow(sport: Sport?, on: Boolean, title: String, note: String?, onPick: (Sport?) -> Unit) {
    val colors = ZankupTheme.colors
    val tint = if (on) colors.accent else colors.fg
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton) { onPick(sport) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ZankupIcon(sportIcon(sport), contentDescription = null, tint = tint, size = 26.dp)
        Column(Modifier.weight(1f)) {
            Text(title, color = tint, fontSize = 15.sp)
            if (note != null) Text(note, color = tint, fontSize = 13.sp)
        }
        if (on) ZankupIcon(ZIcon.Check, contentDescription = null, tint = colors.accent, size = 26.dp)
    }
}

/** A radio row of a sheet: a title, an optional note and the radio on the right. */
@Composable
internal fun RadioRow(title: String, on: Boolean, onClick: () -> Unit, note: String? = null) {
    val colors = ZankupTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.fg, fontSize = 15.sp)
            if (note != null) Muted(note, style = MaterialTheme.typography.bodySmall)
        }
        Box(
            Modifier.size(24.dp).border(2.dp, if (on) colors.accent else colors.fg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (on) Box(Modifier.size(12.dp).clip(CircleShape).background(colors.accent))
        }
    }
}

/** An activity of a day, with its date: a dated row of a sheet that opens it. */
internal data class DatedActivity(val day: LocalDate, val activity: CalendarActivity)

/** A day's or week's totals and its activities, each opening its page. */
@Composable
internal fun BucketSheet(title: String, activities: List<DatedActivity>, onOpen: (Long) -> Unit) {
    val colors = ZankupTheme.colors
    val format = volumeFormat()
    LazyColumn(Modifier.padding(bottom = 8.dp)) {
        item {
            Box(Modifier.padding(bottom = 12.dp)) { CentredTitle(title) }
            Box(Modifier.fillMaxWidth().background(colors.bg).padding(horizontal = 20.dp, vertical = 18.dp)) {
                BucketStats(
                    activities.sumOf { it.activity.distanceM ?: 0.0 },
                    activities.sumOf { it.activity.ascentM ?: 0.0 },
                    activities.sumOf { it.activity.movingS },
                    format
                )
            }
        }
        items(activities, key = { it.activity.id }) { (day, a) ->
            val sport = sportOf(a.sport)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(a.id.toLong()) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SportTile(sport, 48)
                    Column(Modifier.weight(1f)) {
                        Text(
                            a.name ?: stringResource(sportName(sport)),
                            color = colors.fg,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Muted(longDate(day), style = MaterialTheme.typography.bodySmall)
                    }
                }
                BucketStats(a.distanceM ?: 0.0, a.ascentM ?: 0.0, a.movingS, format)
            }
        }
    }
}

@Composable
private fun BucketStats(
    distance: Double,
    ascent: Double,
    time: Double,
    format: com.zankup.android.progress.logic.VolumeFormat
) {
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        listOf(VolumeMetric.DISTANCE to distance, VolumeMetric.ASCENT to ascent, VolumeMetric.TIME to time)
            .filter { (m, v) -> m == VolumeMetric.TIME || v > 0 }
            .forEach { (m, v) ->
                Column {
                    Text(metricShort(m), fontSize = 13.sp, color = ZankupTheme.colors.fg)
                    Figure(format.value(m, v))
                }
            }
    }
}

/** A day's activities, to choose one, when a bubble holds several. */
@Composable
internal fun DayList(activities: List<CalendarActivity>, onOpen: (Long) -> Unit) {
    val colors = ZankupTheme.colors
    val formats = LocalFormats.current
    Column(
        Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Muted(
            pluralStringResource(R.plurals.progress_day_activities, activities.size, activities.size),
            Modifier.padding(bottom = 4.dp)
        )
        activities.forEach { a ->
            val sport = sportOf(a.sport)
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(a.id.toLong()) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Dot(sportColor(sport), 14)
                Text(
                    a.name ?: stringResource(sportName(sport)),
                    Modifier.weight(1f),
                    color = colors.fg,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Muted(
                    listOfNotNull(
                        a.distanceM?.takeIf { it > 0 }?.let(formats::distance),
                        formats.hoursMinutes(a.movingS)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** A sheet's centred title. */
@Composable
internal fun CentredTitle(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = ZankupTheme.colors.fg
    )
}
