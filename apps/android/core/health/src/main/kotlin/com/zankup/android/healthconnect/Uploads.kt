package com.zankup.android.healthconnect

import com.zankup.android.api.model.DayUpload
import com.zankup.android.api.model.SleepUpload
import com.zankup.android.api.model.StageIn
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt

// Turning Health Connect records into what `/api/wellness/health-connect` takes, as the React Native
// app does (apps/mobile/src/health.ts). Pure, so it is tested without Health Connect.

/** A night as read: its id and origin, times, the offset at its end and its stages (Health Connect's codes). */
data class Night(
    val id: String?,
    val origin: String?,
    val start: Instant,
    val end: Instant,
    val endOffset: ZoneOffset?,
    val stages: List<Triple<Instant, Instant, Int>>
)

/** One reading of a daily measure, with the offset it was taken at. */
data class Sample(val time: Instant, val offset: ZoneOffset?, val value: Double)

/** The measures averaged per day; overnight ones count for the morning after. */
enum class Field(val overnight: Boolean) { RESTING_HR(false), HRV(true), SPO2(true), RESPIRATION(true) }

// SleepSessionRecord's stages: 1 awake, 2 sleeping, 3 out of bed, 4 light, 5 deep, 6 REM, 7 awake in
// bed. "Sleeping" without detail counts as light; 0 (unknown) is left out.
private val STAGES =
    mapOf(
        1 to StageIn.Stage.AWAKE,
        2 to StageIn.Stage.LIGHT,
        3 to StageIn.Stage.AWAKE,
        4 to StageIn.Stage.LIGHT,
        5 to StageIn.Stage.DEEP,
        6 to StageIn.Stage.REM,
        7 to StageIn.Stage.AWAKE
    )

/** The night as uploaded; null without an id (the server dedupes by it). */
fun sleepUpload(night: Night): SleepUpload? {
    val id = night.id ?: return null
    return SleepUpload(
        id = id,
        start = night.start.toString(),
        end = night.end.toString(),
        origin = night.origin,
        utcOffsetS = night.endOffset?.totalSeconds,
        stages = night.stages.mapNotNull { (from, to, code) ->
            STAGES[code]?.let { StageIn(from.toString(), to.toString(), it) }
        }
    )
}

/**
 * The local date a sample belongs to: its own offset, or the phone's zone without one. Overnight
 * measures are moved six hours forward, so a reading at 23:30 counts for the morning after.
 */
fun localDay(sample: Sample, overnight: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    val offset = sample.offset ?: zone.rules.getOffset(sample.time)
    val shifted = sample.time.plusSeconds(if (overnight) 6 * 3600L else 0L)
    return shifted.atOffset(offset).toLocalDate().toString()
}

/** Averages per day of each measure (one decimal), merged with the steps into one row per day. */
fun dailyAverages(
    series: Map<Field, List<Sample>>,
    steps: Map<String, Long> = emptyMap(),
    zone: ZoneId = ZoneId.systemDefault()
): List<DayUpload> {
    val rows = sortedMapOf<String, DayUpload>()
    fun row(day: String) = rows.getOrPut(day) { DayUpload(day = day) }
    series.forEach { (field, samples) ->
        samples.filter { it.value > 0 }
            .groupBy { localDay(it, field.overnight, zone) }
            .forEach { (day, list) ->
                val mean = (list.sumOf { it.value } / list.size * 10).roundToInt() / 10.0
                val r = row(day)
                rows[day] =
                    when (field) {
                        Field.RESTING_HR -> r.copy(restingHr = mean)
                        Field.HRV -> r.copy(hrvMs = mean)
                        Field.SPO2 -> r.copy(spo2 = mean)
                        Field.RESPIRATION -> r.copy(respiration = mean)
                    }
            }
    }
    steps.forEach { (day, count) -> if (count > 0) rows[day] = row(day).copy(steps = count.toInt()) }
    return rows.values.toList()
}
