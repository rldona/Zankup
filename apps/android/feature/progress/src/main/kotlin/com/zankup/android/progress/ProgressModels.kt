package com.zankup.android.progress

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zankup.android.data.Store
import com.zankup.android.progress.data.ProgressQueries
import com.zankup.android.progress.logic.DayRange
import com.zankup.android.progress.logic.FitnessRange
import com.zankup.android.progress.logic.ProgressRange
import com.zankup.android.progress.logic.monthPairRange
import com.zankup.android.progress.logic.volumeRange
import com.zankup.android.progress.logic.weekAt
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The weeks of Relative effort's page, and the three before the first that give its range. */
internal const val EFFORT_WEEKS = 52

/** The tab's cards (OS-1140): every answer they read, asked for at once and refreshed when the tab opens. */
@HiltViewModel
class ProgressModel
@Inject
constructor(store: Store) : ViewModel() {
    val today: LocalDate = LocalDate.now()
    internal val queries = ProgressQueries(store, viewModelScope)
    private val tomorrow = today.plusDays(1)

    // Created the first time a card asks for it: the cards at the bottom of the tab do not fetch until
    // they are scrolled into view.
    internal val volume by lazy { queries.volume(DayRange(weekAt(11, today), tomorrow), byDay = false) }
    internal val streak by lazy { queries.streak() }
    internal val log by lazy { queries.calendar(DayRange(weekAt(1, today), weekAt(0, today).plusDays(7))) }
    internal val fitness by lazy {
        queries.fitness(com.zankup.android.progress.logic.rangePeriod(FitnessRange.M1, today))
    }
    internal val effortWeeks by lazy { queries.volume(DayRange(weekAt(4, today), tomorrow), byDay = false) }
    internal val effortDays by lazy {
        queries.volume(DayRange(weekAt(1, today), weekAt(0, today).plusDays(7)), byDay = true)
    }
    internal val estimates by lazy { queries.estimates() }
    internal val goals by lazy { queries.goals() }
    internal val zones by lazy { queries.heartRateZones(volumeRange(ProgressRange.M1, today).current) }
    internal val recentPower by lazy { queries.powerCurve(DayRange(today.minusDays(55), tomorrow)) }
    internal val allPower by lazy { queries.powerCurve(DayRange(LocalDate.of(2000, 1, 1), tomorrow)) }
    internal val me by lazy { queries.me() }
    internal val medals by lazy { queries.recentBests(365) }
    internal val months by lazy { queries.calendar(monthPairRange(today)) }
    internal val devices by lazy { queries.devices(30) }

    private val _refreshing = MutableStateFlow(false)

    /** While a pull from the top is fetching everything again. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh() = queries.refresh()

    /** A pull from the top, as Home's: every card fetched again, the indicator on until all are back. */
    fun pullRefresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                queries.refreshAll()
            } finally {
                _refreshing.value = false
            }
        }
    }
}

/** The detail of Volume: the range and the one compared with, made as they are picked. */
@HiltViewModel
class ProgressDetailModel
@Inject
constructor(store: Store) : ViewModel() {
    val today: LocalDate = LocalDate.now()
    internal val queries = ProgressQueries(store, viewModelScope)
    internal val streak = queries.streak()

    fun refresh() = queries.refresh()
}

/** The training log: the first activity, then eight weeks of calendar at a time as they scroll in. */
@HiltViewModel
class TrainingLogModel
@Inject
constructor(store: Store) : ViewModel() {
    val today: LocalDate = LocalDate.now()
    internal val queries = ProgressQueries(store, viewModelScope)
    internal val streak = queries.streak()

    fun refresh() = queries.refresh()
}

/** Fitness over a range, the range kept on the phone for next time. */
@HiltViewModel
class FitnessModel
@Inject
constructor(store: Store, @param:ApplicationContext context: Context) : ViewModel() {
    val today: LocalDate = LocalDate.now()
    internal val queries = ProgressQueries(store, viewModelScope)
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    /** The range chosen last time, a month the first time. */
    var range: FitnessRange
        get() = FitnessRange.entries.firstOrNull { it.key == prefs.getString(RANGE, null) } ?: FitnessRange.M1
        set(value) = prefs.edit { putString(RANGE, value.key) }

    fun refresh() = queries.refresh()

    private companion object {
        const val RANGE = "fitness_range"
    }
}

/** Relative effort: a year of weeks (and the three before), and the picked week's days. */
@HiltViewModel
class EffortModel
@Inject
constructor(store: Store) : ViewModel() {
    val today: LocalDate = LocalDate.now()
    internal val queries = ProgressQueries(store, viewModelScope)
    internal val weeks = queries.volume(DayRange(weekAt(EFFORT_WEEKS + 2, today), today.plusDays(1)), byDay = false)

    fun refresh() = queries.refresh()
}

/** Race predictions: the engine's estimates with their history. */
@HiltViewModel
class PredictionsModel
@Inject
constructor(store: Store) : ViewModel() {
    val today: LocalDate = LocalDate.now()
    internal val queries = ProgressQueries(store, viewModelScope)
    internal val estimates = queries.estimates()

    fun refresh() = queries.refresh()
}
