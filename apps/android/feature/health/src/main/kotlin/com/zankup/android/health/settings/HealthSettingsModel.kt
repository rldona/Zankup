package com.zankup.android.health.settings

import androidx.lifecycle.ViewModel
import com.zankup.android.api.endpoints.HealthApi
import com.zankup.android.api.model.HealthSettings
import com.zankup.android.api.model.PriorityIn
import com.zankup.android.data.Store
import com.zankup.android.healthconnect.HealthConnect
import com.zankup.android.healthconnect.ReadResult
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Settings › Health (OS-1151): the history, Health Connect, each source and the one to prefer per metric. */
@HiltViewModel
class HealthSettingsModel
@Inject
constructor(
    private val store: Store,
    private val source: ApiSource,
    val healthConnect: HealthConnect
) : ViewModel() {
    val settings = store.query("health/settings", HealthSettings.serializer()) { api ->
        call { api.service<HealthApi>().getHealthSettings() }
    }

    /** Asks Garmin and Google Health for the last year; the sources it asked, or null on failure. */
    suspend fun askHistory(): List<String>? {
        val api = source.api ?: return null
        return when (val r = call { api.service<HealthApi>().importHealthHistory() }) {
            is ApiResult.Ok -> r.value["queued"].orEmpty()
            is ApiResult.Failure -> null
        }
    }

    suspend fun readHistory(): ApiResult<ReadResult>? = source.api?.let { healthConnect.readHistory(it) }

    suspend fun read(): ApiResult<ReadResult>? =
        source.api?.let { healthConnect.readAndSend(it, force = true) }.also { settings.refresh() }

    suspend fun disconnectGoogle(): Boolean {
        val api = source.api ?: return false
        return (call { api.service<HealthApi>().disconnectGoogleHealth() } is ApiResult.Ok).also { settings.refresh() }
    }

    suspend fun wipe(name: String): Boolean {
        val api = source.api ?: return false
        return (call { api.service<HealthApi>().deleteHealthData(name) } is ApiResult.Ok).also { settings.refresh() }
    }

    /** The source to prefer for [metric]; null goes back to the automatic rules. */
    suspend fun prefer(metric: String, name: String?): Boolean {
        val api = source.api ?: return false
        val body = PriorityIn(mapOf(metric to listOfNotNull(name)))
        val result = call { api.service<HealthApi>().setHealthPriority(body) }
        if (result is ApiResult.Ok) store.put("health/settings", HealthSettings.serializer(), result.value)
        return result is ApiResult.Ok
    }

    suspend fun refresh() = settings.refresh()
}
