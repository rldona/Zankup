package com.zankup.android.designsystem.theme

import androidx.annotation.StringRes
import com.zankup.android.designsystem.R

/** The name Settings shows for each theme. */
@get:StringRes
val ThemeChoice.label: Int
    get() =
        when (this) {
            ThemeChoice.SYSTEM -> R.string.theme_system
            ThemeChoice.LIGHT -> R.string.theme_light
            ThemeChoice.DARK -> R.string.theme_dark
            ThemeChoice.OLED -> R.string.theme_oled
            ThemeChoice.HIGH_CONTRAST -> R.string.theme_high_contrast
            ThemeChoice.FOREST -> R.string.theme_forest
            ThemeChoice.OCEAN -> R.string.theme_ocean
            ThemeChoice.SUNSET -> R.string.theme_sunset
            ThemeChoice.STRAVA -> R.string.theme_strava
            ThemeChoice.STRAVA_DARK -> R.string.theme_strava_dark
        }
