package com.zankup.android.tools

import com.zankup.android.api.model.GearOut
import com.zankup.android.api.model.SyncProgress
import com.zankup.android.api.model.SyncStatus
import com.zankup.android.domain.UnitFormat
import com.zankup.android.domain.Units
import com.zankup.android.domain.formatDuration
import com.zankup.android.domain.formatNumber
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal const val METRES_PER_MILE = 1609.344

// ---- Gear ----

/** The habitual ones first, then the rest in use, then the retired (as the React Native app). */
internal fun sortGear(list: List<GearOut>): List<GearOut> = list.filter { it.habitual && !it.retired } +
    list.filter { !it.habitual && !it.retired } +
    list.filter { it.retired }

/** "Brand Model", without the brand twice when the model already starts with it. */
internal fun gearModel(brand: String?, model: String?): String {
    val b = brand.orEmpty()
    val m = model.orEmpty()
    return if (m.lowercase().startsWith(b.lowercase())) m else "$b $m".trim()
}

/** How much of its limit the gear has done, 0 to 1; null without a limit. */
internal fun mileageShare(distanceM: Double, limitM: Double?): Float? {
    if (limitM == null || limitM <= 0) return null
    return (distanceM / limitM).coerceIn(0.0, 1.0).toFloat()
}

/** Metres in the person's distance unit, whole, as the form shows them ("800"). */
internal fun metresToField(metres: Double, units: Units): String {
    val perUnit = if (units == Units.IMPERIAL) METRES_PER_MILE else 1000.0
    return (metres / perUnit).roundToLong().toString()
}

/** What was typed in km or miles ("800", "12,5") as metres; null when it is not a number ≥ 0. */
internal fun fieldToMetres(text: String, units: Units): Double? {
    val value = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
    val perUnit = if (units == Units.IMPERIAL) METRES_PER_MILE else 1000.0
    return value * perUnit
}

// ---- Import ----

/** One part of an upload: its index, where it starts and how long it is. */
internal data class Part(val index: Int, val offset: Long, val length: Int)

/** How many parts a file of [size] bytes takes, at least one. */
internal fun partCount(size: Long, partSize: Int): Int = max(1, ceil(size.toDouble() / partSize).toInt())

/** The parts a file of [size] bytes is sent in, [partSize] bytes each (the last one shorter). */
internal fun parts(size: Long, partSize: Int): List<Part> = (0 until partCount(size, partSize)).map { index ->
    val offset = index.toLong() * partSize
    Part(index, offset, max(0L, minOf(partSize.toLong(), size - offset)).toInt())
}

/**
 * Where a part goes: the API gives an absolute URL, or a path when `API_PUBLIC_URL` is unset, then
 * `/{index}?token=…`.
 */
internal fun partUrl(base: String, instance: String, index: Int, token: String): String {
    val root = if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(base)) {
        base
    } else {
        instance.trimEnd('/') + base
    }
    return "$root/$index?token=${URLEncoder.encode(token, "UTF-8")}"
}

/** "12.5 MB": bytes in megabytes, up to one decimal. */
internal fun megabytes(bytes: Long, locale: Locale): String =
    "${formatNumber(bytes / 1024.0 / 1024.0, locale, 0, 1)} MB"

/** An import still being worked on, so its list is polled. */
internal fun importActive(status: String) = status == "queued" || status == "running"

// ---- Sync ----

internal fun runActive(run: SyncStatus?) =
    run?.status == SyncStatus.Status.QUEUED || run?.status == SyncStatus.Status.RUNNING

/** The window being listed and how many there are, when a long run goes window by window. */
internal fun runWindows(run: SyncStatus): Pair<Int, Int>? {
    val p: SyncProgress = run.progress
    val window = p.window ?: return null
    val windows = p.windows ?: return null
    if (!runActive(run) || windows <= 1) return null
    return window.coerceIn(0, windows) to windows
}

/** Whether the latest run has just ended: it was queued or running, and now it is not. */
internal fun runEnded(before: SyncStatus.Status?, now: SyncStatus?): Boolean {
    val wasActive = before == SyncStatus.Status.QUEUED || before == SyncStatus.Status.RUNNING
    return wasActive && now != null && !runActive(now)
}

// ---- Events ----

/** Best efforts at mile distances: with metric units their records are left out. */
internal val MILE_EFFORTS = setOf("half_mile", "1mile", "2mile", "10mile")

/** What an event says, before it is put into words. */
internal sealed interface EventWording {
    data class Record(val distanceKey: String, val time: String) : EventWording

    data class EstimateUp(val kind: String, val value: String) : EventWording

    data class Ramp(val ramp: String) : EventWording

    data class Unknown(val kind: String) : EventWording
}

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

private fun JsonElement?.number(): Double? = (this as? JsonPrimitive)?.doubleOrNull

/** Whether an event is shown with these units: a record at a mile distance is not, with km. */
internal fun eventShown(kind: String, payload: Map<String, JsonElement>, units: Units): Boolean =
    kind != "record" || units == Units.IMPERIAL || payload["distance_key"].text() !in MILE_EFFORTS

/** The activity an event is about, when it has one. */
internal fun eventActivity(payload: Map<String, JsonElement>): Long? =
    (payload["activity_id"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

/** An event as the notification words it, its figures already written in [unit]. */
internal fun eventWording(kind: String, payload: Map<String, JsonElement>, unit: UnitFormat): EventWording =
    when (kind) {
        "record" -> EventWording.Record(
            payload["distance_key"].text().orEmpty(),
            formatDuration(payload["time_s"].number() ?: 0.0)
        )
        "estimate_up" -> {
            val estimate = payload["kind"].text().orEmpty()
            EventWording.EstimateUp(estimate, estimateValue(estimate, payload["value"].number() ?: 0.0, unit))
        }
        "ramp" -> EventWording.Ramp(payload["ramp"].text().orEmpty())
        else -> EventWording.Unknown(kind)
    }

/** An estimate's value with its unit: "250 W", "4:30 /km", "52.3", "1:45:00". */
internal fun estimateValue(kind: String, value: Double, unit: UnitFormat): String = when {
    kind == "ftp" -> "${value.roundToLong()} W"
    kind == "critical_speed" -> unit.pace(value)
    kind == "lthr" || kind == "hr_max" -> "${value.roundToLong()} bpm"
    kind.startsWith("race_") -> formatDuration(value)
    else -> formatNumber(value, unit.locale, 1, 1)
}

/** An API date-time ("2026-11-18T07:30:00Z", with an offset, or without one as UTC). */
internal fun parseInstant(text: String?): Instant? {
    if (text == null) return null
    return runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(text) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC) }.getOrNull()
        ?: runCatching { LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull()
}
