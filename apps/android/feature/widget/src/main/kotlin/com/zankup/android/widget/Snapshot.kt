package com.zankup.android.widget

import com.zankup.android.api.model.ActivitySummary
import com.zankup.android.api.model.FitnessDay
import com.zankup.android.domain.Units
import com.zankup.android.ui.Formats
import com.zankup.android.ui.sportOf
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/** What the widget draws, kept on the phone so it draws at once and offline. */
@Serializable
data class Snapshot(
    val activityId: Long? = null,
    /** The activity's name; null to show its sport's name. */
    val title: String? = null,
    val sport: String? = null,
    val subtitle: String = "",
    val figures: String = "",
    /** Today's form (TSB) with its sign, or empty. */
    val form: String = ""
)

/**
 * The snapshot for the latest activity and the latest day of form. Figures are metric whatever the
 * units, as the React Native app's widget.
 */
fun snapshotOf(latest: ActivitySummary?, form: FitnessDay?, locale: Locale): Snapshot {
    val formats = Formats(Units.METRIC, locale)
    val tsb = form?.tsb?.roundToInt()?.let { if (it > 0) "+$it" else "$it" }.orEmpty()
    if (latest == null) return Snapshot(form = tsb)
    val sport = sportOf(latest.sport)
    val moving = latest.movingS ?: latest.durationS
    val figures =
        listOfNotNull(
            latest.distanceM?.takeIf { it > 0 }?.let(formats::distance),
            formats.duration(moving),
            latest.distanceM?.takeIf { it > 0 && moving > 0 }?.let { formats.speedFor(sport, it, moving) }
        ).joinToString(" · ")
    return Snapshot(
        activityId = latest.id.toLong(),
        title = latest.name,
        sport = latest.sport,
        subtitle = formats.activityDate(latest.startUtc, latest.timezone),
        figures = figures,
        form = tsb
    )
}
