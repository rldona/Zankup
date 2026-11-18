package com.zankup.android.health

import com.zankup.android.api.model.WeightOut
import com.zankup.android.domain.Units
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

// Weight over time, as the shared core has it (domain/weight.ts), and the person's units.

/** The ranges of the weight page; 3 months the first time. */
enum class WeightRange(val days: Int?) {
    M1(30),
    M3(91),
    M6(182),
    Y1(365),
    ALL(null)
}

/** The first day a range shows: so many days back, or the first weighing for all of them. */
fun weightRangeStart(range: WeightRange, today: LocalDate, weighings: List<WeightOut>): LocalDate {
    val days = range.days ?: return weighings.firstOrNull()?.let { LocalDate.parse(it.day) } ?: today
    return today.minusDays(days.toLong())
}

/**
 * One value a day from [from] to [to]: a weighing's own, between two weighings the straight line
 * joining them, and nothing before the first or after the last. Weighings oldest first.
 */
fun dailyWeights(weighings: List<WeightOut>, from: LocalDate, to: LocalDate): List<Double?> {
    val days = weighings.map { LocalDate.parse(it.day) }
    val out = ArrayList<Double?>()
    var next = 0
    var day = from
    while (day <= to) {
        while (next < weighings.size && days[next] < day) next++
        val after = weighings.getOrNull(next)
        val before = if (after != null && days[next] == day) after else weighings.getOrNull(next - 1)
        when {
            before == null || after == null -> out += null
            before === after -> out += after.weightKg
            else -> {
                val b = days[next - 1]
                val share = ChronoUnit.DAYS.between(b, day).toDouble() / ChronoUnit.DAYS.between(b, days[next])
                out += before.weightKg + (after.weightKg - before.weightKg) * share
            }
        }
        day = day.plusDays(1)
    }
    return out
}

private fun tenth(v: Double) = (v * 10).roundToLong() / 10.0

/** How much the weight moved from the first weighing on or after [from] to the latest; null with fewer than two. */
fun weightChange(weighings: List<WeightOut>, from: LocalDate): Double? {
    val inside = weighings.filter { LocalDate.parse(it.day) >= from }
    if (inside.size < 2) return null
    return tenth(inside.last().weightKg - inside.first().weightKg)
}

/** A weighing in the history, with its step from the one before (null for the first). */
data class WeightStep(val day: LocalDate, val weightKg: Double, val stepKg: Double?)

/** The history, newest first. */
fun weightHistory(weighings: List<WeightOut>): List<WeightStep> = weighings.mapIndexed { i, w ->
    val before = weighings.getOrNull(i - 1)
    WeightStep(LocalDate.parse(w.day), w.weightKg, before?.let { tenth(w.weightKg - it.weightKg) })
}.reversed()

// --- units ----------------------------------------------------------------------------------------

const val KG_PER_LB = 0.45359237
const val MIN_KG = 20.0
const val MAX_KG = 300.0

fun weightUnit(units: Units): String = if (units == Units.IMPERIAL) "lb" else "kg"

/** Kilograms in the person's unit. */
fun toUnit(kg: Double, units: Units): Double = if (units == Units.IMPERIAL) kg / KG_PER_LB else kg

/** The person's unit in kilograms. */
fun fromUnit(value: Double, units: Units): Double = if (units == Units.IMPERIAL) value * KG_PER_LB else value

/**
 * Kilograms from what was typed in the person's unit ("72.5", "72,5"), or null when it is no weight
 * (20 to 300 kg). Kept to two decimals, as the web does.
 */
fun parseWeight(text: String, units: Units): Double? {
    val typed = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    val kg = fromUnit(typed, units)
    if (kg < MIN_KG || kg > MAX_KG) return null
    return (kg * 100).roundToLong() / 100.0
}
