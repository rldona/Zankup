package com.zankup.android.health

import com.zankup.android.api.model.WeightOut
import com.zankup.android.domain.Units
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightLogicTest {
    private val today = LocalDate.of(2026, 11, 18)
    private val weighings = listOf(
        WeightOut("2026-11-10", 72.0),
        WeightOut("2026-11-14", 74.0),
        WeightOut("2026-11-16", 73.4)
    )

    @Test
    fun rangeStarts() {
        assertEquals(today.minusDays(91), weightRangeStart(WeightRange.M3, today, weighings))
        assertEquals(today.minusDays(30), weightRangeStart(WeightRange.M1, today, weighings))
        assertEquals(LocalDate.of(2026, 11, 10), weightRangeStart(WeightRange.ALL, today, weighings))
        assertEquals(today, weightRangeStart(WeightRange.ALL, today, emptyList()))
    }

    @Test
    fun dailyWeightsJoinWeighingsInALine() {
        val values = dailyWeights(weighings, LocalDate.of(2026, 11, 9), today)
        assertEquals(10, values.size)
        assertNull(values[0])
        assertEquals(72.0, values[1]!!, 1e-9)
        assertEquals(72.5, values[2]!!, 1e-9)
        assertEquals(74.0, values[5]!!, 1e-9)
        assertEquals(73.7, values[6]!!, 1e-9)
        assertEquals(73.4, values[7]!!, 1e-9)
        // Nothing after the last weighing.
        assertNull(values[8])
        assertNull(values[9])
    }

    @Test
    fun changeOverARange() {
        assertEquals(1.4, weightChange(weighings, LocalDate.of(2026, 11, 1))!!, 1e-9)
        assertEquals(-0.6, weightChange(weighings, LocalDate.of(2026, 11, 11))!!, 1e-9)
        assertNull(weightChange(weighings, LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun historyNewestFirstWithSteps() {
        val history = weightHistory(weighings)
        assertEquals(LocalDate.of(2026, 11, 16), history.first().day)
        assertEquals(-0.6, history.first().stepKg!!, 1e-9)
        assertEquals(2.0, history[1].stepKg!!, 1e-9)
        assertNull(history.last().stepKg)
    }

    @Test
    fun unitsAndParsing() {
        assertEquals(72.5, parseWeight("72,5", Units.METRIC)!!, 1e-9)
        assertEquals(72.5, parseWeight(" 72.5 ", Units.METRIC)!!, 1e-9)
        assertNull(parseWeight("19", Units.METRIC))
        assertNull(parseWeight("301", Units.METRIC))
        assertNull(parseWeight("", Units.METRIC))
        assertNull(parseWeight("abc", Units.METRIC))
        // 160 lb is 72.57 kg.
        assertEquals(72.57, parseWeight("160", Units.IMPERIAL)!!, 1e-9)
        assertNull(parseWeight("40", Units.IMPERIAL))
        assertNull(parseWeight("700", Units.IMPERIAL))
        assertEquals(220.462, toUnit(100.0, Units.IMPERIAL), 1e-3)
        assertEquals(100.0, fromUnit(toUnit(100.0, Units.IMPERIAL), Units.IMPERIAL), 1e-9)
        assertEquals(80.0, toUnit(80.0, Units.METRIC), 1e-9)
        assertEquals("lb", weightUnit(Units.IMPERIAL))
        assertEquals("kg", weightUnit(Units.METRIC))
    }
}
