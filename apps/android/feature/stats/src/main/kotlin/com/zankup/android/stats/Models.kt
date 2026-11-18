package com.zankup.android.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.StatsApi
import com.zankup.android.api.endpoints.TrainingApi
import com.zankup.android.api.model.Bucket
import com.zankup.android.api.model.CalendarDay
import com.zankup.android.api.model.CurvePoint
import com.zankup.android.api.model.EfficiencyPoint
import com.zankup.android.api.model.EngineStatus
import com.zankup.android.api.model.EstimateOut
import com.zankup.android.api.model.FitnessDay
import com.zankup.android.api.model.Heatmap
import com.zankup.android.api.model.Records
import com.zankup.android.api.model.Review
import com.zankup.android.api.model.Summary
import com.zankup.android.api.model.YearLine
import com.zankup.android.data.Loadable
import com.zankup.android.data.Query
import com.zankup.android.data.Store
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

private const val STOP_AFTER_MS = 5_000L

/** The engine is asked again this often while it recomputes. */
internal const val ENGINE_POLL_MS = 3_000L

/**
 * The kept answer for each [keys] value, refreshed as it is first shown: a new period, month or sport
 * switches to its own query.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <K, T> ViewModel.follow(keys: Flow<K>, query: (K) -> Query<T>): StateFlow<Loadable<T>> = keys
    .distinctUntilChanged()
    .flatMapLatest { key ->
        val q = query(key)
        q.state.onStart { viewModelScope.launch { q.refresh() } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), Loadable())

/** One fixed query, refreshed each time the screen opens. */
internal fun <T> ViewModel.once(query: Query<T>): StateFlow<Loadable<T>> = query.state
    .onStart { viewModelScope.launch { query.refresh() } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), Loadable())

private fun Period.key() = "$from/$to"

/** Which best-effort curve: power (any sport) or speed (runs). */
enum class CurveKind { POWER, SPEED }

/**
 * Stats (OS-1141): the period chosen on the toolbar and, for it, the summary, fitness, volume,
 * efficiency, curves and records; the engine's progress is polled while it recomputes.
 */
@HiltViewModel
class StatsModel
@Inject
constructor(private val store: Store, private val source: ApiSource) : ViewModel() {
    private val periodState = MutableStateFlow(StatsPeriod.initial(LocalDate.now()))
    val period: StateFlow<StatsPeriod> = periodState.asStateFlow()

    private val curveKind = MutableStateFlow(CurveKind.POWER)
    val curve: StateFlow<CurveKind> = curveKind.asStateFlow()

    private val engineState = MutableStateFlow<EngineStatus?>(null)
    val engine: StateFlow<EngineStatus?> = engineState.asStateFlow()
    private var polling: Job? = null

    private val years = store.query("stats/years", ListSerializer(Int.serializer())) { api ->
        call { api.service<StatsApi>().getYears() }
    }

    private val range = periodState.map { it.range }

    val summary = follow(range) { p ->
        store.query("stats/summary/${p.key()}", Summary.serializer()) { api ->
            call { api.service<StatsApi>().getSummary(p.from.toString(), p.to.toString()) }
        }
    }
    val fitness = follow(range) { p ->
        store.query("stats/fitness/${p.key()}", ListSerializer(FitnessDay.serializer())) { api ->
            call { api.service<StatsApi>().getFitness(p.from.toString(), p.to.toString()) }
        }
    }
    val volume = follow(range) { p ->
        store.query("stats/volume/${p.key()}/${p.unit.name.lowercase()}", ListSerializer(Bucket.serializer())) { api ->
            val unit = StatsApi.UnitGetVolume.entries.first { it.name == p.unit.name }
            call { api.service<StatsApi>().getVolume(p.from.toString(), p.to.toString(), unit) }
        }
    }
    val efficiency = follow(range) { p ->
        store.query("stats/efficiency/${p.key()}", ListSerializer(EfficiencyPoint.serializer())) { api ->
            call { api.service<StatsApi>().getEfficiency(p.from.toString(), p.to.toString()) }
        }
    }
    val curves = follow(combine(range, curveKind) { p, k -> p to k }) { (p, k) -> curveQuery(k, p.from, p.to) }
    val allTimeCurves = follow(curveKind) { k -> curveQuery(k, ALL_TIME_FROM, null) }
    val records = follow(range) { p ->
        store.query("stats/records/${p.key()}", Records.serializer()) { api ->
            call { api.service<StatsApi>().getRecords(p.from.toString(), p.to.toString()) }
        }
    }

    init {
        viewModelScope.launch {
            years.state.collect { kept ->
                // Newest first: the last is the first year of the history.
                val first = kept.data?.minOrNull() ?: return@collect
                periodState.update { it.copy(firstYear = first) }
            }
        }
    }

    private fun curveQuery(kind: CurveKind, from: LocalDate, to: LocalDate?): Query<List<CurvePoint>> {
        val apiKind = if (kind == CurveKind.POWER) StatsApi.KindGetCurves.POWER else StatsApi.KindGetCurves.SPEED
        val sport = if (kind == CurveKind.SPEED) listOf("run") else null
        val key = "stats/curves/${kind.name.lowercase()}/$from/${to ?: "now"}"
        return store.query(key, ListSerializer(CurvePoint.serializer())) { api ->
            call { api.service<StatsApi>().getCurves(apiKind, from.toString(), to?.toString(), sport) }
        }
    }

    /** Called when the screen opens: the years, and the engine until it is idle. */
    fun open() {
        viewModelScope.launch { years.refresh() }
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (true) {
                val api = source.api ?: break
                val status = (call { api.service<TrainingApi>().getEngine() } as? ApiResult.Ok)?.value
                engineState.value = status
                if (status?.running != true) break
                delay(ENGINE_POLL_MS)
            }
        }
    }

    fun setPeriod(period: StatsPeriod) {
        periodState.value = period
    }

    fun setMode(mode: StatsMode) = periodState.update { it.copy(mode = mode) }

    fun setCurve(kind: CurveKind) {
        curveKind.value = kind
    }

    private companion object {
        val ALL_TIME_FROM: LocalDate = LocalDate.of(2000, 1, 1)
    }
}

