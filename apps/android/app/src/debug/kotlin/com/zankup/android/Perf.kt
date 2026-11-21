package com.zankup.android

import android.util.Log
import android.view.Window
import androidx.metrics.performance.JankStats
import java.util.Locale

/**
 * Debug only: the frames that miss their deadline and the screen that was drawing
 * (`adb logcat -s ZankupJank`). Release builds carry an empty one in their own source set.
 */
object Perf {
    /** The route drawn right now, kept up to date by the shell as screens change. */
    @Volatile
    var screen: String = "start"

    private var stats: JankStats? = null

    fun install(window: Window) {
        if (stats != null) return
        stats =
            JankStats.createAndTrack(window) { frame ->
                if (!frame.isJank) return@createAndTrack
                val ms = frame.frameDurationUiNanos / 1_000_000.0
                val message = "screen=$screen frame=${"%.1f".format(Locale.ROOT, ms)}ms states=${frame.states}"
                Log.w("ZankupJank", message)
            }
    }

    fun setTracking(on: Boolean) {
        stats?.isTrackingEnabled = on
    }
}
