package com.zankup.android.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.model.HealthDay
import com.zankup.android.api.model.NightDetail
import com.zankup.android.api.model.WeightOut
import com.zankup.android.data.Loadable
import com.zankup.android.data.Store
import com.zankup.android.health.data.HealthQueries
import com.zankup.android.healthconnect.HealthConnect
import com.zankup.android.session.ApiSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Health tab (OS-1150): the days of the period chosen, last night's stages and the weighings. A
 * sync asks the server for new data and reloads the figures once it has had time to fetch them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HealthModel
@Inject
constructor(
    store: Store,
    private val source: ApiSource,
    private val healthConnect: HealthConnect
) : ViewModel() {
    val today: LocalDate = LocalDate.now()
    internal val queries = HealthQueries(store, source, viewModelScope)

    private val _period = MutableStateFlow(HealthPeriod.D30)
    val period: StateFlow<HealthPeriod> = _period.asStateFlow()

    internal val days: StateFlow<Loadable<List<HealthDay>>> =
        _period
            .flatMapLatest { queries.days(healthRange(it, today)) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, Loadable())

    internal val weight: StateFlow<Loadable<List<WeightOut>>> = queries.weight()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncFailed = MutableStateFlow(false)
    val syncFailed: StateFlow<Boolean> = _syncFailed.asStateFlow()

    fun setPeriod(period: HealthPeriod) {
        _period.value = period
    }

    internal fun night(day: String): StateFlow<Loadable<NightDetail>> = queries.night(day)

    fun refresh() = queries.refresh()

    /** `POST /api/wellness/sync` and Health Connect, then the figures again once the server has had time to bring them. */
    fun sync() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            _syncFailed.value = false
            val ok = queries.sync()
            // And this phone's Health Connect, once it has been allowed (as the React Native app).
            val api = source.api
            if (api != null && healthConnect.lastRead.value != null) {
                runCatching { healthConnect.readAndSend(api, force = true) }
            }
            if (ok) {
                delay(SYNC_WAIT_MS)
                queries.refreshWellness()
            }
            _syncFailed.value = !ok
            _syncing.value = false
        }
    }

    private companion object {
        /** As the React Native app: the server fetches Garmin and Google Health in the background. */
        const val SYNC_WAIT_MS = 8_000L
    }
}

/** The weight page: every weighing, and adding, changing or deleting one. */
@HiltViewModel
class WeightModel
@Inject
constructor(store: Store, source: ApiSource) : ViewModel() {
    val today: LocalDate = LocalDate.now()
    internal val queries = HealthQueries(store, source, viewModelScope)
    internal val weight: StateFlow<Loadable<List<WeightOut>>> = queries.weight()

    fun refresh() = queries.refresh()

    suspend fun save(day: LocalDate, kg: Double, old: LocalDate?): Boolean = queries.saveWeight(day, kg, old)

    suspend fun delete(day: LocalDate): Boolean = queries.deleteWeight(day)
}