/** Estimates (OS-1141): VDOT, FTP, critical speed, threshold HR and race predictions. */
@HiltViewModel
class EstimatesModel
@Inject
constructor(store: Store) : ViewModel() {
    // Home reads the same answer under the same key.
    val estimates = once(
        store.query("stats/estimates", ListSerializer(EstimateOut.serializer())) { api ->
            call { api.service<StatsApi>().getEstimates() }
        }
    )
}

/** Year by year (OS-1141): every year's cumulative distance. */
@HiltViewModel
class YearsModel
@Inject
constructor(store: Store) : ViewModel() {
    val lines = once(
        store.query("stats/progress", ListSerializer(YearLine.serializer())) { api ->
            call { api.service<StatsApi>().getProgress() }
        }
    )
}

/** The training calendar (OS-1141): the weeks that cover the month shown. */
@HiltViewModel
class CalendarModel
@Inject
constructor(private val store: Store) : ViewModel() {
    private val monthState = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = monthState.asStateFlow()

    val days = follow(monthState) { m ->
        val (from, to) = monthGrid(m)
        store.query("stats/calendar/$from/$to", ListSerializer(CalendarDay.serializer())) { api ->
            call { api.service<StatsApi>().getCalendar(from.toString(), to.toString()) }
        }
    }

    fun shift(by: Long) = monthState.update { it.plusMonths(by) }
}

/** The heatmap (OS-1141): every GPS route, of one sport or all. */
@HiltViewModel
class HeatmapModel
@Inject
constructor(private val store: Store) : ViewModel() {
    private val sportState = MutableStateFlow<String?>(null)
    val sport: StateFlow<String?> = sportState.asStateFlow()

    val routes = follow(sportState.map { it ?: ALL }) { s ->
        store.query("stats/heatmap/$s", Heatmap.serializer()) { api ->
            call { api.service<StatsApi>().getHeatmap(sport = if (s == ALL) null else listOf(s)) }
        }
    }

    fun setSport(sport: String?) {
        sportState.value = sport
    }

    private companion object {
        const val ALL = "all"
    }
}

/** A month or a year in review (OS-1141), moved with arrows. */
@HiltViewModel
class ReviewModel
@Inject
constructor(private val store: Store) : ViewModel() {
    private val shownState = MutableStateFlow<Pair<ReviewPeriod, String>?>(null)
    val shown: StateFlow<Pair<ReviewPeriod, String>?> = shownState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val review: StateFlow<Loadable<Review>> = shownState
        .flatMapLatest { shown ->
            if (shown == null) return@flatMapLatest kotlinx.coroutines.flow.flowOf(Loadable())
            val (period, value) = shown
            val q = store.query("stats/review/${period.name.lowercase()}/$value", Review.serializer()) { api ->
                val p = if (period == ReviewPeriod.YEAR) {
                    StatsApi.PeriodGetReview.YEAR
                } else {
                    StatsApi.PeriodGetReview.MONTH
                }
                call { api.service<StatsApi>().getReview(p, value) }
            }
            q.state.onStart { viewModelScope.launch { q.refresh() } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), Loadable())

    /** The period the screen was opened on, this month or this year; kept once moved. */
    fun open(period: String) {
        if (shownState.value != null) return
        val p = if (period == "year") ReviewPeriod.YEAR else ReviewPeriod.MONTH
        shownState.value = p to currentPeriodValue(p, LocalDate.now())
    }

    fun shift(by: Int) = shownState.update { it?.let { (p, v) -> p to shiftPeriod(p, v, by) } }

    fun switchTo(period: ReviewPeriod) = shownState.update { it?.let { (_, v) -> period to switchPeriod(period, v) } }
}
