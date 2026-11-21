package com.zankup.android.health.data

import com.zankup.android.api.endpoints.AuthApi
import com.zankup.android.api.endpoints.HealthApi
import com.zankup.android.api.endpoints.ProfileApi
import com.zankup.android.api.model.HealthDay
import com.zankup.android.api.model.Me
import com.zankup.android.api.model.NightDetail
import com.zankup.android.api.model.WeightIn
import com.zankup.android.api.model.WeightOut
import com.zankup.android.data.Loadable
import com.zankup.android.data.Query
import com.zankup.android.data.Store
import com.zankup.android.health.HealthRange
import com.zankup.android.network.ApiResult
import com.zankup.android.network.ZankupApi
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/**
 * The answers Health reads, made the first time they are asked for and kept for the screen's life: each
 * shows what the phone keeps at once and is fetched again behind it. Keys start "health/". The writes
 * (sync, weighings) are here too, with the queries they change refreshed after them.
 */
internal class HealthQueries(
    private val store: Store,
    private val source: ApiSource,
    private val scope: CoroutineScope
) {
    private class Made<T>(val query: Query<T>, val state: StateFlow<Loadable<T>>)

    private val made = HashMap<String, Made<*>>()

    private fun <T> get(
        key: String,
        serializer: KSerializer<T>,
        fetch: suspend (ZankupApi) -> ApiResult<T>
    ): StateFlow<Loadable<T>> {
        @Suppress("UNCHECKED_CAST")
        (made[key] as Made<T>?)?.let { return it.state }
        val query = store.query("health/$key", serializer, fetch)
        val state = query.state.stateIn(scope, SharingStarted.Eagerly, Loadable())
        made[key] = Made(query, state)
        scope.launch { query.refreshIfStale() }
        return state
    }

    /** Fetches everything asked for so far again, when what is kept is older than its time. */
    fun refresh() = made.values.forEach { scope.launch { it.query.refreshIfStale() } }

    private fun refresh(prefix: String) =
        made.filterKeys { it.startsWith(prefix) }.values.forEach { scope.launch { it.query.refresh() } }

    fun days(range: HealthRange): StateFlow<Loadable<List<HealthDay>>> {
        val start = range.start.toString()
        val end = range.end.toString()
        return get("days/$start/$end", ListSerializer(HealthDay.serializer())) { api ->
            call { api.service<HealthApi>().getHealthDays(start, end) }
        }
    }

    fun night(day: String): StateFlow<Loadable<NightDetail>> = get("night/$day", NightDetail.serializer()) { api ->
        call { api.service<HealthApi>().getHealthNight(day) }
    }

    fun weight(): StateFlow<Loadable<List<WeightOut>>> = get("weight", ListSerializer(WeightOut.serializer())) { api ->
        call { api.service<ProfileApi>().listWeight() }
    }

    /** Asks every connected source for new data; the server fetches it in the background. */
    suspend fun sync(): Boolean {
        val api = source.api ?: return false
        return call { api.service<HealthApi>().syncHealth() } is ApiResult.Ok
    }

    /** Refreshes the days and nights after a sync. */
    fun refreshWellness() {
        refresh("days/")
        refresh("night/")
    }

    /**
     * Saves a day's weighing (a second on the same day replaces it); one moved to another day leaves
     * [old]. The latest is the profile's weight, so the profile is fetched again too.
     */
    suspend fun saveWeight(day: LocalDate, kg: Double, old: LocalDate?): Boolean {
        val api = source.api ?: return false
        val saved = call { api.service<ProfileApi>().saveWeight(WeightIn(day.toString(), kg)) } is ApiResult.Ok
        if (saved && old != null && old != day) call { api.service<ProfileApi>().deleteWeight(old.toString()) }
        if (saved) weightChanged()
        return saved
    }

    suspend fun deleteWeight(day: LocalDate): Boolean {
        val api = source.api ?: return false
        val ok = call { api.service<ProfileApi>().deleteWeight(day.toString()) } is ApiResult.Ok
        if (ok) weightChanged()
        return ok
    }

    private suspend fun weightChanged() {
        made["weight"]?.query?.refresh() ?: store.query(
            "health/weight",
            ListSerializer(WeightOut.serializer())
        ) { api -> call { api.service<ProfileApi>().listWeight() } }.refresh()
        // The profile as Home keeps it (its key), so W/kg and the rest follow.
        store.query("auth/me", Me.serializer()) { api -> call { api.service<AuthApi>().getMe() } }.refresh()
    }
}
