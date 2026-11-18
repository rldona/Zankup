package com.zankup.android.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every colour a screen may use. Screens never write a colour themselves: they read it from
 * [ZankupTheme.colors], so a new theme touches no screen.
 */
@Immutable
data class Palette(
    val isDark: Boolean,
    /** Behind everything (the window). */
    val bg: Color,
    /** Behind a screen's content, where it differs from [bg] (Strava's white page on grey). */
    val page: Color,
    val surface: Color,
    val surface2: Color,
    val fg: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentFg: Color,
    val accentSoft: Color,
    val sport: SportColors,
    val zones: List<Color> = ZONES,
    val fitness: Color = Color(0xFF1C7ED6),
    val fatigue: Color = Color(0xFFE64980),
    val formPositive: Color = Color(0xFF40C057),
    val formNegative: Color = Color(0xFFFA5252),
    val sleep: SleepColors = SleepColors(),
    val hrv: Color = Color(0xFF12B886)
)

@Immutable
data class SportColors(
    val run: Color,
    val ride: Color,
    val strength: Color,
    val walk: Color,
    val row: Color,
    val swim: Color,
    val other: Color
)

@Immutable
data class SleepColors(
    val deep: Color = Color(0xFF364FC7),
    val light: Color = Color(0xFF74C0FC),
    val rem: Color = Color(0xFF9775FA),
    val awake: Color = Color(0xFFFFA94D)
)

/** Heart-rate, pace and power zones 1 to 5, the same in every theme. */
val ZONES =
    listOf(
        Color(0xFFA5D8FF),
        Color(0xFF69DB7C),
        Color(0xFFFFD43B),
        Color(0xFFFF922B),
        Color(0xFFF03E3E)
    )
