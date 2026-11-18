package com.zankup.android.tools

import com.zankup.android.api.model.GearOut
import com.zankup.android.api.model.SyncProgress
import com.zankup.android.api.model.SyncStatus
import com.zankup.android.domain.UnitFormat
import com.zankup.android.domain.Units
import java.util.Locale
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsLogicTest {
    private val metric = UnitFormat(Units.METRIC, Locale.ENGLISH)

    private fun gear(id: Int, habitual: Boolean = false, retired: Boolean = false) = GearOut(
        id = id, kind = "shoe", name = "g$id", brand = null, model = null, sports = emptyList(), initialM = 0.0,
        limitM = null, retired = retired, habitual = habitual, distanceM = 0.0, activities = 0, movingS = 0.0,
        firstUsed = null, lastUsed = null, overLimit = false
    )

    private fun run(status: SyncStatus.Status, window: Int? = null, windows: Int? = null) = SyncStatus(
        id = 1, trigger = SyncStatus.Trigger.BUTTON, status = status, startedAt = "2026-11-18T07:00:00Z",
        finishedAt = null, newCount = 0, progress = SyncProgress(window = window, windows = windows),
        pendingImports = 0, error = null
    )

    // ---- Parts ----

    @Test
    fun `a file is split in parts of the size the API asks, the last one shorter`() {
        val split = parts(25, 10)
        assertEquals(listOf(Part(0, 0, 10), Part(1, 10, 10), Part(2, 20, 5)), split)
        assertEquals(3, partCount(25, 10))
        assertEquals(2, partCount(20, 10))
    }

    @Test
    fun `an empty file still takes one part`() {
        assertEquals(1, partCount(0, 10))
        assertEquals(listOf(Part(0, 0, 0)), parts(0, 10))
    }

    @Test
    fun `a part goes to the absolute URL, or to the instance plus the path`() {
        assertEquals(
            "https://up.example.com/api/direct/uploads/7/2?token=a%2Bb",
            partUrl("https://up.example.com/api/direct/uploads/7", "https://api.zankup.com/", 2, "a+b")
        )
        assertEquals(
            "https://api.zankup.com/api/direct/uploads/7/0?token=t",
            partUrl("/api/direct/uploads/7", "https://api.zankup.com/", 0, "t")
        )
    }

    @Test
    fun `megabytes keep one decimal`() {
        assertEquals("1.5 MB", megabytes(1_572_864, Locale.ENGLISH))
        assertEquals("0 MB", megabytes(0, Locale.ENGLISH))
    }

    // ---- Gear ----

    @Test
    fun `habitual gear comes first and the retired last`() {
        val sorted = sortGear(listOf(gear(1, retired = true), gear(2), gear(3, habitual = true)))
        assertEquals(listOf(3, 2, 1), sorted.map { it.id })
    }

    @Test
    fun `mileage is a share of the limit, capped at the whole bar`() {
        assertEquals(0.5f, mileageShare(400_000.0, 800_000.0)!!, 0.0001f)
        assertEquals(1f, mileageShare(900_000.0, 800_000.0)!!, 0.0001f)
        assertNull(mileageShare(100.0, null))
        assertNull(mileageShare(100.0, 0.0))
    }

    @Test
    fun `the form's distances are in the person's unit`() {
        assertEquals("800", metresToField(800_000.0, Units.METRIC))
        assertEquals("497", metresToField(800_000.0, Units.IMPERIAL))
        assertEquals(12_500.0, fieldToMetres("12,5", Units.METRIC)!!, 0.001)
        assertEquals(1609.344, fieldToMetres("1", Units.IMPERIAL)!!, 0.001)
        assertNull(fieldToMetres("abc", Units.METRIC))
        assertNull(fieldToMetres("-1", Units.METRIC))
    }

    @Test
    fun `the brand is not written twice`() {
        assertEquals("Nike Pegasus", gearModel("Nike", "Pegasus"))
        assertEquals("Nike Pegasus 40", gearModel("Nike", "Nike Pegasus 40"))
        assertEquals("Canyon", gearModel("Canyon", null))
    }

    // ---- Sync ----

    @Test
    fun `the windows show only while a long run goes on`() {
        assertEquals(3 to 10, runWindows(run(SyncStatus.Status.RUNNING, 3, 10)))
        assertNull(runWindows(run(SyncStatus.Status.RUNNING, 1, 1)))
        assertNull(runWindows(run(SyncStatus.Status.SUCCESS, 10, 10)))
        assertNull(runWindows(run(SyncStatus.Status.QUEUED)))
    }

    @Test
    fun `a run ends when it was active and is no longer`() {
        assertTrue(runEnded(SyncStatus.Status.RUNNING, run(SyncStatus.Status.SUCCESS)))
        assertTrue(runEnded(SyncStatus.Status.QUEUED, run(SyncStatus.Status.ERROR)))
        assertFalse(runEnded(SyncStatus.Status.RUNNING, run(SyncStatus.Status.RUNNING)))
        assertFalse(runEnded(null, run(SyncStatus.Status.SUCCESS)))
        assertFalse(runEnded(SyncStatus.Status.SUCCESS, run(SyncStatus.Status.SUCCESS)))
        assertTrue(runActive(run(SyncStatus.Status.QUEUED)))
        assertFalse(runActive(null))
    }

    // ---- Events ----

    private fun payload(vararg pairs: Pair<String, Any>): Map<String, JsonElement> = pairs.associate { (k, v) ->
        k to when (v) {
            is Number -> JsonPrimitive(v)
            else -> JsonPrimitive(v.toString())
        }
    }

    @Test
    fun `a record is worded with its distance and time`() {
        val w = eventWording("record", payload("distance_key" to "5k", "time_s" to 1234.0), metric)
        assertEquals(EventWording.Record("5k", "20:34"), w)
    }

    @Test
    fun `an estimate going up carries its value with its unit`() {
        assertEquals(
            EventWording.EstimateUp("ftp", "251 W"),
            eventWording("estimate_up", payload("kind" to "ftp", "value" to 250.6), metric)
        )
        assertEquals(
            EventWording.EstimateUp("critical_speed", "4:00 /km"),
            eventWording("estimate_up", payload("kind" to "critical_speed", "value" to 1000.0 / 240), metric)
        )
        assertEquals(
            EventWording.EstimateUp("vdot", "52.3"),
            eventWording("estimate_up", payload("kind" to "vdot", "value" to 52.34), metric)
        )
    }

    @Test
    fun `a ramp and an unknown event`() {
        assertEquals(EventWording.Ramp("7"), eventWording("ramp", payload("ramp" to 7), metric))
        assertEquals(EventWording.Unknown("new_thing"), eventWording("new_thing", emptyMap(), metric))
    }

    @Test
    fun `records at mile distances are left out with metric units`() {
        val mile = payload("distance_key" to "1mile")
        assertFalse(eventShown("record", mile, Units.METRIC))
        assertTrue(eventShown("record", mile, Units.IMPERIAL))
        assertTrue(eventShown("record", payload("distance_key" to "5k"), Units.METRIC))
        assertTrue(eventShown("ramp", emptyMap(), Units.METRIC))
    }

    @Test
    fun `an event opens its activity when it names one`() {
        assertEquals(42L, eventActivity(payload("activity_id" to 42)))
        assertNull(eventActivity(payload("activity_id" to "42")))
        assertNull(eventActivity(emptyMap()))
    }

    @Test
    fun `API dates are read with or without an offset`() {
        assertEquals(0L, parseInstant("1970-01-01T00:00:00Z")!!.epochSecond)
        assertEquals(0L, parseInstant("1970-01-01T01:00:00+01:00")!!.epochSecond)
        assertEquals(0L, parseInstant("1970-01-01T00:00:00")!!.epochSecond)
        assertNull(parseInstant("nope"))
    }
}
