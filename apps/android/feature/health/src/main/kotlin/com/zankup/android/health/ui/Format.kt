package com.zankup.android.health.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zankup.android.domain.Units
import com.zankup.android.domain.formatNumber
import com.zankup.android.health.HealthSource
import com.zankup.android.health.R
import com.zankup.android.health.RelativeDay
import com.zankup.android.health.relativeDay
import com.zankup.android.health.toUnit
import com.zankup.android.health.weightUnit
import com.zankup.android.ui.LocalFormats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** A date in the locale's own order for the fields given ("EEEEdMMMM": "Wednesday 18 November"). */
internal fun dayText(day: LocalDate, skeleton: String, locale: Locale): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(day)

@Composable
internal fun longDay(day: LocalDate) = dayText(day, "EEEEdMMMM", LocalFormats.current.locale)

@Composable
internal fun shortDay(day: LocalDate) = dayText(day, "dMMM", LocalFormats.current.locale)

/** "18 November 2026", for the weighings. */
@Composable
internal fun fullDay(day: LocalDate) = dayText(day, "dMMMMyyyy", LocalFormats.current.locale)

/** "Today, …", "Yesterday, …" or the date, capitalised. */
@Composable
internal fun dayTitle(day: LocalDate, today: LocalDate): String {
    val text = longDay(day)
    return when (relativeDay(day, today)) {
        RelativeDay.TODAY -> stringResource(R.string.health_today, text)
        RelativeDay.YESTERDAY -> stringResource(R.string.health_yesterday, text)
        RelativeDay.OTHER -> text.replaceFirstChar { it.titlecase(LocalFormats.current.locale) }
    }
}

@Composable
internal fun one(value: Double) = formatNumber(value, LocalFormats.current.locale, 0, 1)

@Composable
internal fun sourceName(source: HealthSource): String = stringResource(
    when (source) {
        HealthSource.GARMIN -> R.string.health_source_garmin
        HealthSource.GOOGLE_HEALTH -> R.string.health_source_google_health
        HealthSource.HEALTH_CONNECT -> R.string.health_source_health_connect
    }
)

/** "From Garmin", or "From Garmin · also recorded by Health Connect". */
@Composable
internal fun fromText(source: HealthSource, others: List<HealthSource>): String {
    val name = sourceName(source)
    if (others.isEmpty()) return stringResource(R.string.health_from, name)
    val rest = others.map { sourceName(it) }.joinToString(", ")
    return stringResource(R.string.health_from_also, name, rest)
}

/** A weight in the person's unit, without it: 72,5. */
internal fun weightValue(kg: Double, units: Units, locale: Locale): String =
    formatNumber(toUnit(kg, units), locale, 0, 1)

/** A weight with its unit: 72,5 kg or 159,8 lb. */
@Composable
internal fun weightText(kg: Double): String {
    val f = LocalFormats.current
    return "${weightValue(kg, f.units, f.locale)} ${weightUnit(f.units)}"
}

/** A change with its sign and unit: +0,4 kg, −1,2 lb. */
@Composable
internal fun signedWeight(kg: Double): String {
    val sign = when {
        kg > 0 -> "+"
        kg < 0 -> "−"
        else -> ""
    }
    return sign + weightText(abs(kg))
}
