package com.zankup.android.stats

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsLogicTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun monthsRangeSpansWholeMonthsInEitherOrder() {
        val nov = monthIndex(YearMonth.of(2026, 11))
        assertEquals(
            Period(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1), VolumeUnit.DAY),
            monthsRange(nov, nov)
        )
        val two = monthsRange(nov, nov - 1)
        assertEquals(LocalDate.of(2026, 10, 1), two.from)
        assertEquals(LocalDate.of(2026, 12, 1), two.to)
        assertEquals(VolumeUnit.WEEK, two.unit)
        assertEquals(VolumeUnit.MONTH, monthsRange(nov - 3, nov).unit)
        // Across a year: December to January.
        val dec = monthIndex(YearMonth.of(2025, 12))
        assertEquals(LocalDate.of(2025, 12, 1), monthsRange(dec, dec + 1).from)
        assertEquals(LocalDate.of(2026, 2, 1), monthsRange(dec, dec + 1).to)
    }

    @Test
    fun monthIndexRoundTrips() {
        val m = YearMonth.of(2025, 1)
        assertEquals(m, monthOf(monthIndex(m)))
        assertEquals(YearMonth.of(2024, 12), monthOf(monthIndex(m) - 1))
    }

    @Test
    fun otherPeriods() {
        assertEquals(Period(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1), VolumeUnit.MONTH), yearRange(2025))
        assertEquals(
            Period(LocalDate.of(2019, 1, 1), LocalDate.of(2026, 11, 19), VolumeUnit.YEAR),
            allTimeRange(today, 2019)
        )
        assertEquals(
            Period(LocalDate.of(2025, 11, 19), LocalDate.of(2026, 11, 19), VolumeUnit.MONTH),
            last12Range(today)
        )
    }

    @Test
    fun periodOpensOnLastMonthToThis() {
        val p = StatsPeriod.initial(today, 2020)
        assertEquals(StatsMode.MONTHS, p.mode)
        assertEquals(LocalDate.of(2026, 10, 1), p.range.from)
        assertEquals(LocalDate.of(2026, 12, 1), p.range.to)
        assertFalse(p.canToForward)
        assertTrue(p.canToBack)
        assertTrue(p.canFromBack)
        assertTrue(p.canFromForward)
    }

    @Test
    fun arrowsStopAtTheEndsOfHistoryAndNeverCross() {
        val p = StatsPeriod.initial(today, 2026).copy(mode = StatsMode.YEAR)
        assertFalse(p.canYearBack)
        assertFalse(p.canYearForward)
        assertEquals(2026, p.yearBy(1).year)
        assertEquals(2026, p.yearBy(-1).year)
        val months = StatsPeriod.initial(today, 2020)
        val met = months.fromBy(1)
        assertEquals(met.toMonth, met.fromMonth)
        assertFalse(met.canFromForward)
        assertFalse(met.canToBack)
        assertEquals(met.fromMonth, met.toBy(-1).toMonth)
        assertEquals(months.toMonth, months.toBy(1).toMonth)
        val first = StatsPeriod(today, 2026, StatsMode.MONTHS, 2026, 2026 * 12, 2026 * 12 + 2)
        assertFalse(first.canFromBack)
    }

    @Test
    fun compareSharesTheRow() {
        val c = compare(50.0, 100.0)
        assertEquals(-50, c.percent)
        assertEquals(0.5f, c.now)
        assertEquals(1f, c.before)
        assertNull(compare(10.0, 0.0).percent)
        assertEquals(0f, compare(0.0, 0.0).now)
    }

    @Test
    fun comparisonLabelsNameTheSpans() {
        val en = Locale.ENGLISH
        assertEquals(
            Compared.Named("2024", "2025"),
            comparisonLabels(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1), en)
        )
        assertEquals(
            Compared.Named("Nov 2025", "Nov 2026"),
            comparisonLabels(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1), en)
        )
        assertEquals(
            Compared.Named("Oct–Nov 2025", "Oct–Nov 2026"),
            comparisonLabels(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 1), en)
        )
        assertEquals(Compared.Rolling, comparisonLabels(last12Range(today).from, last12Range(today).to, en))
        assertEquals(Compared.None, comparisonLabels(LocalDate.of(2019, 1, 1), today, en))
    }

    @Test
    fun calendarWeeksCoverTheMonthFromMonday() {
        // November 2026 starts on a Sunday and ends on a Monday.
        val (from, to) = monthGrid(YearMonth.of(2026, 11))
        assertEquals(LocalDate.of(2026, 10, 26), from)
        assertEquals(LocalDate.of(2026, 12, 7), to)
        // June 2026 starts on a Monday and ends on a Tuesday.
        assertEquals(LocalDate.of(2026, 6, 1) to LocalDate.of(2026, 7, 6), monthGrid(YearMonth.of(2026, 6)))
        // February 2027 is exactly four weeks.
        assertEquals(LocalDate.of(2027, 2, 1) to LocalDate.of(2027, 3, 1), monthGrid(YearMonth.of(2027, 2)))
        assertEquals(6, weeksOf((0 until 42).toList()).size)
    }

    @Test
    fun twoTapsMakeARange() {
        val first = pick(null, 5)
        assertEquals(5..5, first)
        assertEquals(2..5, pick(first, 2))
        assertEquals(9..9, pick(2..5, 9))
        assertEquals(5..5, pick(5..5, 5))
    }

    @Test
    fun spansAddUp() {
        data class A(val d: Double?, val t: Double)
        val total = spanTotal(listOf(A(1000.0, 300.0), A(null, 600.0)), { it.d }, { it.t })
        assertEquals(SpanTotal(2, 1000.0, 900.0), total)
        assertEquals(28f, bubbleSize(100.0, 100.0))
        assertEquals(18f + 10f * kotlin.math.sqrt(0.1f), bubbleSize(null, 100.0), 1e-4f)
    }

    @Test
    fun reviewPeriodsMove() {
        assertEquals("2027-01", shiftPeriod(ReviewPeriod.MONTH, "2026-12", 1))
        assertEquals("2025-12", shiftPeriod(ReviewPeriod.MONTH, "2026-01", -1))
        assertEquals("2025", shiftPeriod(ReviewPeriod.YEAR, "2026", -1))
        assertEquals("2026", switchPeriod(ReviewPeriod.YEAR, "2026-03"))
        assertEquals("2026-12", switchPeriod(ReviewPeriod.MONTH, "2026"))
        assertEquals("2026-11", currentPeriodValue(ReviewPeriod.MONTH, today))
        assertEquals("November 2026", periodTitle(ReviewPeriod.MONTH, "2026-11", Locale.ENGLISH))
        assertEquals("+50 %", change(150.0, 100.0))
        assertEquals("-25 %", change(75.0, 100.0))
        assertNull(change(5.0, 0.0))
    }

    @Test
    fun yearLines() {
        val accent = Color(0xFFFC4C02)
        val sports = listOf(Color(0xFF1C7ED6), Color(0xFFFD7E14), Color(0xFF40C057))
        val colours = yearColours(listOf(2026, 2025, 2024, 2023), 2025, accent, sports, Color.Gray)
        assertEquals(accent, colours[2025])
        // Orange is too close to the accent: left out.
        assertEquals(Color(0xFF1C7ED6), colours[2026])
        assertEquals(Color(0xFF40C057), colours[2024])
        assertEquals(Color(0xFF1C7ED6), colours[2023])
        assertEquals(listOf(2026, 2024, 2025), selectedLast(listOf(2026, 2025, 2024), { it }, 2025))
        assertEquals(12.5, lineTotal(listOf(1000.0, 12500.0), 1000.0), 1e-9)
        assertEquals(0.0, lineTotal(emptyList(), 1000.0), 1e-9)
    }

    @Test
    fun curvesAndEfficiency() {
        assertEquals(listOf(1, 5, 60), curveDurations(mapOf(5 to 1.0, 60 to 2.0), mapOf(1 to 3.0, 5 to 2.0)))
        val d = curveDomain(listOf(100.0, 400.0), listOf(200.0, 300.0))
        assertEquals(95.0, d.start, 1e-9)
        assertEquals(345.0, d.endInclusive, 1e-9)
        assertEquals(0.0..1.0, curveDomain(emptyList(), emptyList()))
        assertEquals(50.0, efficiencyChange(listOf(1.0, 1.0, 1.5))!!, 1e-9)
        assertNull(efficiencyChange(listOf(1.0, 2.0)))
        assertEquals("30 s", durationLabel(30))
        assertEquals("5 min", durationLabel(300))
        assertEquals("1 h", durationLabel(3600))
    }

    @Test
    fun recordsAndEstimates() {
        assertFalse(effortShown("1mile", imperial = false))
        assertTrue(effortShown("1mile", imperial = true))
        assertTrue(effortShown("5k", imperial = false))
        assertEquals("riegel" to "10", methodParts("riegel:10"))
        assertEquals("ftp_20min" to "", methodParts("ftp_20min"))
    }
}
