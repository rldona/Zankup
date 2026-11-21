package com.zankup.android.progress.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupInfoButton
import com.zankup.android.designsystem.components.ZankupSheet
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.progress.R
import com.zankup.android.ui.family
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf

/** A sport's chip or row icon, and the all-sports one. */
internal fun sportIcon(sport: Sport?): ZIcon = sport?.family?.icon ?: ZIcon.Progress

internal fun sportIconOf(key: String?): ZIcon = if (key == null) ZIcon.Progress else sportOf(key).family.icon

/** "All sports" or the sport's name. */
@Composable
internal fun sportLabel(sport: Sport?): String =
    if (sport == null) stringResource(R.string.more_all_sports) else stringResource(sportName(sport))

/** The header's (i): the shared explanation dialog. */
@Composable
internal fun InfoAction(text: String) {
    ZankupInfoButton(text)
}

/** Progress's range picker: the chosen one a filled accent pill, the rest accent words. */
@Composable
internal fun RangePills(range: ProgressRange, onPick: (ProgressRange) -> Unit, modifier: Modifier = Modifier) {
    val colors = ZankupTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ProgressRange.entries.forEach { r ->
            val on = r == range
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (on) colors.accent else Color.Transparent)
                    .selectable(on, role = Role.RadioButton) { onPick(r) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(rangeLabel(r)),
                    color = if (on) colors.accentFg else colors.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

internal fun rangeLabel(r: ProgressRange) = when (r) {
    ProgressRange.D7 -> R.string.more_range_7d
    ProgressRange.M1 -> R.string.more_range_1m
    ProgressRange.M3 -> R.string.more_range_3m
    ProgressRange.M6 -> R.string.more_range_6m
    ProgressRange.YTD -> R.string.more_range_ytd
    ProgressRange.Y1 -> R.string.more_range_1y
}

/** Two options side by side in a rounded outline, the chosen one shaded with a tick (Skills' span). */
@Composable
internal fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    offColor: Color = Color.Transparent
) {
    val colors = ZankupTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).border(1.dp, colors.line, CircleShape)
    ) {
        options.forEach { o ->
            val on = o == selected
            Row(
                Modifier
                    .weight(1f)
                    .background(if (on) colors.surface2 else offColor)
                    .selectable(on, role = Role.RadioButton) { onPick(o) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (on) ZankupIcon(ZIcon.Check, null, tint = colors.fg, size = 18.dp)
                Text(
                    label(o),
                    color = colors.fg,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

/** A row of a picking sheet: icon, title (and note), a tick on the chosen one, all in the accent. */
@Composable
internal fun PickRow(
    icon: ZIcon?,
    title: String,
    on: Boolean,
    note: String? = null,
    noteColor: Color = ZankupTheme.colors.fg,
    onClick: () -> Unit
) {
    val colors = ZankupTheme.colors
    val tint = if (on) colors.accent else colors.fg
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(on, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (icon != null) ZankupIcon(icon, null, tint = tint, size = 26.dp)
        Column(Modifier.weight(1f)) {
            Text(title, color = tint, fontSize = 15.sp)
            if (note != null) Text(note, color = if (on) colors.accent else noteColor, fontSize = 13.sp)
        }
        if (on) ZankupIcon(ZIcon.Check, null, tint = colors.accent, size = 26.dp)
    }
}

@Composable
internal fun SheetTitle(text: String, bottom: Int = 8) {
    Text(
        text,
        color = ZankupTheme.colors.fg,
        fontSize = 17.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(bottom = bottom.dp)
    )
}

/**
 * "Filter by sport" (src/progress/SportSheet.tsx): every sport, or one, with a search over their names.
 * [allNote] is the note under "All sports". A goal's sheet (objective-new.tsx) has no search and a
 * muted note.
 */
@Composable
internal fun SportSheet(
    sport: Sport?,
    allNote: String,
    onPick: (Sport?) -> Unit,
    onDismiss: () -> Unit,
    search: Boolean = true
) {
    var query by remember { mutableStateOf("") }
    val names = Sport.entries.associateWith { stringResource(sportName(it)) }
    val colors = ZankupTheme.colors
    ZankupSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            SheetTitle(stringResource(R.string.more_filter_sport), bottom = if (search) 12 else 8)
            if (search) {
                SearchField(query) { query = it }
            }
            val q = query.trim().lowercase()
            if (q.isEmpty()) {
                val note = if (search) colors.fg else colors.muted
                PickRow(ZIcon.Progress, stringResource(R.string.more_all_sports), sport == null, allNote, note) {
                    onPick(null)
                }
            }
            Sport.entries.filter { names.getValue(it).lowercase().contains(q) }.forEach { s ->
                PickRow(sportIcon(s), names.getValue(s), s == sport) { onPick(s) }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val colors = ZankupTheme.colors
    Row(
        Modifier
            .padding(bottom = 6.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.surface2)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ZankupIcon(ZIcon.Search, null, tint = colors.muted, size = 22.dp)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.more_search),
                    color = colors.muted,
                    fontSize = 15.sp
                )
            }
            BasicTextField(
                query,
                onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.fg, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Nine segments, narrower towards the top, filled up to the level. */
@Composable
internal fun LevelBar(level: Double, color: Color, height: Dp) {
    val track = ZankupTheme.colors.surface2
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        segments(level).forEachIndexed { i, fill ->
            Box(
                Modifier
                    .weight(SEGMENT_WIDTHS[i])
                    .height(height)
                    .clip(RoundedCornerShape(height / 2))
                    .background(track)
            ) {
                if (fill > 0f) Box(Modifier.fillMaxWidth(fill).height(height).background(color))
            }
        }
    }
}

/** A labelled figure: "So far" over "12.3 km". */
@Composable
internal fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = ZankupTheme.colors.fg, fontSize = 13.sp)
        Text(
            value,
            color = ZankupTheme.colors.fg,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold).merge(Numbers.tabular)
        )
    }
}

/** A dot and its words, for a chart's legend. */
@Composable
internal fun RowScope.LegendDot(color: Color, text: String) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
    Text(
        text,
        color = ZankupTheme.colors.muted,
        fontSize = 14.sp,
        modifier = Modifier.padding(start = 6.dp, end = 10.dp)
    )
}

/** Medals are gold, silver and bronze in every theme; past the third, the theme's grey. */
internal val MEDAL_COLOURS = listOf(Color(0xFFE8B10E), Color(0xFF8F9EA6), Color(0xFFC06D3D))

/** A medal with its place: the first is "PR". */
@Composable
internal fun Medal(rank: Int, size: Dp) {
    val colors = ZankupTheme.colors
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        ZankupIcon(
            ZIcon.Award,
            null,
            filled = true,
            tint = MEDAL_COLOURS.getOrElse(rank - 1) {
                colors.muted
            },
            size = size
        )
        Text(
            if (rank == 1) "PR" else rank.toString(),
            color = colors.bg,
            fontSize = (size.value * (if (rank == 1) 0.2f else 0.26f)).sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = size * 0.18f)
        )
    }
}
