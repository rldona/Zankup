package com.zankup.android.designsystem.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.R
import com.zankup.android.designsystem.theme.ZankupTheme

/** ZANKUP in one colour, from docs/brand/assets/wordmark.svg (scripts/brand.py). */
@Composable
fun Wordmark(modifier: Modifier = Modifier, height: Dp = 20.dp, color: Color = ZankupTheme.colors.fg) {
    Image(
        painter = painterResource(R.drawable.zankup_wordmark),
        contentDescription = stringResource(R.string.brand_name),
        modifier = modifier.height(height),
        colorFilter = ColorFilter.tint(color)
    )
}

/** The stride mark in one colour, from docs/brand/assets/logo-mark.svg. */
@Composable
fun Mark(modifier: Modifier = Modifier, height: Dp = 28.dp, color: Color = ZankupTheme.colors.fg) {
    Image(
        painter = painterResource(R.drawable.zankup_mark),
        contentDescription = stringResource(R.string.brand_name),
        modifier = modifier.height(height),
        colorFilter = ColorFilter.tint(color)
    )
}
