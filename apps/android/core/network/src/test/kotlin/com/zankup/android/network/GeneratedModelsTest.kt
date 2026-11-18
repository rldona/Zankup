package com.zankup.android.network

import com.zankup.android.api.model.ActivityPage
import com.zankup.android.api.model.ActivityStreams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The generated models read what the API really sends: decimals as Double (no BigDecimal, which needs a
 * serializer of its own), nulls inside a series (a GPS gap), and fields an older API leaves out.
 */
class GeneratedModelsTest {
    @Test
    fun readsAFeedPage() {
        val page =
            ZankupJson.decodeFromString(
                ActivityPage.serializer(),
                """
                {"items":[{"id":7,"name":null,"sport":"running","sub_sport":null,"source":"garmin",
                  "start_utc":"2026-11-18T07:30:00Z","timezone":"Europe/Madrid","duration_s":3005.4,
                  "moving_s":2990.0,"distance_m":10234.5,"ascent_m":88.0,"avg_hr":151,"avg_power":null,
                  "has_gps":true,"route":"abc","new_field":1}],
                 "next_cursor":"2026-11-17"}
                """.trimIndent()
            )
        val run = page.items.single()
        assertEquals(10234.5, run.distanceM!!, 0.0)
        assertNull(run.name)
        assertNull(run.avgPower)
        assertEquals("2026-11-17", page.nextCursor)
    }

    @Test
    fun readsSeriesWithGaps() {
        val streams =
            ZankupJson.decodeFromString(
                ActivityStreams.serializer(),
                """{"t":[0,1,2],"lat":[40.41,null,40.42],"lon":[-3.70,null,-3.71],"dist":[0,2.8,5.6],"hr":[120,null,122],"power":null}"""
            )
        assertEquals(listOf(40.41, null, 40.42), streams.lat)
        assertEquals(listOf(120, null, 122), streams.hr)
        assertNull(streams.power)
        assertNull(streams.speed)
    }
}
