package com.zankup.android.designsystem.theme

import androidx.compose.ui.graphics.Color

/** The themes a person can choose in Settings. [SYSTEM] follows the phone and takes its dynamic accent. */
enum class ThemeChoice(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    OLED("oled"),
    HIGH_CONTRAST("highContrast"),
    FOREST("forest"),
    OCEAN("ocean"),
    SUNSET("sunset"),
    STRAVA("strava"),
    STRAVA_DARK("stravaDark")
    ;

    companion object {
        /** The key the API stores in the settings (`theme`), unknown keys falling back to [SYSTEM]. */
        fun fromKey(key: String?): ThemeChoice = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** The palette of a choice; [SYSTEM] resolves to light or dark here, its dynamic accent in [ZankupTheme]. */
fun paletteFor(choice: ThemeChoice, systemDark: Boolean): Palette = when (choice) {
    ThemeChoice.SYSTEM -> if (systemDark) Dark else Light
    ThemeChoice.LIGHT -> Light
    ThemeChoice.DARK -> Dark
    ThemeChoice.OLED -> Oled
    ThemeChoice.HIGH_CONTRAST -> HighContrast
    ThemeChoice.FOREST -> Forest
    ThemeChoice.OCEAN -> Ocean
    ThemeChoice.SUNSET -> Sunset
    ThemeChoice.STRAVA -> Strava
    ThemeChoice.STRAVA_DARK -> StravaDark
}

private fun c(hex: Long) = Color(0xFF000000 or hex)

val Light =
    Palette(
        isDark = false,
        bg = c(0xF4F5F3),
        page = c(0xF4F5F3),
        surface = c(0xFFFFFF),
        surface2 = c(0xF8F8F6),
        fg = c(0x17191A),
        muted = c(0x676B6E),
        line = c(0xE3E4E0),
        accent = c(0x087453),
        accentFg = c(0xFFFFFF),
        accentSoft = c(0xE2F4EE),
        sport = SportColors(c(0xE8590C), c(0x1C7ED6), c(0x7048E8), c(0x2B8A3E), c(0xC2255C), c(0x0C8599), c(0x676B6E))
    )

val Dark =
    Palette(
        isDark = true,
        bg = c(0x0F1112),
        page = c(0x0F1112),
        surface = c(0x181B1D),
        surface2 = c(0x1E2124),
        fg = c(0xECEEED),
        muted = c(0x9AA0A3),
        line = c(0x2A2E31),
        accent = c(0x20C997),
        accentFg = c(0x06221A),
        accentSoft = c(0x13302A),
        sport = SportColors(c(0xFF8A3D), c(0x4DABF7), c(0x9775FA), c(0x51CF66), c(0xF06595), c(0x3BC9DB), c(0x9AA0A3))
    )

val Oled =
    Palette(
        isDark = true,
        bg = c(0x000000),
        page = c(0x000000),
        surface = c(0x0B0B0B),
        surface2 = c(0x141414),
        fg = c(0xF2F2F2),
        muted = c(0x8F8F8F),
        line = c(0x1F1F1F),
        accent = c(0x38D9A9),
        accentFg = c(0x001A12),
        accentSoft = c(0x0D2A21),
        sport = SportColors(c(0xFF922B), c(0x4DABF7), c(0x9775FA), c(0x69DB7C), c(0xF06595), c(0x3BC9DB), c(0x8F8F8F))
    )

val HighContrast =
    Palette(
        isDark = false,
        bg = c(0xFFFFFF),
        page = c(0xFFFFFF),
        surface = c(0xFFFFFF),
        surface2 = c(0xEEEEEE),
        fg = c(0x000000),
        muted = c(0x262626),
        line = c(0x000000),
        accent = c(0x0033CC),
        accentFg = c(0xFFFFFF),
        accentSoft = c(0xD6E0FF),
        sport = SportColors(c(0xB33C00), c(0x0050B3), c(0x5A2FC2), c(0x1D6B2F), c(0xA61E4D), c(0x0B7285), c(0x262626))
    )

val Forest =
    Palette(
        isDark = true,
        bg = c(0x0D1611),
        page = c(0x0D1611),
        surface = c(0x132019),
        surface2 = c(0x1A2A21),
        fg = c(0xE4EFE7),
        muted = c(0x98AE9F),
        line = c(0x24372C),
        accent = c(0x8CE99A),
        accentFg = c(0x0A1A0F),
        accentSoft = c(0x1D3A26),
        sport = SportColors(c(0xFFA94D), c(0x74C0FC), c(0xB197FC), c(0x8CE99A), c(0xF783AC), c(0x66D9E8), c(0x98AE9F))
    )

val Ocean =
    Palette(
        isDark = false,
        bg = c(0xEDF3F9),
        page = c(0xEDF3F9),
        surface = c(0xFFFFFF),
        surface2 = c(0xF2F7FC),
        fg = c(0x0E2233),
        muted = c(0x57708A),
        line = c(0xD3E1EE),
        accent = c(0x1864AB),
        accentFg = c(0xFFFFFF),
        accentSoft = c(0xDBEAFA),
        sport = SportColors(c(0xE8590C), c(0x1864AB), c(0x6741D9), c(0x2B8A3E), c(0xC2255C), c(0x0C8599), c(0x57708A))
    )

val Sunset =
    Palette(
        isDark = false,
        bg = c(0xFBF3EC),
        page = c(0xFBF3EC),
        surface = c(0xFFFAF5),
        surface2 = c(0xF8EBE0),
        fg = c(0x2B1C12),
        muted = c(0x7F6656),
        line = c(0xEFD9C8),
        accent = c(0xB5370A),
        accentFg = c(0xFFFFFF),
        accentSoft = c(0xFFE6D5),
        sport = SportColors(c(0xC2410C), c(0x1C7ED6), c(0x7048E8), c(0x2B8A3E), c(0xA61E4D), c(0x0C8599), c(0x7F6656))
    )

val Strava =
    Palette(
        isDark = false,
        bg = c(0xF0F0F0),
        page = c(0xFFFFFF),
        surface = c(0xFFFFFF),
        surface2 = c(0xF7F7FA),
        fg = c(0x242428),
        muted = c(0x626270),
        line = c(0xE6E6EB),
        accent = c(0xF54F00),
        accentFg = c(0xFFFFFF),
        accentSoft = c(0xFFF1EA),
        sport = SportColors(c(0xF54F00), c(0x1C7ED6), c(0x7048E8), c(0x2B8A3E), c(0xC2255C), c(0x0C8599), c(0x626270))
    )

val StravaDark =
    Palette(
        isDark = true,
        bg = c(0x000000),
        page = c(0x000000),
        surface = c(0x141414),
        surface2 = c(0x212121),
        fg = c(0xF5F5F5),
        muted = c(0xA6A6A6),
        line = c(0x262626),
        accent = c(0xFC5200),
        accentFg = c(0xFFFFFF),
        accentSoft = c(0x33190B),
        sport = SportColors(c(0xFC5200), c(0x4DABF7), c(0x9775FA), c(0x51CF66), c(0xF06595), c(0x3BC9DB), c(0xA6A6A6))
    )
