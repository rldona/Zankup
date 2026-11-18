package com.zankup.android.progress.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.color
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.progress.R
import com.zankup.android.ui.family
import com.zankup.android.ui.sportName

/** A card of the tab: the whole card opens its page. */
@Composable
internal fun ProgressCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    description: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    ZankupCard(
        modifier = if (description != null) modifier.semantics { contentDescription = description } else modifier,
        onClick = onClick,
        content = content
    )
}

/** A small muted line under a figure: dates, notes (13 sp, as the React Native app's). */
internal val SmallText = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)

/** A card's title row: its icon in the accent, the title (16 sp bold), a note on the right and the chevron. */
@Composable
internal fun CardHeader(
    title: String,
    icon: ZIcon? = null,
    note: String? = null,
    chevron: Boolean = true,
    iconSize: Int = 26
) {
    val colors = ZankupTheme.colors
    val gap = if (iconSize < 26) 6 else 10
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
        if (icon != null) ZankupIcon(icon, contentDescription = null, tint = colors.accent, size = iconSize.dp)
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (note != null) Text(note, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        if (chevron) ZankupIcon(ZIcon.Chevron, contentDescription = null, tint = colors.muted, size = 22.dp)
    }
}

/** A muted line: what a card says when it has nothing to show. */
@Composable
internal fun Muted(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    Text(text, modifier = modifier, style = style, color = ZankupTheme.colors.muted)
}

/** A label over a figure. */
@Composable
internal fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = ZankupTheme.colors.fg)
}

/** A card's big figure: 30 sp, heavy, tabular. */
@Composable
internal fun BigFigure(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 30,
    color: Color = ZankupTheme.colors.fg
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        style = TextStyle(fontSize = size.sp, fontWeight = FontWeight.ExtraBold).merge(Numbers.tabular),
        maxLines = 1
    )
}

/** A figure in a row of totals: 18 sp, heavy. */
@Composable
internal fun Figure(text: String, modifier: Modifier = Modifier) = BigFigure(text, modifier, size = 18)

/** A small badge with an arrow and a figure: ▲ 3 or ▼ 27s; the arrow green when it is good news. */
@Composable
internal fun ChangePill(up: Boolean, text: String, good: Boolean) {
    val colors = ZankupTheme.colors
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(colors.surface2).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(if (up) "▲" else "▼", color = if (good) colors.formPositive else colors.muted, fontSize = 11.sp)
        Text(text, style = MaterialTheme.typography.bodyMedium.merge(Numbers.tabular), color = colors.fg)
    }
}

/** A card before it loads: a title and some lines. */
@Composable
internal fun CardSkeleton(lines: Int = 3) {
    ZankupCard {
        Skeleton(Modifier.fillMaxWidth(0.45f), height = 16.dp)
        repeat(lines) { i ->
            Spacer(Modifier.height(10.dp))
            Skeleton(Modifier.fillMaxWidth(if (i == lines - 1) 0.7f else 1f), height = 14.dp)
        }
    }
}

/** A chart before it loads. */
@Composable
internal fun ChartSkeleton(height: Int = 160) {
    ZankupCard {
        Skeleton(Modifier.fillMaxWidth(0.5f), height = 16.dp)
        Spacer(Modifier.height(10.dp))
        Skeleton(Modifier.fillMaxWidth(), height = height.dp)
    }
}

/** The (i) of a header: a tap opens [text] under it, and a tap anywhere closes it. */
@Composable
internal fun InfoButton(text: String, tint: Color = ZankupTheme.colors.fg) {
    var open by remember { mutableStateOf(false) }
    val colors = ZankupTheme.colors
    Box {
        IconButton(onClick = { open = !open }) {
            InfoGlyph(tint, stringResource(R.string.progress_common_more_info))
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, 120),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 360.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface2,
                    contentColor = colors.fg,
                    border = BorderStroke(1.dp, colors.line),
                    shadowElevation = 6.dp,
                    onClick = { open = false }
                ) {
                    Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** The header's (i). */
@Composable
private fun InfoGlyph(tint: Color, description: String) {
    ZankupIcon(ZIcon.Info, contentDescription = description, tint = tint)
}

/** The left chevron. */
@Composable
internal fun ChevronLeft(tint: Color, description: String?, size: Int = 30) {
    ZankupIcon(ZIcon.ChevronLeft, contentDescription = description, tint = tint, size = size.dp)
}

/** A sport's colour, or the muted grey for none. */
@Composable
internal fun sportColor(sport: Sport?): Color = sport?.family?.color ?: ZankupTheme.colors.muted

/** A sport's icon, or Progress's for all sports. */
internal fun sportIcon(sport: Sport?): ZIcon = sport?.family?.icon ?: ZIcon.Progress

/** A sport's name, or "All sports". */
@Composable
internal fun sportLabel(sport: Sport?): String =
    if (sport == null) stringResource(R.string.progress_all_sports) else stringResource(sportName(sport))

/**
 * A chip of Progress: an outline in the accent when on, as the React Native app's, with an optional
 * icon before the label.
 */
@Composable
internal fun ProgressChip(label: String, on: Boolean, onClick: () -> Unit, icon: ZIcon? = null) {
    val colors = ZankupTheme.colors
    val tint = if (on) colors.accent else colors.fg
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .border(1.25.dp, if (on) colors.accent else colors.line, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) ZankupIcon(icon, contentDescription = null, tint = tint, size = 18.dp)
        Text(label, color = tint, fontSize = 14.sp)
    }
}

/** A wide outlined button: "See your progress in more detail", "Compare date range". */
@Composable
internal fun OutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val colors = ZankupTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.bg)
            .border(1.5.dp, colors.line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** A dot of a colour, before a legend's words. */
@Composable
internal fun Dot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

/** A sheet's title. */
@Composable
internal fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = ZankupTheme.colors.fg
    )
}

/** A sport's round tile: its icon on its colour. */
@Composable
internal fun SportTile(sport: Sport, size: Int = 36) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(sportColor(sport)),
        contentAlignment = Alignment.Center
    ) {
        ZankupIcon(sportIcon(sport), contentDescription = null, tint = Color.White, size = (size * 0.55f).dp)
    }
}

/** Space between the blocks of a column. */
@Composable
internal fun Gap(height: Int) = Spacer(Modifier.height(height.dp))

/** A column with [gap] dp between its children. */
@Composable
internal fun Stack(
    modifier: Modifier = Modifier,
    gap: Int = 12,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap.dp), content = content)
}
