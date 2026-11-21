package com.zankup.android.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.ActivitiesApi
import com.zankup.android.api.endpoints.AuthApi
import com.zankup.android.api.endpoints.HealthApi
import com.zankup.android.api.endpoints.ProfileApi
import com.zankup.android.api.endpoints.StatsApi
import com.zankup.android.api.model.ActivityPage
import com.zankup.android.api.model.EstimateOut
import com.zankup.android.api.model.FitnessDay
import com.zankup.android.api.model.HealthDay
import com.zankup.android.api.model.Me
import com.zankup.android.api.model.Week
import com.zankup.android.data.CacheKeys
import com.zankup.android.data.Loadable
import com.zankup.android.data.Prefetch
import com.zankup.android.data.Store
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/** This week as Home draws it. */
data class WeekCard(
    val days: List<WeekDay>,
    val count: Int,
    val distanceM: Double,
    val movingS: Double,
    val ascentM: Double,
    val streak: Int,
    val usual: UsualWeek?
)

/** Everything Home shows, worked out from what is kept and fetched. */
data class HomeUi(
    val today: LocalDate = LocalDate.now(),
    val week: WeekCard? = null,
    val weekLoading: Boolean = true,
    val weekFailure: ApiResult.Failure? = null,
    /** The feed's first page has been read (kept, fetched or failed). */
    val feedLoaded: Boolean = false,
    /** The feed came back with no activities at all. */
    val empty: Boolean = false,
    /** The last day with activities, when it is today or yesterday. */
    val recent: LastDay? = null,
    val dayHealth: HealthDay? = null,
    val metrics: Metrics = Metrics(),
    val sevenDays: List<SportTotals> = emptyList(),
    val avgSteps: Double? = null,
    val avgRestingHr: Double? = null,
    val avgHrv: Double? = null,
    /** A pull from the top is on, with every answer on its way. */
    val refreshing: Boolean = false
)

fun homeUi(
    today: LocalDate,
    zone: ZoneId,
    week: Loadable<Week>,
    feed: Loadable<ActivityPage>,
    health: Loadable<List<HealthDay>>,
    estimates: Loadable<List<EstimateOut>>,
    fitness: Loadable<List<FitnessDay>>,
    me: Loadable<Me>
): HomeUi {
    val items = feed.data?.items.orEmpty()
    val days = health.data.orEmpty()
    val recent = recentDay(lastDay(items, zone), today)
    return HomeUi(
        today = today,
        week =
        week.data?.let {
            WeekCard(
                days = weekDays(it, today),
                count = it.thisWeek.count,
                distanceM = it.thisWeek.distanceM,
                movingS = it.thisWeek.movingS,
                ascentM = it.thisWeek.ascentM,
                streak = it.streakWeeks,
                usual = usualWeek(it)
            )
        },
        weekLoading = week.loading,
        weekFailure = week.failure.takeIf { week.data == null },
        feedLoaded = feed.data != null || feed.failure != null,
        empty = feed.data?.items?.isEmpty() == true,
        recent = recent,
        dayHealth = dayHealth(recent, today, days),
        metrics = metrics(estimates.data, fitness.data, me.data?.weightKg, health.data, today),
        sevenDays = lastSevenDays(items, today, zone),
        avgSteps = healthAverage(days) { it.steps },
        avgRestingHr = healthAverage(days) { it.restingHr },
        avgHrv = healthAverage(days) { it.hrv }
    )
}

/** The answers Home reads for one day, under the same keys the background sync fills. */
private class HomeQueries(store: Store, val today: LocalDate) {
    private val day = today.toString()

    val week = store.query(CacheKeys.week(day), Week.serializer()) { api ->
        call { api.service<ProfileApi>().getWeek(day) }
    }
    val feed = store.query(CacheKeys.FEED_FIRST, ActivityPage.serializer()) { api ->
        call { api.service<ActivitiesApi>().listActivities() }
    }

    // The last seven days, today included (`end` is exclusive): one request for every widget.
    private val start = today.minusDays(6).toString()
    private val end = today.plusDays(1).toString()
    val health = store.query("wellness/days/$start/$end", ListSerializer(HealthDay.serializer())) { api ->
        call { api.service<HealthApi>().getHealthDays(start, end) }
    }
    val estimates = store.query("stats/estimates", ListSerializer(EstimateOut.serializer())) { api ->
        call { api.service<StatsApi>().getEstimates() }
    }
    val fitness = store.query("stats/fitness", ListSerializer(FitnessDay.serializer())) { api ->
        call { api.service<StatsApi>().getFitness() }
    }
    val me = store.query("auth/me", Me.serializer()) { api ->
        call { api.service<AuthApi>().getMe() }
    }

    val all get() = listOf(week, feed, health, estimates, fitness, me)
}

/**
 * Home (OS-1130): this week, the last day with activities, the metrics and the last seven days, shown
 * from what the phone keeps and refreshed each time Home opens. A new day starts new queries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
@Inject
constructor(private val store: Store, private val prefetch: Prefetch) : ViewModel() {
    private val queries = MutableStateFlow(HomeQueries(store, LocalDate.now()))
    private val pulling = MutableStateFlow(false)

    val ui: StateFlow<HomeUi> =
        combine(
            queries
                .flatMapLatest { q ->
                    val first = combine(q.week.state, q.feed.state, q.health.state) { w, f, h -> Triple(w, f, h) }
                    val second = combine(q.estimates.state, q.fitness.state, q.me.state) { e, f, m -> Triple(e, f, m) }
                    combine(first, second) { (week, feed, health), (estimates, fitness, me) ->
                        homeUi(q.today, ZoneId.systemDefault(), week, feed, health, estimates, fitness, me)
                    }
                },
            pulling
        ) { ui, pulling -> ui.copy(refreshing = pulling) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), HomeUi())

    /** Fetches everything again, for today (a new day when the date has changed). */
    fun refresh() {
        today()
        queries.value.all.forEach { query -> viewModelScope.launch { query.refresh() } }
    }

    /**
     * A pull from the top: everything fetched again from the API, the indicator on until every answer is
     * back (or failed).
     */
    fun pullRefresh() {
        if (pulling.value) return
        today()
        viewModelScope.launch {
            pulling.value = true
            try {
                coroutineScope { queries.value.all.forEach { query -> launch { query.refresh() } } }
            } finally {
                pulling.value = false
            }
        }
    }

    private fun today() {
        val day = LocalDate.now()
        if (queries.value.today != day) queries.value = HomeQueries(store, day)
    }

    /** Starts loading an activity as the finger lands on it. */
    fun prefetch(id: Long) = prefetch.activity(id)

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}
