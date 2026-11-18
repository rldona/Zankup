package com.zankup.android.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.zankup.android.domain.Sport
import com.zankup.android.domain.UnitFormat
import com.zankup.android.domain.Units
import com.zankup.android.domain.formatDuration
import com.zankup.android.domain.formatHoursMinutes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * How figures are written for this person: their units (from the settings) and the phone's language.
 * Screens read it as [LocalFormats]; the app provides it once the settings are known.
 */
@Immutable
class Formats(val units: Units, val locale: Locale) {
    val unit = UnitFormat(units, locale)

    fun distance(metres: Double?) = metres?.takeIf { it > 0 }?.let(unit::distance) ?: "—"

    fun elevation(metres: Double?) = metres?.let(unit::elevation) ?: "—"

    fun duration(seconds: Double?) = seconds?.let(::formatDuration) ?: "—"

    fun hoursMinutes(seconds: Double?) = seconds?.let(::formatHoursMinutes) ?: "—"

    /** A run's pace or a ride's speed, from the distance and the moving time. */
    fun speedFor(sport: Sport, metres: Double?, seconds: Double?): String {
        if (metres == null || seconds == null || metres <= 0 || seconds <= 0) return "—"
        return unit.speedFor(metres / seconds, sport)
    }

    /** "Tue 17 Nov · 07:30" in the activity's own time zone (where it happened), else the phone's. */
    fun activityDate(startUtc: String, timezone: String?): String {
        val zone = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        val at = runCatching { Instant.parse(startUtc).atZone(zone) }.getOrNull() ?: return startUtc
        val day = DateTimeFormatter.ofPattern("EEE d MMM", locale).format(at)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(at)
        return "${day.replaceFirstChar { it.titlecase(locale) }} · $time"
    }

    companion object {
        val Default = Formats(Units.METRIC, Locale.getDefault())
    }
}

val LocalFormats = staticCompositionLocalOf { Formats.Default }
