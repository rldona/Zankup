package com.zankup.android.progress.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.zankup.android.domain.Units
import com.zankup.android.progress.R
import com.zankup.android.progress.logic.ProgressRange
import com.zankup.android.progress.logic.VolumeFormat
import com.zankup.android.progress.logic.VolumeMetric
import com.zankup.android.progress.logic.durationLabel
import com.zankup.android.ui.LocalFormats
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Totals and axis labels for the person's units and language. */
@Composable
@ReadOnlyComposable
internal fun volumeFormat(): VolumeFormat {
    val formats = LocalFormats.current
    return VolumeFormat(formats.units == Units.IMPERIAL, formats.locale)
}

/** Whether the person reads miles and feet. */
@Composable
@ReadOnlyComposable
internal fun imperial(): Boolean = LocalFormats.current.units == Units.IMPERIAL

/** A date as [pattern] writes it in the person's language. */
@Composable
@ReadOnlyComposable
internal fun date(day: LocalDate, pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, LocalFormats.current.locale).format(day)

/** "18 Nov 2026". */
@Composable
@ReadOnlyComposable
internal fun longDate(day: LocalDate) = date(day, "d MMM yyyy")

@Composable
internal fun metricShort(metric: VolumeMetric): String = stringResource(
    when (metric) {
        VolumeMetric.TIME -> R.string.progress_metric_short_time
        VolumeMetric.DISTANCE -> R.string.progress_metric_short_distance
        VolumeMetric.ASCENT -> R.string.progress_metric_short_ascent
        VolumeMetric.COUNT -> R.string.progress_metric_short_count
    }
)

@Composable
internal fun metricTotal(metric: VolumeMetric): String = stringResource(
    when (metric) {
        VolumeMetric.TIME -> R.string.progress_total_time
        VolumeMetric.DISTANCE -> R.string.progress_total_distance
        VolumeMetric.ASCENT -> R.string.progress_total_ascent
        VolumeMetric.COUNT -> R.string.progress_total_count
    }
)

@Composable
internal fun rangeName(range: ProgressRange): String = stringResource(
    when (range) {
        ProgressRange.D7 -> R.string.progress_range_7d
        ProgressRange.M1 -> R.string.progress_range_1m
        ProgressRange.M3 -> R.string.progress_range_3m
        ProgressRange.M6 -> R.string.progress_range_6m
        ProgressRange.YTD -> R.string.progress_range_ytd
        ProgressRange.Y1 -> R.string.progress_range_1y
    }
)

@Composable
internal fun versus(range: ProgressRange): String = stringResource(
    when (range) {
        ProgressRange.D7 -> R.string.progress_vs_7d
        ProgressRange.M1 -> R.string.progress_vs_1m
        ProgressRange.M3 -> R.string.progress_vs_3m
        ProgressRange.M6 -> R.string.progress_vs_6m
        ProgressRange.YTD -> R.string.progress_vs_ytd
        ProgressRange.Y1 -> R.string.progress_vs_1y
    }
)

@Composable
internal fun periodWords(range: ProgressRange): String = stringResource(
    when (range) {
        ProgressRange.D7 -> R.string.progress_period_7d
        ProgressRange.M1 -> R.string.progress_period_1m
        ProgressRange.M3 -> R.string.progress_period_3m
        ProgressRange.M6 -> R.string.progress_period_6m
        ProgressRange.YTD -> R.string.progress_period_ytd
        ProgressRange.Y1 -> R.string.progress_period_1y
    }
)

/** "Last year" or "3 years ago". */
@Composable
internal fun yearsLabel(years: Int): String = if (years ==
    1
) {
    stringResource(R.string.progress_last_year)
} else {
    pluralStringResource(R.plurals.progress_years_ago, years, years)
}

/** A best's measure: 5 km, ½ mile… Power: 30 s, Longest ride. */
@Composable
internal fun bestName(key: String): String {
    if (key.startsWith("power_")) {
        val seconds = key.removePrefix("power_").toIntOrNull() ?: 0
        return stringResource(R.string.progress_best_power, durationLabel(seconds))
    }
    if (key == "longest") return stringResource(R.string.progress_best_longest)
    val id = when (key) {
        "400m" -> R.string.progress_effort_400m
        "half_mile" -> R.string.progress_effort_half_mile
        "1k" -> R.string.progress_effort_1k
        "1mile" -> R.string.progress_effort_1mile
        "2mile" -> R.string.progress_effort_2mile
        "5k" -> R.string.progress_effort_5k
        "10k" -> R.string.progress_effort_10k
        "15k" -> R.string.progress_effort_15k
        "10mile" -> R.string.progress_effort_10mile
        "half_marathon" -> R.string.progress_effort_half_marathon
        "marathon" -> R.string.progress_effort_marathon
        else -> null
    }
    return id?.let { stringResource(it) } ?: key
}

/** "3 activities". */
@Composable
internal fun activitiesCount(count: Int): String =
    pluralStringResource(R.plurals.progress_calendar_activities, count, count)
