package com.zankup.android.healthconnect

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.zankup.android.api.endpoints.HealthApi
import com.zankup.android.api.model.HealthConnectUpload
import com.zankup.android.network.ApiResult
import com.zankup.android.network.ZankupApi
import com.zankup.android.network.call
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What a read sent: nights and days. */
data class ReadResult(val nights: Int, val days: Int)

/**
 * Health Connect on the phone (OS-1151), as the React Native app has it (OS-607): read permissions
 * only (sleep, resting HR, HRV, SpO₂, respiration, steps); each read takes what is new since the last
 * one, with two days of overlap since a night can arrive late, and sends it to the instance, which keeps
 * it as the `health_connect` source. It runs from the button and, once allowed, when the app opens (at
 * most once an hour). Nothing is written to Health Connect.
 */
@Singleton
class HealthConnect
@Inject
constructor(@param:ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("health_connect", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val last = MutableStateFlow(prefs.getString(LAST, null)?.let(Instant::parse))

    /** When it last sent, or null if it never has. */
    val lastRead: StateFlow<Instant?> = last.asStateFlow()

    /** Whether Health Connect is on the phone and up to date. */
    fun available(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    /** The permission screen; the result is what was granted. */
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    suspend fun granted(): Set<String> = if (available()) {
        runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
    } else {
        emptySet()
    }

    /**
     * Reads and sends. [force] skips the hourly limit; [since] reads from then instead of where the
     * last read stopped. Null when nothing could be read (not available, nothing allowed, too soon).
     */
    suspend fun readAndSend(api: ZankupApi, force: Boolean = false, since: Instant? = null): ApiResult<ReadResult>? =
        mutex.withLock {
            if (!available()) return@withLock null
            val allowed = granted()
            if (READ.none { it in allowed }) return@withLock null
            val previous = last.value
            val now = Instant.now()
            if (!force && previous != null && Duration.between(previous, now) < EVERY) return@withLock null
            val start = since ?: previous?.minus(OVERLAP) ?: now.minus(Duration.ofDays(FIRST_DAYS))
            val range = TimeRangeFilter.between(start, now)
            fun has(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in allowed

            val nights =
                if (has(SleepSessionRecord::class)) {
                    all(SleepSessionRecord::class, range).mapNotNull { r ->
                        sleepUpload(
                            Night(
                                r.metadata.id.ifEmpty { null },
                                r.metadata.dataOrigin.packageName,
                                r.startTime,
                                r.endTime,
                                r.endZoneOffset,
                                r.stages.map { Triple(it.startTime, it.endTime, it.stage) }
                            )
                        )
                    }
                } else {
                    emptyList()
                }
            val series = buildMap {
                if (has(RestingHeartRateRecord::class)) {
                    put(
                        Field.RESTING_HR,
                        all(RestingHeartRateRecord::class, range).map {
                            Sample(it.time, it.zoneOffset, it.beatsPerMinute.toDouble())
                        }
                    )
                }
                if (has(HeartRateVariabilityRmssdRecord::class)) {
                    put(
                        Field.HRV,
                        all(HeartRateVariabilityRmssdRecord::class, range).map {
                            Sample(it.time, it.zoneOffset, it.heartRateVariabilityMillis)
                        }
                    )
                }
                if (has(OxygenSaturationRecord::class)) {
                    put(
                        Field.SPO2,
                        all(OxygenSaturationRecord::class, range).map {
                            Sample(it.time, it.zoneOffset, it.percentage.value)
                        }
                    )
                }
                if (has(RespiratoryRateRecord::class)) {
                    put(
                        Field.RESPIRATION,
                        all(RespiratoryRateRecord::class, range).map { Sample(it.time, it.zoneOffset, it.rate) }
                    )
                }
            }
            // Steps summed per local day from midnight, or the first day would be a partial count.
            val steps =
                if (has(StepsRecord::class)) {
                    val midnight = LocalDateTime.ofInstant(start, ZoneId.systemDefault()).toLocalDate().atStartOfDay()
                    client.aggregateGroupByPeriod(
                        AggregateGroupByPeriodRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter =
                            TimeRangeFilter.between(midnight, LocalDateTime.ofInstant(now, ZoneId.systemDefault())),
                            timeRangeSlicer = Period.ofDays(1)
                        )
                    ).associate { it.startTime.toLocalDate().toString() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
                } else {
                    emptyMap()
                }
            val days = dailyAverages(series, steps)

            val chunks = maxOf(nights.size, days.size, 1)
            var at = 0
            while (at < chunks) {
                val sent = call {
                    api.service<HealthApi>().uploadHealthConnect(
                        HealthConnectUpload(nights.drop(at).take(CHUNK), days.drop(at).take(CHUNK))
                    )
                }
                if (sent is ApiResult.Failure) return@withLock sent
                at += CHUNK
            }
            prefs.edit().putString(LAST, now.toString()).apply()
            last.value = now
            ApiResult.Ok(ReadResult(nights.size, days.size))
        }

    /** The last year: beyond 30 days before the permissions were given it needs [HISTORY] granted. */
    suspend fun readHistory(api: ZankupApi) =
        readAndSend(api, force = true, since = Instant.now().minus(Duration.ofDays(HISTORY_DAYS)))

    private suspend fun <T : Record> all(type: KClass<T>, range: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val page = client.readRecords(ReadRecordsRequest(type, range, pageSize = 1000, pageToken = token))
            out += page.records
            token = page.pageToken?.ifEmpty { null }
        } while (token != null)
        return out
    }

    companion object {
        private const val LAST = "last"
        private const val FIRST_DAYS = 30L
        private const val HISTORY_DAYS = 365L
        private const val CHUNK = 200
        private val OVERLAP = Duration.ofDays(2)
        private val EVERY = Duration.ofHours(1)

        /** The read permissions asked for. */
        val READ: Set<String> =
            setOf(
                SleepSessionRecord::class,
                RestingHeartRateRecord::class,
                HeartRateVariabilityRmssdRecord::class,
                OxygenSaturationRecord::class,
                RespiratoryRateRecord::class,
                StepsRecord::class
            ).map { HealthPermission.getReadPermission(it) }.toSet()

        /** Reading more than 30 days before the permissions were given. */
        const val HISTORY = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
    }
}
