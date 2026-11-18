package com.zankup.android.you

import com.zankup.android.domain.Units
import java.time.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouLogicTest {
    @Test
    fun initialsOfTheNameElseTheEmail() {
        assertEquals("AL", initials("Ana López", "a@b.c"))
        assertEquals("RL", initials(null, "raul.lopez@example.com"))
        assertEquals("RX", initials("  ", "r@x"))
    }

    @Test
    fun ageCountsWholeYears() {
        assertEquals(35, ageOn("1990-04-07", LocalDate.of(2025, 4, 7)))
        assertEquals(34, ageOn("1990-04-07", LocalDate.of(2025, 4, 6)))
        assertEquals(34, ageOn("1990-12-31", LocalDate.of(2025, 1, 1)))
        assertNull(ageOn("not a date", LocalDate.of(2025, 1, 1)))
    }

    @Test
    fun datesAreTypedDayMonthYear() {
        assertEquals("14/08/1982", typeDayMonthYear("14081982"))
        assertEquals("14/0", typeDayMonthYear("14a0"))
        assertEquals("07/04/1990", toDayMonthYear("1990-04-07"))
        assertEquals("1982-08-14", fromDayMonthYear("14/08/1982"))
        assertEquals("1982-08-04", fromDayMonthYear("4-8-1982"))
        assertNull(fromDayMonthYear("31/02/1990"))
        assertNull(fromDayMonthYear("1990-02-01"))
        assertNotNullDate("2000-02-29")
        assertNull(isoDate("2001-02-29"))
    }

    private fun assertNotNullDate(iso: String) = assertTrue(isoDate(iso) != null)

    @Test
    fun theBodyClearsEmptyFieldsAndKeepsTheName() {
        val check = checkProfile(ProfileForm(name = " ", location = " Bilbao ", sex = "male"), Units.METRIC)
        val body = (check as ProfileCheck.Ok).body
        assertFalse("display_name" in body)
        assertEquals(JsonPrimitive("Bilbao"), body["location"])
        assertEquals(JsonNull, body["birth_date"])
        assertEquals(JsonPrimitive("male"), body["sex"])
        assertEquals(JsonNull, body["weight_kg"])
        assertEquals(JsonNull, body["height_cm"])
    }

    @Test
    fun theBodyIsInKgAndCm() {
        val metric = checkProfile(ProfileForm(name = "Ana", weight = "72,5", height = "180"), Units.METRIC)
        val body = (metric as ProfileCheck.Ok).body
        assertEquals(JsonPrimitive("Ana"), body["display_name"])
        assertEquals(JsonPrimitive(72.5), body["weight_kg"])
        assertEquals(JsonPrimitive(180), body["height_cm"])
        val imperial = checkProfile(ProfileForm(weight = "160", height = "71"), Units.IMPERIAL) as ProfileCheck.Ok
        assertEquals(JsonPrimitive(72.57), imperial.body["weight_kg"])
        assertEquals(JsonPrimitive(180), imperial.body["height_cm"])
    }

    @Test
    fun badDatesAndNumbersAreRefused() {
        assertEquals(ProfileCheck.BadDate, checkProfile(ProfileForm(birth = "31/02/1990"), Units.METRIC))
        assertEquals(ProfileCheck.BadNumber, checkProfile(ProfileForm(weight = "abc"), Units.METRIC))
        assertEquals(ProfileCheck.BadNumber, checkProfile(ProfileForm(height = "0"), Units.METRIC))
    }

    @Test
    fun unitsRoundTrip() {
        assertEquals("72.5", fieldNumber(weightIn(72.5, Units.METRIC)))
        assertEquals("160", fieldNumber(weightIn(72.57472, Units.IMPERIAL)))
        assertEquals(71, heightIn(180, Units.IMPERIAL))
    }

    @Test
    fun mileRecordsOnlyWithImperial() {
        assertFalse(effortShown("1mile", Units.METRIC))
        assertTrue(effortShown("1mile", Units.IMPERIAL))
        assertTrue(effortShown("10k", Units.METRIC))
    }

    @Test
    fun fastApiDetail() {
        assertEquals("No", errorDetail("""{"detail":"No"}"""))
        assertEquals("bad", errorDetail("""{"detail":[{"msg":"bad"}]}"""))
        assertNull(errorDetail("oops"))
    }
}
