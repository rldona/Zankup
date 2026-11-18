package com.zankup.android.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.zankup.android.designsystem.theme.ThemeChoice
import com.zankup.android.designsystem.theme.ZankupTheme
import kotlin.math.sin
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A pace with stops far outside its axis, the elevation behind, a section and the cursor. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h300dp-xxhdpi")
class LineChartScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun paceWithStopsCursorAndSection() {
        val n = 600
        val profile = Profile(FloatArray(n) { it * 17f }, byTime = false)
        val pace = FloatArray(n) { if (it % 150 == 0) 1800f else 330f + 20f * sin(it / 25f) }
        val alt = FloatArray(n) { 650f + 30f * sin(it / 80f) }
        val cursor = ChartCursor().apply { index = 240 }
        val section = ChartSection().apply { range = 300..420 }
        compose.setContent {
            ZankupTheme(choice = ThemeChoice.DARK, dynamicAccent = false) {
                LineChart(
                    profile,
                    Series("Pace", pace, {
                        "%d:%02d".format(it.toInt() / 60, it.toInt() % 60)
                    }, "5:30 /km", inverted = true, backdrop = alt),
                    cursor,
                    section,
                    modifier = Modifier.background(ZankupTheme.colors.surface).padding(8.dp),
                    xLabel = { "%.2f km".format(it / 1000f) },
                    markLabel = { "%.0f km".format(it / 1000f) },
                    clip = true
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/line-pace.png")
    }
}
