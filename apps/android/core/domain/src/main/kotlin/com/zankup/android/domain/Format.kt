package com.zankup.android.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

/** What the figures are written with when there is none: a pace while standing still. */
const val NO_VALUE = "—"

private const val METRES_PER_MILE = 1609.344
private const val METRES_PER_FOOT = 0.3048

/** Slower than this (m/s) a pace means nothing: an hour and a half per kilometre. */
private const val MIN_PACE_SPEED = 0.3

/**
 * A number written for [locale], with between [minDecimals] and [maxDecimals] decimals: 1234.5 is
 * "1,234.5" in English and "1.234,5" in Spanish: thousands grouped from four digits, as the React Native
 * app writes them ("2.150 pasos").
 *
 * Halves round away from zero, and the number is first read as the decimal it prints as (2.675, not the
 * 2.67499… the double holds), so the app writes the same digits as the web.
 */
fun formatNumber(value: Double, locale: Locale, minDecimals: Int = 0, maxDecimals: Int = max(minDecimals, 0)): String {
    if (!value.isFinite()) return NO_VALUE
    val rounded = BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP)
    val format = (NumberFormat.getNumberInstance(locale) as DecimalFormat).apply {
        minimumFractionDigits = minDecimals
        maximumFractionDigits = maxDecimals
        roundingMode = RoundingMode.HALF_UP
    }
    return format.format(rounded)
}

/** A time as the watch shows it: 305 s is "5:05", 3725 s is "1:02:05". Never negative. */
fun formatDuration(seconds: Double): String {
    val total = max(0L, seconds.roundToLong())
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val secs = total % 60
    return if (hours > 0) {
        "$hours:${pad2(minutes)}:${pad2(secs)}"
    } else {
        "$minutes:${pad2(secs)}"
    }
}

/** A long time in words, to the minute: 3725 s is "1 h 2 min", 600 s is "10 min". */
fun formatHoursMinutes(seconds: Double): String {
    val minutes = max(0L, (seconds / 60).roundToLong())
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

private fun pad2(n: Long) = n.toString().padStart(2, '0')

/** Kilometres or miles, as the units say. */
val Units.distanceUnit: String get() = if (this == Units.METRIC) "km" else "mi"

/** Kilometres per hour or miles per hour. */
val Units.speedUnit: String get() = if (this == Units.METRIC) "km/h" else "mph"

/**
 * The figures of an activity in the person's [units] and [locale], which are separate settings (a
 * Spanish speaker may run in miles). Everything comes in as the API sends it: metres, seconds, m/s.
 *
 * The `…Value` functions write the number alone, for a screen that sets the unit in a smaller style;
 * the others add the unit.
 */
class UnitFormat(val units: Units, val locale: Locale) {
    private val metresPerUnit = if (units == Units.METRIC) 1000.0 else METRES_PER_MILE

    /** "km" or "mi". */
    val distanceUnit: String get() = units.distanceUnit

    /** Two decimals: 6615 m is "6.62" (en) or "6,62" (es), 1609.344 m is "1.00" in miles. */
    fun distanceValue(metres: Double): String = formatNumber(metres / metresPerUnit, locale, 2, 2)

    /** "6.62 km". */
    fun distance(metres: Double): String = "${distanceValue(metres)} $distanceUnit"

    /** Time per km or mile: 2.93 m/s is "5:41" per km; [NO_VALUE] when standing still. */
    fun paceValue(metresPerSecond: Double): String =
        if (metresPerSecond > MIN_PACE_SPEED) formatDuration(metresPerUnit / metresPerSecond) else NO_VALUE

    /** "5:41 /km". */
    fun pace(metresPerSecond: Double): String = "${paceValue(metresPerSecond)} /$distanceUnit"

    /** Up to one decimal, none when it is whole: 8.4 m/s is "30.2" (en) or "30,2" (es) km/h. */
    fun speedValue(metresPerSecond: Double): String =
        formatNumber(metresPerSecond * 3600 / metresPerUnit, locale, maxDecimals = 1)

    /** "30.2 km/h". */
    fun speed(metresPerSecond: Double): String = "${speedValue(metresPerSecond)} ${units.speedUnit}"

    /** Pace for the sports that read pace ([Sport.usesPace]), speed for the rest. */
    fun speedFor(metresPerSecond: Double, sport: Sport): String =
        if (sport.usesPace) pace(metresPerSecond) else speed(metresPerSecond)

    /** Whole metres or feet: 123 m is "123 m" or "404 ft". */
    fun elevation(metres: Double): String {
        val value = if (units == Units.METRIC) metres else metres / METRES_PER_FOOT
        val unit = if (units == Units.METRIC) "m" else "ft"
        return "${formatNumber(value.roundToLong().toDouble(), locale)} $unit"
    }
}
