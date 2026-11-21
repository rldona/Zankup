package com.zankup.android.progress.more

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.StatsApi
import com.zankup.android.api.endpoints.TrainingApi
import com.zankup.android.api.model.Bucket
import com.zankup.android.api.model.GoalCreate
import com.zankup.android.api.model.ZankupApiTrainingGoalOut
import com.zankup.android.data.Query
import com.zankup.android.data.Store
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

internal const val GOALS_KEY = "more/goals"

/** The goals, kept on the phone; shared by Goals and the goal form through [GOALS_KEY]. */
internal fun goalsQuery(store: Store) =
    store.query(GOALS_KEY, ListSerializer(ZankupApiTrainingGoalOut.serializer())) { api ->
        call { api.service<TrainingApi>().listGoals() }
    }

internal fun ZankupApiTrainingGoalOut.figures() =
    GoalFigures(period, metric, target, current, LocalDate.parse(start.take(10)), LocalDate.parse(end.take(10)))

/** Goals (objectives.tsx): the list, each goal's fold (its period's volume), remembered folds and deleting. */
@HiltViewModel
class ObjectivesModel
@Inject
constructor(
    private val store: Store,
    private val source: ApiSource,
    @param:ApplicationContext context: Context
) : ViewModel() {
    val goals = goalsQuery(store)
    private val prefs = context.getSharedPreferences("more_goals", Context.MODE_PRIVATE)
    private val openState = MutableStateFlow(prefs.getStringSet(OPEN, emptySet()).orEmpty().toSet())

    /** The goals whose fold is open, on this phone. */
    val open: StateFlow<Set<String>> = openState.asStateFlow()
    private val volumes = mutableMapOf<String, Query<List<Bucket>>>()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { goals.refreshIfStale() }
    }

    fun setOpen(id: Int, open: Boolean) {
        val now = if (open) openState.value + id.toString() else openState.value - id.toString()
        openState.value = now
        prefs.edit { putStringSet(OPEN, now) }
    }

    /** The volume a goal's fold charts, per week or month over its span. */
    internal fun volume(span: ChartSpan): Query<List<Bucket>> {
        val key = "more/volume/${span.from}/${span.to}/${span.unit}"
        return volumes.getOrPut(key) {
            val unit = if (span.unit == "week") StatsApi.UnitGetVolume.WEEK else StatsApi.UnitGetVolume.MONTH
            store.query(key, ListSerializer(Bucket.serializer())) { api ->
                call { api.service<StatsApi>().getVolume(span.from.toString(), span.to.toString(), unit) }
            }.also { q -> viewModelScope.launch { q.refresh() } }
        }
    }

    suspend fun delete(id: Int): Boolean {
        val api = source.api ?: return false
        val ok = call { api.service<TrainingApi>().deleteGoal(id) } is ApiResult.Ok
        if (ok) goals.refresh()
        return ok
    }

    private companion object {
        const val OPEN = "open"
    }
}

/** The goal form (objective-new.tsx): the goals (for what is taken) and saving one. */
@HiltViewModel
class ObjectiveEditModel
@Inject
constructor(store: Store, private val source: ApiSource) : ViewModel() {
    val goals = goalsQuery(store)

    init {
        viewModelScope.launch { goals.refresh() }
    }

    /** POST a new goal, or PATCH [id]'s; the goals are fetched again after. */
    suspend fun save(id: Int?, body: GoalCreate): Boolean {
        val api = source.api ?: return false
        val result =
            if (id == null) {
                call { api.service<TrainingApi>().createGoal(body) }
            } else {
                call { api.service<TrainingApi>().updateGoal(id, body) }
            }
        if (result is ApiResult.Ok) goals.refresh()
        return result is ApiResult.Ok
    }
}
