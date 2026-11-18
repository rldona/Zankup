package com.zankup.android.settings.more

import java.util.Locale
import kotlin.math.abs
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreLogicTest {
    @Test
    fun keysShowOnlyTheirLastCharacters() {
        assertEquals("1234", MoreLogic.lastChars("abcd1234"))
        assertEquals("ab", MoreLogic.lastChars("ab"))
        assertEquals("", MoreLogic.lastChars(null))
        assertTrue(MoreLogic.canSaveKey("  12345678 "))
        assertFalse(MoreLogic.canSaveKey("1234567"))
    }

    @Test
    fun hoursGoBetweenUtcAndTheLocalTime() {
        assertEquals(8, MoreLogic.toLocal(7, 1))
        assertEquals(1, MoreLogic.toLocal(23, 2))
        assertEquals(23, MoreLogic.toUtc(1, 2))
        assertEquals(19, MoreLogic.toLocal(0, -5))
        for (offset in -11..14) {
            for (h in 0..23) assertEquals(h, MoreLogic.toUtc(MoreLogic.toLocal(h, offset), offset))
        }
        assertEquals("07:00", MoreLogic.hourLabel(7))
    }

    @Test
    fun intervalsKeepTheCurrentValueAndReadAsMinutesOrHours() {
        assertEquals(MoreLogic.INTERVALS, MoreLogic.intervalOptions(30))
        assertEquals(MoreLogic.INTERVALS + 45, MoreLogic.intervalOptions(45))
        assertNull(MoreLogic.intervalLabel(0))
        assertEquals("15 min", MoreLogic.intervalLabel(15))
        assertEquals("1 h", MoreLogic.intervalLabel(60))
        assertEquals("3 h", MoreLogic.intervalLabel(180))
        assertEquals("1.5 h", MoreLogic.intervalLabel(90))
    }

    @Test
    fun modelsAndMoney() {
        assertEquals("Haiku 4.5", MoreLogic.shortModelName("claude-haiku-4-5"))
        assertEquals("Opus 4.1", MoreLogic.shortModelName("claude-opus-4-1"))
        assertEquals("1.00", MoreLogic.money(1.0, Locale.ROOT))
        assertEquals("12.5", MoreLogic.budgetInput("12,5"))
    }

    @Test
    fun instanceValues() {
        assertEquals("x", MoreLogic.text(JsonPrimitive("x")))
        assertNull(MoreLogic.text(JsonNull))
        assertEquals(3.0, MoreLogic.number(JsonPrimitive(3))!!, 0.0)
        assertEquals(2.5, MoreLogic.number(JsonPrimitive("2.5"))!!, 0.0)
        assertTrue(MoreLogic.flag(JsonPrimitive(true)))
        assertFalse(MoreLogic.flag(null))
    }

    @Test
    fun dates() {
        assertEquals("14/08/2026", MoreLogic.toDayMonthYear("2026-08-14"))
        assertEquals("14/08/2026", MoreLogic.typeDayMonthYear("14082026"))
        assertEquals("14/0", MoreLogic.typeDayMonthYear("14/0"))
        assertEquals("2026-08-14", MoreLogic.fromDayMonthYear("14/8/2026"))
        assertEquals("2026-08-14", MoreLogic.fromDayMonthYear("14.08.2026"))
        assertNull(MoreLogic.fromDayMonthYear("31/02/2026"))
        assertNull(MoreLogic.fromDayMonthYear("2026-08-14"))
    }

    @Test
    fun radii() {
        assertEquals(listOf(200, 400, 800, 1000, 1600), MoreLogic.RADII)
        assertEquals("400 m", MoreLogic.radiusText(400.0))
        assertEquals("1 km", MoreLogic.radiusText(1000.0))
        assertEquals("1.6 km", MoreLogic.radiusText(1600.0))
    }

    @Test
    fun aZoneIsAClosedRingAtItsRadius() {
        val lat = 40.4168
        val lon = -3.7038
        val ring = MoreLogic.circle(lat, lon, 400.0)
        assertEquals(49, ring.size)
        assertEquals(ring.first().first, ring.last().first, 1e-9)
        assertEquals(ring.first().second, ring.last().second, 1e-9)
        // Every point about 400 m from the centre (equirectangular distance, fine at this size).
        ring.forEach { (pLon, pLat) ->
            val dy = (pLat - lat) * 111_320
            val dx = (pLon - lon) * 111_320 * kotlin.math.cos(Math.toRadians(lat))
            assertTrue(abs(kotlin.math.sqrt(dx * dx + dy * dy) - 400) < 1)
        }
    }

    @Test
    fun theMapOpensOnTheFirstZoneElseTheLatestRoute() {
        assertEquals(1.0 to 2.0, MoreLogic.centre(listOf(1.0 to 2.0, 3.0 to 4.0), 5.0 to 6.0))
        assertEquals(5.0 to 6.0, MoreLogic.centre(emptyList(), 5.0 to 6.0))
        assertNull(MoreLogic.centre(emptyList(), null))
    }
}
