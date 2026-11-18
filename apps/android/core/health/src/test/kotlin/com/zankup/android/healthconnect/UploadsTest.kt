package com.zankup.android.healthconnect

import com.zankup.android.api.model.StageIn
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadsTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private fun at(s: String) = Instant.parse(s)

    @Test
    fun nightKeepsKnownStagesOnly() {
        val night =
            Night(
                "n1",
                "com.garmin",
                at("2026-11-17T22:30:00Z"),
                at("2026-11-18T06:00:00Z"),
                ZoneOffset.ofHours(1),
                listOf(
                    Triple(at("2026-11-17T22:30:00Z"), at("2026-11-17T23:00:00Z"), 2),
                    Triple(at("2026-11-17T23:00:00Z"), at("2026-11-17T23:30:00Z"), 0),
                    Triple(at("2026-11-17T23:30:00Z"), at("2026-11-18T00:30:00Z"), 5),
                    Triple(at("2026-11-18T00:30:00Z"), at("2026-11-18T01:00:00Z"), 7)
                )
            )
        val up = sleepUpload(night)!!
        assertEquals("n1", up.id)
        assertEquals(3600, up.utcOffsetS)
        assertEquals("2026-11-17T22:30:00Z", up.start)
        assertEquals(
            listOf(StageIn.Stage.LIGHT, StageIn.Stage.DEEP, StageIn.Stage.AWAKE),
            up.stages!!.map { it.stage }
        )
        assertNull(sleepUpload(night.copy(id = null)))
    }

    @Test
    fun overnightReadingsCountForTheMorningAfter() {
        val late = Sample(at("2026-11-17T22:30:00Z"), ZoneOffset.ofHours(1), 50.0) // 23:30 local
        assertEquals("2026-11-18", localDay(late, overnight = true, zone = madrid))
        assertEquals("2026-11-17", localDay(late, overnight = false, zone = madrid))
        // Without an offset, the phone's zone.
        assertEquals("2026-11-18", localDay(late.copy(offset = null), overnight = true, zone = madrid))
    }

    @Test
    fun averagesPerDayMergedWithSteps() {
        val rows =
            dailyAverages(
                mapOf(
                    Field.HRV to
                        listOf(
                            Sample(at("2026-11-17T23:00:00Z"), ZoneOffset.UTC, 50.0),
                            Sample(at("2026-11-18T03:00:00Z"), ZoneOffset.UTC, 55.0),
                            Sample(at("2026-11-18T04:00:00Z"), ZoneOffset.UTC, 0.0)
                        ),
                    Field.RESTING_HR to listOf(Sample(at("2026-11-18T12:00:00Z"), ZoneOffset.UTC, 49.0))
                ),
                steps = mapOf("2026-11-18" to 8123L, "2026-11-19" to 0L),
                zone = ZoneId.of("UTC")
            )
        assertEquals(1, rows.size)
        val day = rows.single()
        assertEquals("2026-11-18", day.day)
        assertEquals(52.5, day.hrvMs!!, 1e-9)
        assertEquals(49.0, day.restingHr!!, 1e-9)
        assertEquals(8123, day.steps)
    }
}
