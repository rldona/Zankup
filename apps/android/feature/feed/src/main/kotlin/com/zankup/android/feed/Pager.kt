package com.zankup.android.feed

import com.zankup.android.api.model.ActivitySummary
import com.zankup.android.api.model.SearchTotals
import com.zankup.android.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One page as the API sends it: its activities, where the next one starts (a cursor or an offset; null
 * at the end) and, for the search, the totals of every match.
 */
data class Chunk<C>(val items: List<ActivitySummary>, val next: C?, val totals: SearchTotals? = null)

/** The pages loaded so far, one after another. */
data class Paging<C>(
    val items: List<ActivitySummary> = emptyList(),
    val next: C? = null,
    val end: Boolean = true,
    val loading: Boolean = false,
    /** The first page has arrived (or was given): an empty list is then really empty. */
    val loaded: Boolean = false,
    val totals: SearchTotals? = null,
    val failure: ApiResult.Failure? = null
)

/**
 * Loads a list page by page with [Paging.next] as the next page's start. [start] begins a new list (a
 * new search, or the feed's pages after its kept first one); whatever was loading for the old one is
 * dropped. An activity already listed is not listed twice.
 */
class Pager<C : Any>(private val scope: CoroutineScope) {
    private val state = MutableStateFlow(Paging<C>())
    val paging: StateFlow<Paging<C>> = state.asStateFlow()

    private var fetch: (suspend (C?) -> ApiResult<Chunk<C>>)? = null
    private var job: Job? = null
    private var generation = 0

    /**
     * A new list that continues from [from]. With [loadFirst] it fetches its first page now (from null);
     * with [keep] what is shown stays until that page replaces it, as a pull to refresh does.
     */
    fun start(from: C?, loadFirst: Boolean, keep: Boolean = false, fetch: suspend (C?) -> ApiResult<Chunk<C>>): Job? {
        job?.cancel()
        generation++
        this.fetch = fetch
        val shown = state.value
        state.value = Paging(
            items = if (keep) shown.items else emptyList(),
            totals = if (keep) shown.totals else null,
            next = from,
            end = !loadFirst && from == null,
            loaded = !loadFirst || (keep && shown.loaded)
        )
        return if (loadFirst) load(replace = true) else null
    }

    /** Back to nothing, with nothing loading. */
    fun clear() {
        job?.cancel()
        generation++
        fetch = null
        state.value = Paging()
    }

    /** The next page, unless one is loading or the list is at its end; after a failure, it tries again. */
    fun loadMore() {
        val now = state.value
        if (now.loading || now.end) return
        load(replace = !now.loaded)
    }

    private fun load(replace: Boolean): Job? {
        val fetch = fetch ?: return null
        val from = state.value.next
        val mine = generation
        state.update { it.copy(loading = true, failure = null) }
        val launched = scope.launch {
            val result = fetch(from)
            if (mine != generation) return@launch
            state.update { now ->
                when (result) {
                    is ApiResult.Ok -> {
                        val chunk = result.value
                        val items = if (replace) chunk.items else now.items + chunk.items
                        now.copy(
                            items = items.distinctBy { it.id },
                            next = chunk.next,
                            end = chunk.next == null,
                            loading = false,
                            loaded = true,
                            totals = if (replace) chunk.totals else now.totals ?: chunk.totals
                        )
                    }
                    is ApiResult.Failure -> now.copy(loading = false, failure = result)
                }
            }
        }
        job = launched
        return launched
    }
}
