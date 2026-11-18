package com.zankup.android.tools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.tools.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A stacked screen of this module: the bar, then its items in a list with the page's gutters. */
@Composable
internal fun ToolsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    val colors = ZankupTheme.colors
    Scaffold(
        modifier = modifier,
        containerColor = colors.page,
        topBar = { ZankupTopBar(title, onBack = onBack, actions = actions) }
    ) { padding ->
        val direction = LocalLayoutDirection.current
        val spacing = ZankupTheme.spacing
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction) + spacing.gutter,
                end = padding.calculateEndPadding(direction) + spacing.gutter,
                top = padding.calculateTopPadding() + spacing.m,
                bottom = padding.calculateBottomPadding() + spacing.l
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            content = content
        )
    }
}

/** A muted line, 13 sp as the React Native app's `Muted`. */
@Composable
internal fun Muted(text: String, modifier: Modifier = Modifier, color: Color = ZankupTheme.colors.muted) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
        color = color
    )
}

/** A heading on the page or in a card, as the React Native app's `Heading`: 17 sp, bold. */
@Composable
internal fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.semantics { heading() },
        color = ZankupTheme.colors.fg,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold
    )
}

/** A line TalkBack reads out as it appears: an error, a run's state. */
@Composable
internal fun Announced(text: String, modifier: Modifier = Modifier, color: Color = ZankupTheme.colors.muted) {
    Muted(text, modifier.semantics { liveRegion = LiveRegionMode.Polite }, color)
}

/** The red of zone 5, for what went past its limit or failed. */
internal val warning: Color @Composable get() = ZankupTheme.colors.zones[4]

internal enum class Tone { NEUTRAL, GOOD, BAD }

/** A small pill: the accent when good, red when bad, grey otherwise. */
@Composable
internal fun Badge(text: String, tone: Tone = Tone.NEUTRAL) {
    val colors = ZankupTheme.colors
    val (bg, fg) = when (tone) {
        Tone.GOOD -> colors.accent to colors.accentFg
        Tone.BAD -> warning to Color.White
        Tone.NEUTRAL -> colors.surface2 to colors.fg
    }
    Surface(shape = RoundedCornerShape(999.dp), color = bg, contentColor = fg) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** A bar of [value] out of [max], read by TalkBack as [label]. */
@Composable
internal fun Bar(value: Float, label: String, modifier: Modifier = Modifier, color: Color = ZankupTheme.colors.accent) {
    LinearProgressIndicator(
        progress = { value.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(6.dp).semantics { contentDescription = label },
        color = color,
        trackColor = ZankupTheme.colors.surface2,
        drawStopIndicator = {}
    )
}

/** The (i): its explanation in a dialog. */
@Composable
internal fun InfoButton(text: String) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.tools_more_info)
    IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
        ZankupIcon(ZIcon.Info, contentDescription = label, tint = ZankupTheme.colors.muted, size = 20.dp)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.tools_close)) } }
        )
    }
}

/** A list's shape while its first answer loads. */
@Composable
internal fun ListSkeleton(rows: Int = 3) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(rows) { Skeleton(Modifier.fillMaxWidth(), height = 96.dp) }
    }
}

/** "18 Nov 2026" in the phone's zone. */
internal fun mediumDate(at: Instant, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault()).format(at)

/** "18 Nov 2026, 07:30" in the phone's zone. */
internal fun mediumDateTime(at: Instant, locale: Locale): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(locale)
    .withZone(ZoneId.systemDefault())
    .format(at)
