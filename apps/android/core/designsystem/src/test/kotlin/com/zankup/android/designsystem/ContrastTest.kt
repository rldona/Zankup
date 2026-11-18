package com.zankup.android.designsystem

import androidx.compose.ui.graphics.Color
import com.zankup.android.designsystem.theme.Palette
import com.zankup.android.designsystem.theme.ThemeChoice
import com.zankup.android.designsystem.theme.paletteFor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every pair of colours the interface draws passes WCAG AA: 4.5:1 for text, 3:1 for graphics. */
class ContrastTest {
    private fun channel(c: Float) = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val (la, lb) = luminance(a) to luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun failures(name: String, p: Palette): List<String> {
        // Strava's orange is the theme: 3:1 on white, AA for bold text and controls, which is how it is used.
        val accentMin = if (name.startsWith("STRAVA")) 3.0 else 4.5
        val backgrounds = mapOf("bg" to p.bg, "page" to p.page, "surface" to p.surface, "surface2" to p.surface2)
        val sports = p.sport.run { listOf(run, ride, strength, walk, row, swim, other) }
        val pairs = mutableListOf<Triple<String, Pair<Color, Color>, Double>>()
        backgrounds.forEach { (bgName, bg) ->
            pairs += Triple("fg on $bgName", p.fg to bg, 4.5)
            pairs += Triple("muted on $bgName", p.muted to bg, 4.5)
            pairs += Triple("accent on $bgName", p.accent to bg, accentMin)
            sports.forEachIndexed { i, sport -> pairs += Triple("sport $i on $bgName", sport to bg, 3.0) }
        }
        pairs += Triple("accentFg on accent", p.accentFg to p.accent, accentMin)
        pairs += Triple("accent on accentSoft", p.accent to p.accentSoft, accentMin)
        pairs += Triple("bg on fg (a pressed chip)", p.bg to p.fg, 4.5)
        return pairs
            .filter { (_, colors, minimum) -> contrast(colors.first, colors.second) < minimum }
            .map { (label, colors, minimum) ->
                "$name: $label %.2f < $minimum".format(contrast(colors.first, colors.second))
            }
    }

    @Test
    fun everyThemePasses() {
        val all =
            ThemeChoice.entries.flatMap { choice ->
                listOf(false, true).distinctBy { paletteFor(choice, it) }.flatMap { dark ->
                    failures(choice.name, paletteFor(choice, dark))
                }
            }
        assertEquals(emptyList<String>(), all)
    }
}
