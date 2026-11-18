package com.zankup.android.you

import com.zankup.android.domain.Units
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Pure helpers of the You tab and its profile editing (tested without a phone). */

/** Up to two letters for the round picture: of the name, else of the e-mail. */
fun initials(name: String?, email: String): String = (name?.takeIf { it.isNotBlank() } ?: email)
    .split(Regex("[\\s@.]+"))
    .filter { it.isNotEmpty() }
    .take(2)
    .joinToString("") { it.first().uppercase() }

/** Whole years from a date of birth ("YYYY-MM-DD") to [today], or null when it is no date. */
fun ageOn(birth: String, today: LocalDate): Int? {
    val born = isoDate(birth) ?: return null
    val before = today.monthValue < born.monthValue ||
        (today.monthValue == born.monthValue && today.dayOfMonth < born.dayOfMonth)
    return today.year - born.year - if (before) 1 else 0
}

/** "1990-04-07" as a date, when it is a real one. */
fun isoDate(value: String): LocalDate? {
    if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value)) return null
    return try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
}

/** "1990-04-07" as people write it here: "07/04/1990". */
fun toDayMonthYear(iso: String): String {
    val parts = iso.take(10).split("-")
    return if (parts.size == 3 && parts.all { it.isNotEmpty() }) "${parts[2]}/${parts[1]}/${parts[0]}" else iso
}

/** What is typed in the date field as it should read: only digits, slashes put in ("14081982" → "14/08/1982"). */
fun typeDayMonthYear(text: String): String {
    val digits = text.filter(Char::isDigit).take(8)
    return listOf(digits.take(2), digits.drop(2).take(2), digits.drop(4)).filter { it.isNotEmpty() }.joinToString("/")
}

/** "14/08/1982" (or "14-8-1982", "14.08.1982") as "1982-08-14", or null when it is no real date. */
fun fromDayMonthYear(text: String): String? {
    val match = Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$").find(text.trim()) ?: return null
    val (d, m, y) = match.destructured
    val iso = "$y-${m.padStart(2, '0')}-${d.padStart(2, '0')}"
    return iso.takeIf { isoDate(it) != null }
}

/** A typed number, with a comma or a point, or null. */
fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

private const val KG_PER_LB = 0.45359237
private const val CM_PER_IN = 2.54

/** The weight in the person's units (kg or lb). */
fun weightIn(kg: Double, units: Units): Double = if (units == Units.IMPERIAL) kg / KG_PER_LB else kg

fun weightToKg(value: Double, units: Units): Double = if (units == Units.IMPERIAL) value * KG_PER_LB else value

/** The height in the person's units (cm or in), whole. */
fun heightIn(cm: Int, units: Units): Int = if (units == Units.IMPERIAL) (cm / CM_PER_IN).roundToInt() else cm

fun heightToCm(value: Double, units: Units): Int =
    if (units == Units.IMPERIAL) (value * CM_PER_IN).roundToInt() else value.roundToInt()

/** "72.5" for a field: one decimal at most, no trailing zero. */
fun fieldNumber(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/** Best efforts in miles mean nothing to someone who thinks in km: left out with metric units. */
val MILE_EFFORTS = setOf("half_mile", "1mile", "2mile", "10mile")

fun effortShown(key: String, units: Units) = units == Units.IMPERIAL || key !in MILE_EFFORTS

/** The edit form as typed: the fields of the sheet. */
data class ProfileForm(
    val name: String = "",
    val location: String = "",
    /** As typed, DD/MM/YYYY. */
    val birth: String = "",
    /** "", "female", "male" or "other". */
    val sex: String = "",
    val weight: String = "",
    val height: String = ""
)

/** What the form gives: the body of `PATCH /api/auth/me`, or what is wrong with it. */
sealed interface ProfileCheck {
    data class Ok(val body: JsonObject) : ProfileCheck

    data object BadDate : ProfileCheck

    data object BadNumber : ProfileCheck
}

/**
 * The PATCH body, as the React Native app sends it: the name only when there is one (it cannot be
 * cleared), and every other field, null when empty (null clears it). Weight and height go in kg and cm.
 */
fun checkProfile(form: ProfileForm, units: Units): ProfileCheck {
    val birth = if (form.birth.isBlank()) null else fromDayMonthYear(form.birth) ?: return ProfileCheck.BadDate
    val weight =
        if (form.weight.isBlank()) {
            null
        } else {
            parseNumber(form.weight)?.takeIf { it > 0 }
                ?: return ProfileCheck.BadNumber
        }
    val height =
        if (form.height.isBlank()) {
            null
        } else {
            parseNumber(form.height)?.takeIf { it > 0 }
                ?: return ProfileCheck.BadNumber
        }
    val fields = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
    form.name.trim().takeIf { it.isNotEmpty() }?.let { fields["display_name"] = JsonPrimitive(it) }
    fields["location"] = form.location.trim().takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull
    fields["birth_date"] = birth?.let(::JsonPrimitive) ?: JsonNull
    fields["sex"] = form.sex.takeIf { it in SEXES }?.let(::JsonPrimitive) ?: JsonNull
    fields["weight_kg"] = weight?.let { JsonPrimitive((weightToKg(it, units) * 100).roundToInt() / 100.0) } ?: JsonNull
    fields["height_cm"] = height?.let { JsonPrimitive(heightToCm(it, units)) } ?: JsonNull
    return ProfileCheck.Ok(JsonObject(fields))
}

val SEXES = listOf("female", "male", "other")
