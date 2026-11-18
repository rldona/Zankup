package com.zankup.android.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
)

/**
 * The phone's own font (Roboto, or the maker's), as the rest of Android. Big figures are bold and tight;
 * labels are medium. Numbers use tabular figures where they line up ([Numbers]).
 */
val ZankupTypography =
    Typography(
        displayLarge = style(48, 52, FontWeight.Bold, -0.02),
        displayMedium = style(40, 44, FontWeight.Bold, -0.02),
        displaySmall = style(32, 36, FontWeight.Bold, -0.01),
        headlineLarge = style(28, 34, FontWeight.Bold, -0.01),
        headlineMedium = style(24, 30, FontWeight.Bold),
        headlineSmall = style(20, 26, FontWeight.Bold),
        titleLarge = style(20, 26, FontWeight.SemiBold),
        titleMedium = style(16, 22, FontWeight.SemiBold),
        titleSmall = style(14, 20, FontWeight.SemiBold),
        bodyLarge = style(16, 24, FontWeight.Normal),
        bodyMedium = style(14, 20, FontWeight.Normal),
        bodySmall = style(12, 16, FontWeight.Normal),
        labelLarge = style(14, 20, FontWeight.Medium),
        labelMedium = style(12, 16, FontWeight.Medium),
        labelSmall = style(11, 14, FontWeight.Medium, 0.02)
    )

/** Figures that must line up in columns or not jump while they change (splits, timers, the cursor). */
object Numbers {
    val tabular = TextStyle(fontFeatureSettings = "tnum")
}
