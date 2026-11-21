package com.zankup.android.progress.data

import com.zankup.android.api.endpoints.AuthApi
import com.zankup.android.api.endpoints.StatsApi
import com.zankup.android.api.endpoints.TrainingApi
import com.zankup.android.api.model.Bucket
import com.zankup.android.api.model.CalendarDay
import com.zankup.android.api.model.CurvePoint
import com.zankup.android.api.model.DeviceUse
import com.zankup.android.api.model.EstimateOut
import com.zankup.android.api.model.FitnessDay
import com.zankup.android.api.model.Me
import com.zankup.android.api.model.Streak
import com.zankup.android.api.model.ZankupApiStatsMedal
import com.zankup.android.api.model.ZankupApiTrainingGoalOut
import com.zankup.android.api.model.ZonesOut
import com.zankup.android.data.Loadable
import com.zankup.android.data.Query
import com.zankup.android.data.Store
import com.zankup.android.network.ApiResult
import com.zankup.android.network.ZankupApi
import com.zankup.android.network.call
import com.zankup.android.progress.logic.DayRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/**
 * The answers a Progress screen reads, made the first time they are asked for and kept for the screen's
 * life: each shows what the phone keeps at once and is fetched again behind it. Keys start "progress/".
 */
class ProgressQueries(private val store: Store, private val scope: CoroutineScope) {
    private class Made<T>(val query: Query<T>, val state: StateFlow<Loadable<T>>)

    private val made = HashMap<String, Made<*>>()

    private fun <T> get(
        key: String,
        serializer: KSerializer<T>,
        storeKey: String = "progress/$key",
        fetch: suspend (ZankupApi) -> ApiResult<T>
    ): StateFlow<Loadable<T>> {
        @Suppress("UNCHECKED_CAST")
        (made[key] as Made<T>?)?.let { return it.state }
        val query = store.query(storeKey, serializer, fetch)
        val state = query.state.stateIn(scope, SharingStarted.Eagerly, Loadable())
        made[key] = Made(query, state)
        scope.launch { query.refreshIfStale() }
        return state
    }

    /** Fetches everything asked for so far again, when what is kept is older than its time. */
    fun refresh() = made.values.forEach { scope.launch { it.query.refreshIfStale() } }

    /** Every query fetched again, returning when all have answered (or failed): a pull to refresh. */
    suspend fun refreshAll() = kotlinx.coroutines.coroutineScope {
        made.values.toList().forEach { launch { it.query.refresh() } }
    }

    fun volume(range: DayRange, byDay: Boolean): StateFlow<Loadable<List<Bucket>>> {
        val unit = if (byDay) StatsApi.UnitGetVolume.DAY else StatsApi.UnitGetVolume.WEEK
        return get("volume/${unit.value}/${range.from}/${range.to}", ListSerializer(Bucket.serializer())) { api ->
            call { api.service<StatsApi>().getVolume(range.from.toString(), range.to.toString(), unit) }
        }
    }

    fun calendar(range: DayRange): StateFlow<Loadable<List<CalendarDay>>> =
        get("calendar/${range.from}/${range.to}", ListSerializer(CalendarDay.serializer())) { api ->
            call { api.service<StatsApi>().getCalendar(range.from.toString(), range.to.toString()) }
        }

    fun streak(): StateFlow<Loadable<Streak>> = get("streak", Streak.serializer()) { api ->
        call { api.service<StatsApi>().getStreak() }
    }

    fun fitness(range: DayRange): StateFlow<Loadable<List<FitnessDay>>> =
        get("fitness/${range.from}/${range.to}", ListSerializer(FitnessDay.serializer())) { api ->
            call { api.service<StatsApi>().getFitness(range.from.toString(), range.to.toString()) }
        }

    fun estimates(): StateFlow<Loadable<List<EstimateOut>>> =
        get("estimates", ListSerializer(EstimateOut.serializer()), storeKey = "stats/estimates") { api ->
            call { api.service<StatsApi>().getEstimates() }
        }

    fun goals(): StateFlow<Loadable<List<ZankupApiTrainingGoalOut>>> =
        get("goals", ListSerializer(ZankupApiTrainingGoalOut.serializer())) { api ->
            call { api.service<TrainingApi>().listGoals() }
        }

    fun heartRateZones(range: DayRange): StateFlow<Loadable<ZonesOut>> =
        get("zones/hr/${range.from}/${range.to}", ZonesOut.serializer()) { api ->
            call {
                api.service<StatsApi>().getZones(StatsApi.KindGetZones.HR, range.from.toString(), range.to.toString())
            }
        }

    fun powerCurve(range: DayRange): StateFlow<Loadable<List<CurvePoint>>> =
        get("curves/power/${range.from}/${range.to}", ListSerializer(CurvePoint.serializer())) { api ->
            call {
                api.service<StatsApi>().getCurves(
                    StatsApi.KindGetCurves.POWER,
                    range.from.toString(),
                    range.to.toString()
                )
            }
        }

    fun me(): StateFlow<Loadable<Me>> = get("me", Me.serializer(), storeKey = "auth/me") { api ->
        call { api.service<AuthApi>().getMe() }
    }

    fun recentBests(days: Int): StateFlow<Loadable<List<ZankupApiStatsMedal>>> =
        get("bests/recent/$days", ListSerializer(ZankupApiStatsMedal.serializer())) { api ->
            call { api.service<StatsApi>().getRecentBests(days) }
        }

    fun devices(days: Int): StateFlow<Loadable<List<DeviceUse>>> =
        get("devices/$days", ListSerializer(DeviceUse.serializer())) { api ->
            call { api.service<StatsApi>().getDevices(days) }
        }
}
