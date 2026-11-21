package com.zankup.android.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frames, not seconds: the cold start and the journeys that must stay smooth (scrolling, push/pop and
 * the Training chart's scrub), with the Baseline Profile on. On the phone:
 *
 *   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.zankup.android.baselineprofile.Frames
 */
@RunWith(AndroidJUnit4::class)
class Frames {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun startup() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
        measureBlock = { startActivityAndWait() }
    )

    @Test
    fun feedScroll() = journey(setup = { openFeed() }) { scrollFeed() }

    @Test
    fun activityPushPop() = journey(setup = { openFeed() }) { openActivity() }

    @Test
    fun progressScroll() = journey(setup = { openProgress() }) { scrollProgress() }

    @Test
    fun trainingScrub() = journey(setup = { openTraining() }) { scrubTraining() }

    /** A journey: the app opened and placed by [setup], then [block] measured frame by frame. */
    private fun journey(
        iterations: Int = 5,
        setup: MacrobenchmarkScope.() -> Unit,
        block: MacrobenchmarkScope.() -> Unit
    ) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = iterations,
        setupBlock = {
            startActivityAndWait()
            setup()
        },
        measureBlock = block
    )
}
