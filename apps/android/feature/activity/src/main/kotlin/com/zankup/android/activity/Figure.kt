package com.zankup.android.activity

import androidx.annotation.StringRes
import com.zankup.android.api.model.ActivityDetail
import com.zankup.android.domain.formatNumber
import com.zankup.android.ui.Formats
import kotlin.math.roundToInt

/** A figure of the activity: its name (a string resource) and its value written for this person. */
data class Figure(@param:StringRes val label: Int, val value: String)

/**
 * The activity's figures: six main ones (distance, moving time, pace or speed, ascent, power or heart
 * rate, calories — the next ones filling the places a sport without distance leaves) and the rest
 * behind "More". Estimated calories carry an asterisk.
 */
fun figures(a: ActivityDetail, pace: Boolean, f: Formats): Pair<List<Figure>, List<Figure>> {
    val moving = a.movingS ?: a.durationS
    val m = a.metrics
    val main = mutableListOf<Figure>()
    val more = mutableListOf<Figure>()
    val distance = a.distanceM?.takeIf { it > 0 }
    if (distance != null) main += Figure(R.string.fig_distance, f.distance(distance))
    main += Figure(R.string.fig_moving, f.duration(moving))
    if (distance != null && moving > 0) {
        val speed = distance / moving
        main +=
            Figure(
                if (pace) R.string.fig_pace else R.string.fig_speed,
                if (pace) f.unit.pace(speed) else f.unit.speed(speed)
            )
    }
    a.ascentM?.takeIf { it > 0 }?.let { main += Figure(R.string.fig_ascent, f.elevation(it)) }
    when {
        a.avgPower != null -> main += Figure(R.string.fig_power, "${a.avgPower} W")
        a.avgHr != null -> main += Figure(R.string.fig_hr, "${a.avgHr} bpm")
    }
    val kcal = a.calories?.toDouble() ?: m?.calories
    if (kcal != null && kcal > 0) {
        val star = if (a.calories == null) " *" else ""
        main += Figure(R.string.fig_calories, "${formatNumber(kcal.roundToInt().toDouble(), f.locale)} kcal$star")
    }
    val stopped = a.movingS?.let { a.durationS - it } ?: 0.0
    if (stopped > 60) more += Figure(R.string.fig_elapsed, f.duration(a.durationS))
    if (a.avgPower != null && a.avgHr != null) more += Figure(R.string.fig_hr, "${a.avgHr} bpm")
    a.maxHr?.let { more += Figure(R.string.fig_max_hr, "$it bpm") }
    a.normalizedPower?.let { more += Figure(R.string.fig_np, "$it W") }
    m?.trimp?.let { more += Figure(R.string.fig_effort, formatNumber(it.roundToInt().toDouble(), f.locale)) }
    if (pace) m?.gapSpeed?.let { more += Figure(R.string.fig_gap, f.unit.pace(it)) }
    m?.decoupling?.let { more += Figure(R.string.fig_decoupling, "${formatNumber(it, f.locale, 0, 1)} %") }
    a.trainingEffect?.takeIf { it > 0 }?.let { more += Figure(R.string.fig_te, formatNumber(it, f.locale, 0, 1)) }
    // Without a distance (strength, indoor) the next figures fill the six places.
    while (main.size < 6 && more.isNotEmpty()) main += more.removeAt(0)
    return main to more
}
