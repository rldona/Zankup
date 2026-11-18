package com.zankup.android.settings.more

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** The pure parts of Settings › AI, Privacy and Account, kept apart so they can be tested. */
internal object MoreLogic {
    // --- service keys ---------------------------------------------------------------------------

    /** The server never sends a key back: only its last characters, to recognise it. */
    fun lastChars(last4: String?): String = last4.orEmpty().takeLast(4)

    /** A key worth trying: the server rejects anything shorter anyway. */
    fun canSaveKey(value: String): Boolean = value.trim().length >= 8

    // --- hours ----------------------------------------------------------------------------------

    /** An hour kept in UTC, as the phone shows it ([offsetHours] from UTC: Madrid +1 or +2). */
    fun toLocal(utc: Int, offsetHours: Int): Int = Math.floorMod(utc + offsetHours, 24)

    fun toUtc(local: Int, offsetHours: Int): Int = Math.floorMod(local - offsetHours, 24)

    fun hourLabel(hour: Int): String = "%02d:00".format(Locale.ROOT, hour)

    // --- Garmin's sync interval -----------------------------------------------------------------

    val INTERVALS = listOf(0, 15, 30, 60, 180)

    /** The choices, with the current value added when it is none of them (set from the server). */
    fun intervalOptions(current: Int): List<Int> = if (current in INTERVALS) INTERVALS else INTERVALS + current

    /** "15 min", "1 h", "1.5 h"; null for 0 (off), which has its own words. */
    fun intervalLabel(minutes: Int): String? = when {
        minutes == 0 -> null
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60.0} h"
    }

    // --- the Coach's model and budget -----------------------------------------------------------

    /** "claude-haiku-4-5" as "Haiku 4.5". */
    fun shortModelName(id: String): String = id
        .replace("claude-", "")
        .replace(Regex("-(\\d+)-(\\d+)$"), " $1.$2")
        .replaceFirstChar { it.uppercase() }

    fun money(value: Double, locale: Locale = Locale.getDefault()): String = "%.2f".format(locale, value)

    /** What is typed in the budget field, with a decimal comma taken as a point. */
    fun budgetInput(text: String): String = text.replace(',', '.')

    // --- instance settings ----------------------------------------------------------------------

    fun text(value: JsonElement?): String? = (value as? JsonPrimitive)?.takeIf { value !is JsonNull }?.content

    fun number(value: JsonElement?): Double? = (value as? JsonPrimitive)?.let {
        it.doubleOrNull
            ?: it.content.toDoubleOrNull()
    }

    fun flag(value: JsonElement?): Boolean = (value as? JsonPrimitive)?.booleanOrNull ?: false

    // --- dates ----------------------------------------------------------------------------------

    /** "2026-08-14" as "14/08/2026". */
    fun toDayMonthYear(iso: String): String {
        val parts = iso.take(10).split('-')
        return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else iso
    }

    /** What is typed in a date field, with the slashes put in: "14082026" reads "14/08/2026". */
    fun typeDayMonthYear(text: String): String {
        val digits = text.filter(Char::isDigit).take(8)
        return listOf(digits.take(2), digits.drop(2).take(2), digits.drop(4)).filter {
            it.isNotEmpty()
        }.joinToString("/")
    }

    /** "14/08/2026" (or with - or .) as "2026-08-14", or null when it is no real date. */
    fun fromDayMonthYear(text: String): String? {
        val match = Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$").find(text.trim()) ?: return null
        val (d, m, y) = match.destructured
        val iso = "$y-${m.padStart(2, '0')}-${d.padStart(2, '0')}"
        return try {
            LocalDate.parse(iso).toString()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    // --- privacy zones --------------------------------------------------------------------------

    val RADII = listOf(200, 400, 800, 1000, 1600)

    /** "400 m", "1 km", "1.6 km". */
    fun radiusText(metres: Double): String = if (metres >= 1000) {
        val km = metres / 1000
        if (km % 1.0 == 0.0) "${km.toInt()} km" else "$km km"
    } else {
        "${metres.toInt()} m"
    }

    /** A circle as a closed ring of (lon, lat), to draw a zone on the map. */
    fun circle(lat: Double, lon: Double, radius: Double, steps: Int = 48): List<Pair<Double, Double>> {
        val dLat = radius / METRES_PER_DEGREE
        val dLon = radius / (METRES_PER_DEGREE * cos(lat * PI / 180))
        return (0..steps).map { i ->
            val a = i.toDouble() / steps * 2 * PI
            (lon + dLon * cos(a)) to (lat + dLat * sin(a))
        }
    }

    /** Where the map opens: the first zone, else where the latest route starts (lat, lon); null for the world. */
    fun centre(zones: List<Pair<Double, Double>>, routeStart: Pair<Double, Double>?): Pair<Double, Double>? =
        zones.firstOrNull() ?: routeStart

    /** A new zone's id, unique enough among a person's few zones. */
    fun zoneId(now: Long): String = "zone-$now"

    private const val METRES_PER_DEGREE = 111_320.0
}
