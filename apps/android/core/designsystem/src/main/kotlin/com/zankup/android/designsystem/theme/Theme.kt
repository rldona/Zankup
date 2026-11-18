package com.zankup.android.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

private val LocalPalette = staticCompositionLocalOf { Light }
private val LocalSpacing = staticCompositionLocalOf { Spacing() }

/**
 * Zankup's theme. [choice] is the person's theme; with [ThemeChoice.SYSTEM] it follows the phone's light
 * or dark mode and, on Android 12+, takes the phone's dynamic accent (Material You) when [dynamicAccent].
 */
@Composable
fun ZankupTheme(
    choice: ThemeChoice = ThemeChoice.SYSTEM,
    systemDark: Boolean = isSystemInDarkTheme(),
    dynamicAccent: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val base = paletteFor(choice, systemDark)
    val palette =
        remember(base, choice, dynamicAccent) {
            if (choice == ThemeChoice.SYSTEM && dynamicAccent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val dynamic = if (base.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                base.copy(
                    accent = dynamic.primary,
                    accentFg = dynamic.onPrimary,
                    accentSoft = dynamic.primaryContainer
                )
            } else {
                base
            }
        }
    CompositionLocalProvider(LocalPalette provides palette, LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = ZankupTypography,
            shapes = ZankupShapes,
            content = content
        )
    }
}

/** What screens read: `ZankupTheme.colors.accent`, `ZankupTheme.spacing.m`. */
object ZankupTheme {
    val colors: Palette
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current
}

/** Material's own components (buttons, switches, sheets…) drawn with the palette. */
internal fun Palette.toColorScheme(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = accent,
        onPrimary = accentFg,
        primaryContainer = accentSoft,
        onPrimaryContainer = fg,
        secondary = accent,
        onSecondary = accentFg,
        secondaryContainer = accentSoft,
        onSecondaryContainer = fg,
        tertiary = accent,
        onTertiary = accentFg,
        background = bg,
        onBackground = fg,
        surface = surface,
        onSurface = fg,
        surfaceVariant = surface2,
        onSurfaceVariant = muted,
        surfaceContainerLowest = page,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface2,
        surfaceContainerHighest = surface2,
        surfaceBright = surface,
        surfaceDim = bg,
        outline = muted,
        outlineVariant = line,
        error = formNegative,
        onError = accentFg
    )
}
