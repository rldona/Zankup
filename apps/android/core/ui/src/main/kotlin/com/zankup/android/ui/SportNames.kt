package com.zankup.android.ui

import androidx.annotation.StringRes
import com.zankup.android.domain.Sport

/** The sport's name, as a fallback title for an activity without one. */
@StringRes
fun sportName(sport: Sport): Int = when (sport) {
    Sport.RUN -> R.string.sport_run
    Sport.RIDE -> R.string.sport_ride
    Sport.STRENGTH -> R.string.sport_strength
    Sport.WALK -> R.string.sport_walk
    Sport.ROW -> R.string.sport_row
    Sport.SWIM -> R.string.sport_swim
    Sport.OTHER -> R.string.sport_other
}
