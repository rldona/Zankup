package com.zankup.android.settings.basic

import com.zankup.android.api.model.EstimateOut
import com.zankup.android.api.model.ExportOut
import com.zankup.android.api.model.Notifications
import com.zankup.android.network.ZankupJson
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLogicTest {
    private fun estimate(kind: String, value: Double) =
        EstimateOut(kind, value, "m", "2026-11-01", emptyList(), emptyList())

    @Test
    fun numbers() {
        assertEquals(160, numberOrNull(" 160 "))
        assertNull(numberOrNull(""))
        assertNull(numberOrNull("abc"))
        assertEquals("1601", digitsOnly("16a0-1x9"))
    }

    @Test
    fun estimatesArePlaceholders() {
        val list = listOf(estimate("lthr", 171.6), estimate("ftp", 249.4))
        assertEquals(172, estimateOf(list, "lthr"))
        assertEquals(249, estimateOf(list, "ftp"))
        assertNull(estimateOf(list, "vdot"))
        assertNull(estimateOf(null, "lthr"))
    }

    @Test
    fun thresholdsSaveOnlyWhatChanged() {
        assertNull(thresholdChange("170", 170))
        assertNull(thresholdChange("", null))
        assertEquals(Change(175), thresholdChange("175", 170))
        assertEquals(Change(null), thresholdChange("", 170))
    }

    @Test
    fun language() {
        assertEquals(LanguageChoice.ES, LanguageChoice.fromTag("es"))
        assertEquals(LanguageChoice.AUTO, LanguageChoice.fromTag(null))
        assertEquals(LanguageChoice.AUTO, LanguageChoice.fromTag("fr"))
        assertTrue(needsRestart(LanguageChoice.ES, "en", 31))
        assertFalse(needsRestart(LanguageChoice.ES, "es", 31))
        assertFalse(needsRestart(LanguageChoice.AUTO, "en", 31))
        assertFalse(needsRestart(LanguageChoice.ES, "en", 33))
    }

    @Test
    fun hoursInThePhonesTime() {
        val madrid = ZoneId.of("Europe/Madrid")
        assertEquals(1, offsetHours(madrid, Instant.parse("2026-01-15T12:00:00Z")))
        assertEquals(2, offsetHours(madrid, Instant.parse("2026-07-15T12:00:00Z")))
        assertEquals(0, utcToLocalHour(23, 1))
        assertEquals(23, localToUtcHour(0, 1))
        assertEquals(20, utcToLocalHour(1, -5))
        assertEquals(1, localToUtcHour(20, -5))
        for (h in 0..23) assertEquals(h, localToUtcHour(utcToLocalHour(h, 2), 2))
        assertEquals("07:00", hourLabel(7))
    }

    @Test
    fun datesTyped() {
        assertEquals("14", typeDayMonthYear("14"))
        assertEquals("14/08", typeDayMonthYear("1408"))
        assertEquals("14/08/1982", typeDayMonthYear("14/08/19825"))
        assertEquals("1982-08-14", fromDayMonthYear("14/08/1982"))
        assertNull(fromDayMonthYear("31/02/2026"))
        assertNull(fromDayMonthYear("14/08/82"))
        assertEquals("14/08/1982", toDayMonthYear("1982-08-14"))
        assertEquals("soon", toDayMonthYear("soon"))
    }

    @Test
    fun exportSizeAndState() {
        assertEquals("1.5 MB", megabytes(1_572_864, Locale.ENGLISH))
        assertEquals("1,5 MB", megabytes(1_572_864, Locale.forLanguageTag("es")))
        assertEquals("0 MB", megabytes(0, Locale.ENGLISH))
        assertEquals("12 MB", megabytes(12L * 1_048_576, Locale.ENGLISH))
        fun export(status: String) = ExportOut(1, status, null, null, "2026-11-18T10:00:00Z", null, null, null)
        assertTrue(export("queued").busy)
        assertTrue(export("running").busy)
        assertFalse(export("done").busy)
        assertEquals("Nov 25, 2026", mediumDate("2026-11-25T10:00:00+00:00", Locale.US, ZoneId.of("UTC")))
        assertEquals("never", mediumDate("never", Locale.US))
    }

    @Test
    fun notificationKindsAreAllSent() {
        val prefs = Notifications().with("estimate", true).with("activity", false)
        assertTrue(prefs.isOn("estimate"))
        assertFalse(prefs.isOn("activity"))
        assertTrue(Notifications().isOn("record"))
        assertFalse(Notifications(estimate = null).isOn("estimate"))
        val json = prefs.toJson()
        assertEquals(8, json.size)
        assertFalse(json.getValue("activity").jsonPrimitive.boolean)
        assertTrue(json.getValue("weekly").jsonPrimitive.boolean)
    }

    @Test
    fun nullsReachTheApi() {
        // Clearing a value needs the null in the body, which the app's JSON drops from classes.
        val body = JsonObject(mapOf("hr_max" to JsonNull, "hr_rest" to JsonPrimitive(50)))
        assertEquals("""{"hr_max":null,"hr_rest":50}""", ZankupJson.encodeToString(JsonObject.serializer(), body))
        val map: Map<String, JsonElement> = mapOf("alert_email" to JsonNull)
        assertEquals(
            """{"alert_email":null}""",
            ZankupJson.encodeToString(MapSerializer(String.serializer(), JsonElement.serializer()), map)
        )
    }
}
