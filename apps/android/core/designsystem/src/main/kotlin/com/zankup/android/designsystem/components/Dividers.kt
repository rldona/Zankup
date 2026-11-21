package com.zankup.android.designsystem.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.theme.ZankupTheme

/**
 * A hairline between rows: 0.5 dp, in the line colour and inset as much as the row's content.
 * [inset] is the card's own padding for custom rows, and 16 dp for a [ListRow], whose text starts there.
 */
@Composable
fun RowDivider(modifier: Modifier = Modifier, inset: Dp = ZankupTheme.spacing.m) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = inset),
        thickness = 0.5.dp,
        color = ZankupTheme.colors.line
    )
}

/** A line between sections or widgets: 1 dp, in the line colour, as wide as its container. */
@Composable
fun SectionDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = ZankupTheme.colors.line)
}
