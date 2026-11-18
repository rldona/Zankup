package com.zankup.android.feed

import com.zankup.android.domain.Sport
import com.zankup.android.domain.Units
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {
    private val today = LocalDate.of(2026, 11, 18)
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun params(request: SearchRequest) = searchParams(request, today, madrid)

    @Test
    fun nothingChosenIsTheFeed() {
        assertFalse(SearchRequest().searching)
        assertEquals(SearchParams(), params(SearchRequest()))
    }

    @Test
    fun textSportOrFilterSearches() {
        assertTrue(SearchRequest(query = "trail").searching)
        assertTrue(SearchRequest(sport = Sport.RIDE).searching)
        assertTrue(SearchRequest(filters = Filters(withGps = true)).searching)
        assertTrue(SearchRequest(filters = Filters(order = Order.ASC)).searching)
    }

    @Test
    fun numbersAcceptCommaOrPointAndRejectTheRest() {
        assertEquals(10.5, parseNumber("10,5")!!, 1e-9)
        assertEquals(10.5, parseNumber(" 10.5 ")!!, 1e-9)
        assertNull(parseNumber(""))
        assertNull(parseNumber("abc"))
        assertNull(parseNumber("-3"))
        assertNull(parseNumber("NaN"))
    }

    @Test
    fun distancesAreInTheUnitsTyped() {
        assertEquals(10_000.0, distanceMetres("10", Units.METRIC)!!, 1e-9)
        assertEquals(16_093.44, distanceMetres("10", Units.IMPERIAL)!!, 1e-6)
        assertEquals(304.8, ascentMetres("1000", Units.IMPERIAL)!!, 1e-6)
        assertEquals(500.0, ascentMetres("500", Units.METRIC)!!, 1e-9)
        assertEquals(1_800.0, durationSeconds("30")!!, 1e-9)
    }

    @Test
    fun periodsStartAtLocalMidnight() {
        assertNull(periodStart(Period.ALL, today, madrid))
        assertEquals("2025-12-31T23:00:00Z", periodStart(Period.YEAR, today, madrid))
        assertEquals("2025-11-17T23:00:00Z", periodStart(Period.LAST_12, today, madrid))
    }

    @Test
    fun everyFilterBecomesItsParameter() {
        val filters = Filters(
            period = Period.YEAR,
            distanceMin = "5",
            distanceMax = "21,1",
            durationMin = "20",
            durationMax = "120",
            ascentMin = "300",
            withRecord = true,
            withDescription = true,
            withGps = true,
            sort = SortBy.ELEVATION,
            order = Order.ASC
        )
        val p = params(SearchRequest(query = "  hill  ", sport = Sport.RUN, filters = filters))
        assertEquals("hill", p.q)
        assertEquals(listOf("run"), p.sport)
        assertEquals("2025-12-31T23:00:00Z", p.from)
        assertEquals(5_000.0, p.minDistance!!, 1e-9)
        assertEquals(21_100.0, p.maxDistance!!, 1e-9)
        assertEquals(1_200.0, p.minDuration!!, 1e-9)
        assertEquals(7_200.0, p.maxDuration!!, 1e-9)
        assertEquals(300.0, p.minAscent!!, 1e-9)
        assertEquals(true, p.withRecord)
        assertEquals(true, p.withDescription)
        assertEquals(true, p.withGps)
        assertEquals("elevation", p.sort)
        assertEquals("asc", p.order)
    }

    @Test
    fun defaultsAreLeftOut() {
        val p = params(SearchRequest(query = "x", filters = Filters(distanceMin = "oops")))
        assertNull(p.minDistance)
        assertNull(p.withRecord)
        assertNull(p.sort)
        assertNull(p.order)
    }

    @Test
    fun activeCountsWhatIsOn() {
        assertEquals(0, Filters().active)
        assertEquals(0, Filters(distanceMin = "abc").active)
        assertEquals(1, Filters(sort = SortBy.SPEED, order = Order.ASC).active)
        assertEquals(3, Filters(period = Period.LAST_12, durationMax = "60", withRecord = true).active)
        assertTrue(Filters(ascentMin = "10").on)
    }
}
