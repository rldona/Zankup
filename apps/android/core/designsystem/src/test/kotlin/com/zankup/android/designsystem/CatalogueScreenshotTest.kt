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
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The catalogue drawn in every theme (System as light and as dark). `./gradlew recordRoborazziDebug`
 * writes the pictures to src/test/screenshots; CI's `verifyRoborazziDebug` fails when one changes.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp-xxhdpi")
class CatalogueScreenshotTest(private val choice: ThemeChoice, private val dark: Boolean) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun catalogue() {
        compose.setContent {
            ZankupTheme(choice = choice, systemDark = dark, dynamicAccent = false) {
                Catalogue(Modifier.fillMaxWidth())
            }
        }
        val name = if (choice == ThemeChoice.SYSTEM) "system-${if (dark) "dark" else "light"}" else choice.key
        compose.onRoot().captureRoboImage("src/test/screenshots/catalogue-$name.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun themes() = ThemeChoice.entries.flatMap { choice ->
            val darkModes = if (choice == ThemeChoice.SYSTEM) listOf(false, true) else listOf(false)
            darkModes.map { dark -> arrayOf<Any>(choice, dark) }
        }
    }
}
