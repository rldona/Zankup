package com.zankup.android

import android.view.Window

/** Release builds carry no frame tracing: the debug source set has the real one. */
object Perf {
    @Volatile
    var screen: String = "start"

    fun install(window: Window) = Unit

    fun setTracking(on: Boolean) = Unit
}
