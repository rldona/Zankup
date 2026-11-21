package com.zankup.android.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.designsystem.theme.ZankupTheme
import kotlin.math.abs

/**
 * A card: the surface on the page, 14 dp inside. A card that fills the screen's width between the page's
 * gutters bleeds to the screen's edges and is square, as Strava's sections (the page shows between them);
 * a card that shares a row, or has its own margins, keeps its 10 dp corners. A hairline in the themes that
 * draw lines (High contrast). Tappable when [onClick] is given, with Android's ripple.
 */
@Composable
fun ZankupCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /**
     * Whether what is inside is cut to the card. A card with a chart turns it off, so the chart's bubble
     * can rise over the title and the card above; the card still looks the same. Tappable cards clip.
     */
    clip: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = ZankupTheme.colors
    val gutter = ZankupTheme.spacing.gutter
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val border = if (colors.line == colors.fg) BorderStroke(2.dp, colors.line) else null
    val shape = remember(screenWidthPx, density) { BleedShape(screenWidthPx, with(density) { 10.dp.toPx() }) }
    val gutterPx = with(density) { gutter.roundToPx() }
    // Between the gutters of a full-width row the card grows by them on both sides (to the screen's edges).
    val width =
        Modifier.layout { measurable, constraints ->
            val spread = constraints.maxWidth + 2 * gutterPx - screenWidthPx
            val full = constraints.hasBoundedWidth && abs(spread) <= 6f
            val extra = if (full) 2 * gutterPx else 0
            val placeable =
                measurable.measure(
                    constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra)
                )
            layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
        }
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), content = content)
    }
    if (!clip && onClick == null) {
        // As the Surface below, its background and hairline drawn behind, but nothing clipped. Above the
        // cards before it, so a bubble rising out of it is not drawn under them.
        Box(
            modifier
                .then(width)
                .zIndex(1f)
                .background(colors.surface, shape)
                .then(if (border != null) Modifier.border(border, shape) else Modifier)
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.fg) { inner() }
        }
    } else if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.then(width),
            shape = shape,
            color = colors.surface,
            contentColor = colors.fg,
            border = border,
            content = inner
        )
    } else {
        Surface(
            modifier = modifier.then(width),
            shape = shape,
            color = colors.surface,
            contentColor = colors.fg,
            border = border,
            content = inner
        )
    }
}

/** Square when as wide as the screen (a card bled to the edges), rounded otherwise. */
private class BleedShape(private val screenWidthPx: Float, private val radiusPx: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        if (size.width >= screenWidthPx - 6f) {
            Outline.Rectangle(size.toRect())
        } else {
            Outline.Rounded(RoundRect(size.toRect(), CornerRadius(radiusPx)))
        }
}

/** A section's title above its cards, with an optional action on the right ("See all"). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(vertical = ZankupTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = ZankupTheme.colors.fg)
        action?.invoke(this)
    }
}

/**
 * One row of a list or of Settings: an icon, a title and a subtitle, a value and a chevron when it
 * opens something. At least 56 dp tall.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ZIcon? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    /** A hairline under it, as the React Native app's menu rows (all but a card's last). */
    divider: Boolean = false
) {
    val colors = ZankupTheme.colors
    val content: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(
                    if (divider) {
                        Modifier.drawBehind {
                            val y = size.height - 0.5.dp.toPx()
                            drawLine(
                                colors.line,
                                Offset(16.dp.toPx(), y),
                                Offset(size.width - 16.dp.toPx(), y),
                                0.5.dp.toPx()
                            )
                        }
                    } else {
                        Modifier
                    }
                ).padding(horizontal = ZankupTheme.spacing.m, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // As the React Native app's rows: the icon in the text colour, the title bold.
            if (icon != null) ZankupIcon(icon, contentDescription = null, tint = colors.fg)
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.fg
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            if (onClick != null) ZankupIcon(ZIcon.Chevron, contentDescription = null, tint = colors.muted, size = 20.dp)
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, color = colors.surface, content = content)
    } else {
        Surface(modifier = modifier, color = colors.surface, content = content)
    }
}
