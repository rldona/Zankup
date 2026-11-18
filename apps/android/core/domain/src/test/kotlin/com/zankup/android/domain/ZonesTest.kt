package com.zankup.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZonesTest {
    @Test
    fun limitsFromMaxHeartRate() {
        assertEquals(listOf(95, 114, 133, 152, 171), zoneLimits(190.0))
        // From a threshold heart rate, with the shares of its model.
        assertEquals(listOf(128, 144, 160), zoneLimits(160.0, listOf(0.8, 0.9, 1.0)))
    }

    @Test
    fun timeInHeartRateZones() {
        // Ten minutes, a sample every 10 s: 120 bpm (zone 2) then 170 bpm (zone 4).
        val times = List(61) { it * 10.0 }
        val hr = List(61) { if (it < 30) 120.0 else 170.0 }
        assertEquals(listOf(0.0, 300.0, 0.0, 300.0, 0.0), timeInZones(times, hr, zoneLimits(190.0)))
        // The 90 s gap of a paused watch is not counted.
        val paused = timeInZones(listOf(0.0, 10.0, 100.0), listOf(150.0, 150.0, 150.0), zoneLimits(190.0))
        assertEquals(10.0, paused[2], 0.0)
        // Below zone 1 and missing samples count nothing.
        assertEquals(List(5) { 0.0 }, timeInZones(listOf(0.0, 1.0, 2.0), listOf(80.0, null, 150.0), zoneLimits(190.0)))
    }

    // Threshold 3.3 m/s (5:03/km): bounds at 0, 78, 88, 95 and 100 % of it.
    private val bounds = listOf(0.0, 2.574, 2.904, 3.135, 3.3)

    @Test
    fun timeInPaceZones() {
        val speeds = listOf(2.9, 2.95, 3.2, 3.4, 0.2, null)
        val times = listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0)
        // 2.9 is just under Z3; 0.2 m/s is standing.
        assertEquals(listOf(0.0, 1.0, 1.0, 1.0, 1.0), paceZoneSeconds(speeds, times, bounds))
        // A pause.
        assertEquals(List(5) { 0.0 }, paceZoneSeconds(listOf(3.0, 3.0), listOf(0.0, 60.0), bounds))
    }

    @Test
    fun aSplitsPaceZone() {
        assertEquals(2, paceZoneOf(3.0, bounds))
        assertEquals(4, paceZoneOf(4.0, bounds))
        assertNull(paceZoneOf(0.0, bounds))
        assertNull(paceZoneOf(3.0, emptyList()))
    }
}
