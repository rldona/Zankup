package com.zankup.android.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    private val english = Locale.ENGLISH
    private val spanish = Locale.forLanguageTag("es")
    private val metricEn = UnitFormat(Units.METRIC, english)
    private val metricEs = UnitFormat(Units.METRIC, spanish)
    private val imperialEn = UnitFormat(Units.IMPERIAL, english)

    @Test
    fun durationsLikeTheWatch() {
        assertEquals("5:05", formatDuration(305.0))
        assertEquals("1:02:05", formatDuration(3725.0))
        assertEquals("0:00", formatDuration(-3.0))
        assertEquals("0:01", formatDuration(0.5))
    }

    @Test
    fun hoursAndMinutes() {
        assertEquals("1 h 2 min", formatHoursMinutes(3725.0))
        assertEquals("10 min", formatHoursMinutes(600.0))
        assertEquals("1 h 0 min", formatHoursMinutes(3600.0))
    }

    @Test
    fun distanceByLocaleAndUnits() {
        assertEquals("6.62", metricEn.distanceValue(6615.0))
        assertEquals("6,62", metricEs.distanceValue(6615.0))
        assertEquals("1.00", imperialEn.distanceValue(1609.344))
        assertEquals("6.62 km", metricEn.distance(6615.0))
        assertEquals("1.00 mi", imperialEn.distance(1609.344))
        assertEquals("km", Units.METRIC.distanceUnit)
        assertEquals("mi", imperialEn.distanceUnit)
    }

    @Test
    fun paceFromSpeed() {
        // The reference night run: 6,615 m in 37:37 is 5:41/km.
        assertEquals("5:41", metricEn.paceValue(6615.0 / 2257))
        assertEquals("10:00", imperialEn.paceValue(1609.344 / 600))
        assertEquals("—", metricEn.paceValue(0.0))
        assertEquals("—", metricEn.paceValue(Double.NaN))
        assertEquals("5:41 /km", metricEn.pace(6615.0 / 2257))
        assertEquals("10:00 /mi", imperialEn.pace(1609.344 / 600))
    }

    @Test
    fun speedWithOneDecimalAtMost() {
        assertEquals("30,2", metricEs.speedValue(8.4))
        assertEquals("30.2", metricEn.speedValue(8.4))
        assertEquals("30", metricEn.speedValue(8.3333))
        assertEquals("30.2 km/h", metricEn.speed(8.4))
        assertEquals("18.8 mph", imperialEn.speed(8.4))
    }

    @Test
    fun paceOrSpeedBySport() {
        assertTrue(Sport.RUN.usesPace)
        assertTrue(Sport.SWIM.usesPace)
        assertTrue(Sport.ROW.usesPace)
        assertTrue(Sport.WALK.usesPace)
        assertFalse(Sport.RIDE.usesPace)
        assertFalse(Sport.STRENGTH.usesPace)
        assertEquals("5:41 /km", metricEn.speedFor(6615.0 / 2257, Sport.RUN))
        assertEquals("30.2 km/h", metricEn.speedFor(8.4, Sport.RIDE))
        assertEquals(Sport.OTHER, Sport.fromKey("kitesurf"))
        assertEquals(Sport.RIDE, Sport.fromKey("ride"))
    }

    @Test
    fun elevationRounded() {
        assertEquals("123 m", metricEn.elevation(123.4))
        assertEquals("404 ft", imperialEn.elevation(123.0))
        assertEquals("1,234 m", metricEn.elevation(1234.0))
        // Spanish groups thousands only from five digits on.
        assertEquals("1.234 m", metricEs.elevation(1234.0))
        assertEquals("12.345 m", metricEs.elevation(12345.0))
    }

    @Test
    fun numbersByLocale() {
        assertEquals("7", formatNumber(7.0, english))
        assertEquals("1,234.5", formatNumber(1234.5, english, maxDecimals = 1))
        assertEquals("1.234,5", formatNumber(1234.5, spanish, maxDecimals = 1))
        assertEquals("12.345,6", formatNumber(12345.6, spanish, maxDecimals = 1))
        // Halves away from zero, on the decimal the number prints as.
        assertEquals("-3,3", formatNumber(-3.25, spanish, maxDecimals = 1))
        assertEquals("2.68", formatNumber(2.675, english, maxDecimals = 2))
        assertEquals("0.3", formatNumber(0.25, english, maxDecimals = 1))
    }
}
