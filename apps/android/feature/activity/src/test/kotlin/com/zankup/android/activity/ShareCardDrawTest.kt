package com.zankup.android.activity

import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The card draws at its size; the picture is left in build/ to look at. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ShareCardDrawTest {
    @Test
    fun drawsTheCard() {
        val route = listOf(
            listOf(40.55 to -3.62, 40.553 to -3.615, 40.556 to -3.618, 40.56 to -3.61, 40.558 to -3.6, 40.552 to -3.605)
        )
        val bitmap = drawCard(
            CardContent(
                "San Sebastián de los Reyes Carrera",
                "17 de noviembre de 2026",
                listOf(
                    CardFigure("Distancia", "10,04 km"),
                    CardFigure("Tiempo", "55:43"),
                    CardFigure("Ritmo", "5:33 /km"),
                    CardFigure("Desnivel", "72 m")
                ),
                route
            ),
            CardColours(0xFF111316.toInt(), 0xFFEEF0F3.toInt(), 0xFF9AA1AD.toInt(), 0xFFFF922B.toInt())
        )
        assertEquals(CARD_WIDTH, bitmap.width)
        assertEquals(CARD_HEIGHT, bitmap.height)
        File("build/share-card.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
