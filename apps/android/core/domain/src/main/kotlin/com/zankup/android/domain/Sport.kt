package com.zankup.android.domain

/** The sports Zankup groups activities into, by the key the API sends (`run`, `ride`…). */
enum class Sport(val key: String) {
    RUN("run"),
    RIDE("ride"),
    STRENGTH("strength"),
    WALK("walk"),
    ROW("row"),
    SWIM("swim"),
    OTHER("other")
    ;

    /**
     * Whether its main speed figure is a pace (time per distance), as runners, walkers, swimmers and
     * rowers read it, rather than a speed.
     */
    val usesPace: Boolean get() = this == RUN || this == WALK || this == SWIM || this == ROW

    companion object {
        /** The sport for an API key; anything unknown (a new sport on an older app) is [OTHER]. */
        fun fromKey(key: String?): Sport = entries.firstOrNull { it.key == key } ?: OTHER
    }
}
