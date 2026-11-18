package com.zankup.android.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.zankup.android.designsystem.catalogue.Catalogue
import com.zankup.android.designsystem.theme.ThemeChoice
import com.zankup.android.designsystem.theme.ZankupTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The catalogue at Android's largest font scale (200 %) and in High contrast (OS-1181): text wraps rather
 * than being cut, and every control keeps its 48 dp target.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp-xxhdpi")
class LargeFontScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(fontScale = 2.0f)
    fun largestFont() {
        compose.setContent {
            ZankupTheme(choice = ThemeChoice.SYSTEM, systemDark = true, dynamicAccent = false) {
                Catalogue(Modifier.fillMaxWidth())
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/catalogue-font-200.png")
    }

    @Test
    @Config(fontScale = 2.0f)
    fun largestFontHighContrast() {
        compose.setContent {
            ZankupTheme(choice = ThemeChoice.HIGH_CONTRAST, systemDark = false, dynamicAccent = false) {
                Catalogue(Modifier.fillMaxWidth())
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/catalogue-font-200-high-contrast.png")
    }
}
