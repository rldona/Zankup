package com.zankup.android.feed

import com.zankup.android.domain.Sport
import com.zankup.android.domain.Units
import java.time.LocalDate
import java.time.ZoneId

/** Which activities by date: every one, this calendar year, or the last twelve months up to today. */
enum class Period { ALL, YEAR, LAST_12 }

/** What the results are sorted by, with the API's key for it. */
enum class SortBy(val key: String) {
    DATE("date"),
    DISTANCE("distance"),
    DURATION("duration"),
    ELEVATION("elevation"),
    SPEED("speed")
}

enum class Order(val key: String) {
    DESC("desc"),
    ASC("asc")
}

/**
 * The filters sheet as the person filled it in. The figures are kept as typed — distance in km or mi,
 * time in minutes, ascent in m or ft — and turned into the API's metres and seconds by [searchParams].
 */
data class Filters(
    val period: Period = Period.ALL,
    val distanceMin: String = "",
    val distanceMax: String = "",
    val durationMin: String = "",
    val durationMax: String = "",
    val ascentMin: String = "",
    val withRecord: Boolean = false,
    val withDescription: Boolean = false,
    val withGps: Boolean = false,
    val sort: SortBy = SortBy.DATE,
    val order: Order = Order.DESC
) {
    /** How many filters are on; a sort other than newest first counts as one. */
    val active: Int
        get() = listOf(
            period != Period.ALL,
            parseNumber(distanceMin) != null,
            parseNumber(distanceMax) != null,
            parseNumber(durationMin) != null,
            parseNumber(durationMax) != null,
            parseNumber(ascentMin) != null,
            withRecord,
            withDescription,
            withGps,
            sort != SortBy.DATE || order != Order.DESC
        ).count { it }

    val on: Boolean get() = active > 0
}

/** What the screen shows: the feed when nothing is typed or chosen, otherwise the search's results. */
data class SearchRequest(
    val query: String = "",
    val sport: Sport? = null,
    val filters: Filters = Filters(),
    val units: Units = Units.METRIC
) {
    val searching: Boolean get() = query.isNotEmpty() || sport != null || filters.on
}

/** The search endpoint's parameters, in metres, seconds and ISO instants; null ones are not sent. */
data class SearchParams(
    val q: String? = null,
    val sport: List<String>? = null,
    val from: String? = null,
    val minDistance: Double? = null,
    val maxDistance: Double? = null,
    val minDuration: Double? = null,
    val maxDuration: Double? = null,
    val minAscent: Double? = null,
    val withRecord: Boolean? = null,
    val withDescription: Boolean? = null,
    val withGps: Boolean? = null,
    val sort: String? = null,
    val order: String? = null
)

private const val METRES_PER_KM = 1000.0
private const val METRES_PER_MILE = 1609.344
private const val METRES_PER_FOOT = 0.3048
private const val SECONDS_PER_MINUTE = 60.0

/** A figure as typed, with a comma or a point; null when empty, not a number or negative. */
fun parseNumber(text: String): Double? = text
    .trim()
    .replace(',', '.')
    .toDoubleOrNull()
    ?.takeIf { it.isFinite() && it >= 0 }

/** A distance typed in km or mi, in metres. */
fun distanceMetres(text: String, units: Units): Double? {
    val perUnit = if (units == Units.METRIC) METRES_PER_KM else METRES_PER_MILE
    return parseNumber(text)?.let { it * perUnit }
}

/** An ascent typed in m or ft, in metres. */
fun ascentMetres(text: String, units: Units): Double? {
    val perUnit = if (units == Units.METRIC) 1.0 else METRES_PER_FOOT
    return parseNumber(text)?.let { it * perUnit }
}

/** A time typed in minutes, in seconds. */
fun durationSeconds(text: String): Double? = parseNumber(text)?.let { it * SECONDS_PER_MINUTE }

/** "m" or "ft", the unit the minimum ascent is typed in. */
val Units.elevationUnit: String get() = if (this == Units.METRIC) "m" else "ft"

/** The start of [period] as an ISO instant, at midnight in [zone]; null for every activity. */
fun periodStart(period: Period, today: LocalDate, zone: ZoneId): String? = when (period) {
    Period.ALL -> null
    Period.YEAR -> today.withDayOfYear(1)
    Period.LAST_12 -> today.minusYears(1)
}?.atStartOfDay(zone)?.toInstant()?.toString()

/** The request as the search endpoint's parameters; the defaults (newest first, switches off) are left out. */
fun searchParams(request: SearchRequest, today: LocalDate, zone: ZoneId): SearchParams {
    val f = request.filters
    val units = request.units
    return SearchParams(
        q = request.query.trim().ifEmpty { null },
        sport = request.sport?.let { listOf(it.key) },
        from = periodStart(f.period, today, zone),
        minDistance = distanceMetres(f.distanceMin, units),
        maxDistance = distanceMetres(f.distanceMax, units),
        minDuration = durationSeconds(f.durationMin),
        maxDuration = durationSeconds(f.durationMax),
        minAscent = ascentMetres(f.ascentMin, units),
        withRecord = f.withRecord.takeIf { it },
        withDescription = f.withDescription.takeIf { it },
        withGps = f.withGps.takeIf { it },
        sort = f.sort.takeIf { it != SortBy.DATE }?.key,
        order = f.order.takeIf { it != Order.DESC }?.key
    )
}
