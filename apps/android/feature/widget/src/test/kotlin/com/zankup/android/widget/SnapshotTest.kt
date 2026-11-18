package com.zankup.android.widget

import com.zankup.android.api.model.ActivitySummary
import com.zankup.android.api.model.FitnessDay
import com.zankup.android.network.ZankupJson
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnapshotTest {
    private val run =
        ZankupJson.decodeFromString(
            ActivitySummary.serializer(),
            """{"id":7,"name":"Morning run","sport":"run","sub_sport":null,"source":"garmin",
              "start_utc":"2026-11-18T07:30:00Z","timezone":"Europe/Madrid","duration_s":3100,
              "moving_s":3000,"distance_m":10000,"ascent_m":50,"avg_hr":150,"avg_power":null,
              "has_gps":true,"route":null}"""
        )
    private fun form(tsb: Double) = ZankupJson.decodeFromString(
        FitnessDay.serializer(),
        """{"day":"2026-11-18","load":0,"ctl":50,"atl":45,"tsb":$tsb}"""
    )

    @Test
    fun latestActivityAndSignedForm() {
        val s = snapshotOf(run, form(4.6), Locale.UK)
        assertEquals(7L, s.activityId)
        assertEquals("Morning run", s.title)
        assertEquals("+5", s.form)
        assertEquals(3, s.figures.split(" · ").size)
        assertEquals("-3", snapshotOf(run, form(-3.2), Locale.UK).form)
    }

    @Test
    fun nothingYet() {
        val s = snapshotOf(null, null, Locale.UK)
        assertNull(s.activityId)
        assertEquals("", s.form)
        assertEquals("", s.figures)
    }
}
