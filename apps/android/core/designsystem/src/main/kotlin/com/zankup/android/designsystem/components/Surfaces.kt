package com.zankup.android.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.designsystem.theme.ZankupTheme

/**
 * A card: the surface on the page, 16 dp round and 14 dp inside as the React Native app's cards, with a
 * hairline in the themes that draw lines (High contrast). Tappable when [onClick] is given, with Android's
 * ripple.
 */
@Composable
fun ZankupCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = ZankupTheme.colors
    val border = if (colors.line == colors.fg) BorderStroke(2.dp, colors.line) else null
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(14.dp), content = content)
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            contentColor = colors.fg,
            border = border,
            content = inner
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            contentColor = colors.fg,
            border = border,
            content = inner
        )
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
        Text(title, style = MaterialTheme.typography.titleLarge, color = ZankupTheme.colors.fg)
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
