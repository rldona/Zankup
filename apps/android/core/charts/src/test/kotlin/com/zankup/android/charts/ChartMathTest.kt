package com.zankup.android.charts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {
    private val profile = Profile(floatArrayOf(0f, 10f, 20f, 30f, 40f), byTime = false)

    @Test
    fun findsTheSampleAlongTheAxis() {
        assertEquals(0, profile.indexAt(0f))
        assertEquals(1, profile.indexAt(19.9f))
        assertEquals(2, profile.indexAt(20f))
        assertEquals(4, profile.indexAt(500f))
        assertEquals(-1, Profile(floatArrayOf(), byTime = true).indexAt(3f))
    }

    @Test
    fun marksAtRoundSteps() {
        assertEquals(2000f, markStep(10_234f, 1000f))
        assertEquals(1000f, markStep(5_000f, 1000f))
        assertEquals(10f * 1609.344f, markStep(80_000f, 1609.344f), 0.01f)
        assertEquals(1000f, markStep(0f, 1000f))
    }

    @Test
    fun averagesSamplesPerColumnAndKeepsGaps() {
        val values = floatArrayOf(1f, 3f, Float.NaN, Float.NaN, 5f)
        val cols = columns(profile, values, 2)
        assertEquals(2f, cols[0], 0f)
        assertEquals(5f, cols[1], 0f)
        val gap =
            columns(
                Profile(
                    FloatArray(9) {
                        it * 10f
                    },
                    false
                ),
                floatArrayOf(1f, 1f, 1f, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 2f, 2f),
                4
            )
        assertTrue(gap.any { it.isNaN() })
    }

    @Test
    fun slidesBetweenValues() {
        assertArrayEquals(floatArrayOf(5f, 10f), blend(floatArrayOf(0f, 10f), floatArrayOf(10f, 10f), 0.5f), 0f)
        assertArrayEquals(floatArrayOf(1f), blend(floatArrayOf(0f, 0f), floatArrayOf(1f), 0.5f), 0f)
    }

    @Test
    fun clipsTheRangeOnlyWhenAsked() {
        val values = FloatArray(100) { if (it == 0) 1000f else 300f + it }
        assertEquals(1000f, valueRange(values, clip = false)!!.second)
        assertTrue(valueRange(values, clip = true)!!.second < 1000f)
    }
}
