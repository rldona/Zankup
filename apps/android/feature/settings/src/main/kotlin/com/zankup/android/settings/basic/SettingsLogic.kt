package com.zankup.android.settings.basic

import com.zankup.android.api.model.EstimateOut
import com.zankup.android.api.model.ExportOut
import com.zankup.android.api.model.Notifications
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// --- numbers typed in a field ---

/** "160" → 160, "" → null; anything that is not a whole number is null too. */
fun numberOrNull(text: String): Int? = text.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()

/** Only the digits of what was typed, at most [max] of them. */
fun digitsOnly(text: String, max: Int = 4): String = text.filter(Char::isDigit).take(max)

// --- heart rate and zones ---

/** The engine's estimate of [kind] (lthr, ftp…), rounded, if there is one. */
fun estimateOf(estimates: List<EstimateOut>?, kind: String): Int? =
    estimates?.firstOrNull { it.kind == kind }?.value?.let { Math.round(it).toInt() }

/** What a threshold field saves when it loses focus: null when nothing changed, else the new value. */
fun thresholdChange(text: String, saved: Int?): Change? {
    val next = numberOrNull(text)
    return if (next == saved) null else Change(next)
}

/** A value to save, null meaning "clear it". */
data class Change(val value: Int?)

// --- language ---

/** The app's language: the phone's, or one picked. [tag] is what the API keeps (`language`). */
enum class LanguageChoice(val tag: String?) {
    AUTO(null),
    EN("en"),
    ES("es")
    ;

    companion object {
        fun fromTag(tag: String?): LanguageChoice = entries.firstOrNull { it.tag != null && it.tag == tag } ?: AUTO
    }
}

/**
 * Whether to say "restart to apply": only below Android 13, where the app cannot switch its own language
 * at once, and only when the one picked is not the one showing.
 */
fun needsRestart(choice: LanguageChoice, showing: String, sdk: Int): Boolean =
    sdk < 33 && choice != LanguageChoice.AUTO && choice.tag != showing

// --- hours kept in UTC, shown in the phone's time ---

/** The phone's offset from UTC now, in whole hours (Madrid: +1 in winter, +2 in summer). */
fun offsetHours(zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): Int =
    zone.rules.getOffset(now).totalSeconds / 3600

fun utcToLocalHour(utc: Int, offset: Int): Int = Math.floorMod(utc + offset, 24)

fun localToUtcHour(local: Int, offset: Int): Int = Math.floorMod(local - offset, 24)

fun hourLabel(hour: Int): String = "%02d:00".format(Locale.ROOT, hour)

// --- dates typed as DD/MM/YYYY ---

/** Formats the digits as they are typed: "1408" → "14/08", "14081982" → "14/08/1982". */
fun typeDayMonthYear(text: String): String {
    val d = text.filter(Char::isDigit).take(8)
    return buildString {
        d.forEachIndexed { i, c ->
            if (i == 2 || i == 4) append('/')
            append(c)
        }
    }
}

/** "14/08/1982" → "1982-08-14"; null when it is not a real day. */
fun fromDayMonthYear(text: String): String? {
    val parts = text.split('/')
    if (parts.size != 3 || parts[2].length != 4) return null
    val (day, month, year) = parts.map { it.toIntOrNull() ?: return null }
    return runCatching { LocalDate.of(year, month, day).toString() }.getOrNull()
}

/** "1982-08-14" → "14/08/1982"; what it got when it is not a date. */
fun toDayMonthYear(iso: String): String = runCatching {
    LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrDefault(iso)

/** An API date or moment as a medium date in [locale] ("18 Nov 2026"); what it got when unreadable. */
fun mediumDate(iso: String, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    return runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(format) }
        .recoverCatching { Instant.parse(iso).atZone(zone).format(format) }
        .recoverCatching { LocalDate.parse(iso.take(10)).format(format) }
        .getOrDefault(iso)
}

// --- export ---

/** Bytes as megabytes with at most one decimal, in [locale]: 1_572_864 → "1.5 MB". */
fun megabytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
    val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    return "${format.format(bytes / 1_048_576.0)} MB"
}

/** Whether an export is still being prepared (then the list is asked again every few seconds). */
val ExportOut.busy get() = status == "queued" || status == "running"

// --- notifications ---

/** The kinds Settings lists, in its order (`health_reauth` is kept but not shown, as in the app). */
val NOTIFICATION_KINDS = listOf("activity", "record", "sync_error", "garmin_key", "estimate", "import", "weekly")

fun Notifications.isOn(kind: String): Boolean = when (kind) {
    "activity" -> activity
    "record" -> record
    "sync_error" -> syncError
    "garmin_key" -> garminKey
    "estimate" -> estimate
    "import" -> `import`
    "weekly" -> weekly
    "health_reauth" -> healthReauth
    else -> null
} ?: (kind != "estimate")

fun Notifications.with(kind: String, on: Boolean): Notifications = when (kind) {
    "activity" -> copy(activity = on)
    "record" -> copy(record = on)
    "sync_error" -> copy(syncError = on)
    "garmin_key" -> copy(garminKey = on)
    "estimate" -> copy(estimate = on)
    "import" -> copy(`import` = on)
    "weekly" -> copy(weekly = on)
    "health_reauth" -> copy(healthReauth = on)
    else -> this
}

/** Every kind spelled out, so a default value is sent too (the JSON drops defaults). */
fun Notifications.toJson(): JsonObject =
    JsonObject((NOTIFICATION_KINDS + "health_reauth").associateWith { JsonPrimitive(isOn(it)) })
