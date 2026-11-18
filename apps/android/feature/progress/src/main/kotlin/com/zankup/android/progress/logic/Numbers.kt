package com.zankup.android.progress.logic

import com.zankup.android.domain.formatNumber
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

internal const val MILE = 1609.344
internal const val FEET_PER_METRE = 3.28084

/** [value] with [digits] decimals for [locale]; with [trim], up to [digits] (none when whole). */
fun number(value: Double, locale: Locale, digits: Int = 0, trim: Boolean = false): String =
    formatNumber(value, locale, if (trim) 0 else digits, digits)

/** A number as the axis of a chart writes it: 12 · 4.5 · 12k. */
fun shortNumber(value: Double, locale: Locale): String {
    val a = abs(value)
    return when {
        a >= 10_000 -> "${number(value / 1000, locale, 1, trim = true)}k"
        a >= 100 || value == value.roundToLong().toDouble() -> value.roundToLong().toString()
        else -> number(value, locale, 1, trim = true)
    }
}
