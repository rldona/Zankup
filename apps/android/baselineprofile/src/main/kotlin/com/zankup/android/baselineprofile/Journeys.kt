package com.zankup.android.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** The release app on the phone, signed in (with the fingerprint lock off, so nothing waits for it). */
internal const val PACKAGE = "com.zankup.next"

private const val WAIT = 10_000L

/** Home, then the feed scrolled, an activity opened, and Progress: the paths worth compiling ahead. */
internal fun MacrobenchmarkScope.browse() {
    device.wait(Until.hasObject(By.res("tab_home")), WAIT)
    scrollFeed()
    openActivity()
    device.findObject(By.res("tab_progress"))?.click()
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.scrollFeed() {
    device.findObject(By.res("tab_activities"))?.click()
    device.wait(Until.hasObject(By.res("feed")), WAIT)
    val feed = device.findObject(By.res("feed")) ?: return
    feed.setGestureMargin(device.displayWidth / 5)
    repeat(2) {
        feed.fling(Direction.DOWN)
        device.waitForIdle()
    }
    feed.fling(Direction.UP)
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.openActivity() {
    val feed = device.findObject(By.res("feed")) ?: return
    feed.children.firstOrNull { it.isClickable }?.click() ?: feed.children.getOrNull(1)?.click()
    device.wait(Until.hasObject(By.res("activity")), WAIT)
    device.waitForIdle()
    device.findObject(By.res("activity"))?.fling(Direction.DOWN)
    device.waitForIdle()
    device.pressBack()
}
