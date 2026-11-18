package com.zankup.android.routes

import com.zankup.android.map.Route
import com.zankup.android.ui.decodePolyline

/** One time done of a route or one effort on a segment, as a list row. */
internal class Effort(val activityId: Long, val day: String, val seconds: Double, val extra: String?)

/** The fastest of the times, or null when there are none. */
internal fun fastest(seconds: List<Double>): Double? = seconds.minOrNull()

/** Seconds behind the fastest; zero for the fastest itself. */
internal fun behind(seconds: Double, fastest: Double): Double = (seconds - fastest).coerceAtLeast(0.0)

/** The API sends the oldest first; the list shows the newest first. */
internal fun <T> newestFirst(oldestFirst: List<T>): List<T> = oldestFirst.asReversed()

/** The list of routes: the ones done most first, then the most recent. */
internal fun <T> mostDone(routes: List<T>, count: (T) -> Int): List<T> = routes.sortedByDescending(count)

/**
 * A route from an encoded polyline: precision 5 for activities, 6 for segments (the decoder reads 5, so
 * the values are scaled). Null when it has no line to draw.
 */
internal fun trackOf(encoded: String?, precision: Int = 5): Route? {
    if (encoded.isNullOrEmpty()) return null
    val scale = Math.pow(10.0, (5 - precision).toDouble())
    val points = decodePolyline(encoded)
    val route = Route(
        DoubleArray(points.size) { points[it].first * scale },
        DoubleArray(points.size) { points[it].second * scale },
        FloatArray(points.size)
    )
    return route.takeIf { it.hasGps }
}

/** "17 Nov 2026" in the phone's language; the text itself when it is not a day. */
internal fun shortDay(day: String, locale: java.util.Locale): String = runCatching {
    java.time.LocalDate.parse(day.take(10))
        .format(
            java.time.format.DateTimeFormatter.ofLocalizedDate(
                java.time.format.FormatStyle.MEDIUM
            ).withLocale(locale)
        )
}.getOrDefault(day)
