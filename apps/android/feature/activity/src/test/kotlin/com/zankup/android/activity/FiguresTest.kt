package com.zankup.android.activity

import com.zankup.android.api.model.ActivityDetail
import com.zankup.android.domain.Units
import com.zankup.android.network.ZankupJson
import com.zankup.android.ui.Formats
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FiguresTest {
    private fun activity(extra: String) = ZankupJson.decodeFromString(
        ActivityDetail.serializer(),
        """
            {"id":1,"sport":"run","source":"garmin","start_utc":"2026-11-17T07:30:00Z","duration_s":3700,
             "has_gps":true,"photo_list":[],"edited_fields":[],"has_fit":true,"imported_at":"2026-11-17T09:00:00Z",
             "laps":[],"splits":[],"best_efforts":[],"sets":[],"segments":[],$extra}
        """.trimIndent()
    )

    private val f = Formats(Units.METRIC, Locale.UK)

    @Test
    fun aRunLeadsWithDistanceTimeAndPace() {
        val run =
            activity(""""distance_m":10000,"moving_s":3000,"ascent_m":80,"avg_hr":150,"max_hr":172,"calories":700""")
        val (main, more) = figures(run, pace = true, f)
        assertEquals(
            listOf(
                R.string.fig_distance,
                R.string.fig_moving,
                R.string.fig_pace,
                R.string.fig_ascent,
                R.string.fig_hr,
                R.string.fig_calories
            ),
            main.map { it.label }
        )
        assertEquals("5:00 /km", main[2].value)
        // Stopped more than a minute: the elapsed time is behind "More".
        assertEquals(listOf(R.string.fig_elapsed, R.string.fig_max_hr), more.map { it.label })
    }

    @Test
    fun withoutDistanceTheNextFiguresFillTheSixPlaces() {
        val strength =
            activity(
                """"sport":"strength","avg_hr":110,"max_hr":150,"training_effect":2.4""".replace(
                    "\"sport\":\"strength\",",
                    ""
                )
            )
        val (main, more) = figures(strength, pace = false, f)
        assertEquals(
            listOf(R.string.fig_moving, R.string.fig_hr, R.string.fig_max_hr, R.string.fig_te),
            main.map {
                it.label
            }
        )
        assertEquals(emptyList<Figure>(), more)
    }

    @Test
    fun estimatedCaloriesCarryAnAsterisk() {
        val run =
            activity(
                """"distance_m":5000,"moving_s":1500,"metrics":{"calories":350.4,"calories_estimated":true,"power_curve":[],"speed_curve":[]}"""
            )
        assertEquals("350 kcal *", figures(run, pace = true, f).first.first { it.label == R.string.fig_calories }.value)
    }
}
