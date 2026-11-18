package com.zankup.android.ui

import com.zankup.android.designsystem.components.SportFamily
import com.zankup.android.domain.Sport

/** The API's sport key ("run", "ride"…) as the domain's sport; anything new is [Sport.OTHER]. */
fun sportOf(key: String?): Sport = Sport.fromKey(key)

/** The family that colours and draws it. */
val Sport.family: SportFamily
    get() =
        when (this) {
            Sport.RUN -> SportFamily.RUN
            Sport.RIDE -> SportFamily.RIDE
            Sport.WALK -> SportFamily.WALK
            Sport.STRENGTH -> SportFamily.STRENGTH
            Sport.ROW -> SportFamily.ROW
            Sport.SWIM -> SportFamily.SWIM
            Sport.OTHER -> SportFamily.OTHER
        }

/** Whether its card leads with distance (and pace or speed) or with time. */
val Sport.byDistance: Boolean get() = this != Sport.STRENGTH && this != Sport.OTHER
