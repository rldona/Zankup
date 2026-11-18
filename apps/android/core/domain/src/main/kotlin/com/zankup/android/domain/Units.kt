package com.zankup.android.domain

/** The person's units, from the settings (`units`): metric unless they chose imperial. */
enum class Units {
    METRIC,
    IMPERIAL
    ;

    companion object {
        fun fromKey(key: String?): Units = if (key == "imperial") IMPERIAL else METRIC
    }
}
