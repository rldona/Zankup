package com.zankup.android.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.ActivitiesApi
import com.zankup.android.api.model.ActivityPage
import com.zankup.android.api.model.ActivitySummary
import com.zankup.android.api.model.SearchPage
import com.zankup.android.api.model.SearchTotals
import com.zankup.android.data.CacheKeys
import com.zankup.android.data.Loadable
import com.zankup.android.data.Prefetch
import com.zankup.android.data.Reachability
import com.zankup.android.data.Store
import com.zankup.android.data.UserSettings
import com.zankup.android.domain.Sport
import com.zankup.android.domain.Units
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Activities per page of the feed, and of the search. */
const val FEED_PAGE = 30
const val SEARCH_PAGE = 50

/** A list as the screen draws it: its cards, or the skeleton, the empty state or the failure. */
data class ListUi(
    val items: List<ActivitySummary> = emptyList(),
    val skeleton: Boolean = true,
    val empty: Boolean = false,
    /** Nothing to show because this failed. */
    val failure: ApiResult.Failure? = null,
    val loadingMore: Boolean = false,
    val totals: SearchTotals? = null
)

data class FeedUi(
    val request: SearchRequest = SearchRequest(),
    val list: ListUi = ListUi(),
    val refreshing: Boolean = false
) {
    val sport: Sport? get() = request.sport
    val filtersOn: Boolean get() = request.filters.on
    val searching: Boolean get() = request.searching
    val units: Units get() = request.units
}

/** The filters sheet while open: the filters being edited and how many activities they find. */
data class SheetUi(val draft: Filters, val count: Int?)

/**
 * Activities (OS-1131): the feed, newest first — its first page kept on the phone and refreshed on
 * open, the rest paged by cursor — or, once something is typed, a sport chosen or a filter set, the
 * search's results paged by offset, with the totals of every match.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel
@Inject
constructor(
    store: Store,
    settings: UserSettings,
    private val source: ApiSource,
    private val prefetch: Prefetch,
    private val reachability: Reachability
) : ViewModel() {
    private val first = store.query(CacheKeys.FEED_FIRST, ActivityPage.serializer()) { api ->
        call { api.service<ActivitiesApi>().listActivities(limit = FEED_PAGE) }
    }
    private val more = Pager<String>(viewModelScope)
    private val results = Pager<Int>(viewModelScope)

    private val typed = MutableStateFlow("")

    /** The search bar's text, as typed (the search follows it debounced). */
    val text: StateFlow<String> = typed.asStateFlow()

    private val sport = MutableStateFlow<Sport?>(null)
    private val filters = MutableStateFlow(Filters())
    private val draft = MutableStateFlow<Filters?>(null)
    private val pulling = MutableStateFlow(false)

    private val query = typed.debouncedQuery().stateIn(viewModelScope, SharingStarted.Eagerly, "")
    private val units = settings.units.stateIn(viewModelScope, SharingStarted.Eagerly, Units.METRIC)

    private val request: StateFlow<SearchRequest> =
        combine(query, sport, filters, units) { q, s, f, u -> SearchRequest(q, s, f, u) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SearchRequest())

    private val feedList = combine(first.state, more.paging) { kept, paged -> feedList(kept, paged) }

    private val resultsList = results.paging.map { paged ->
        ListUi(
            items = paged.items,
            skeleton = !paged.loaded && paged.failure == null,
            empty = paged.loaded && paged.items.isEmpty(),
            failure = paged.failure.takeIf { !paged.loaded },
            loadingMore = paged.loading && paged.loaded,
            totals = paged.totals
        )
    }

    val ui: StateFlow<FeedUi> =
        combine(request, feedList, resultsList, pulling) { r, feed, found, pull ->
            FeedUi(r, if (r.searching) found else feed, pull)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUi())

    private val draftCount =
        combine(draft, request) { d, r -> d?.let { paramsFor(r.copy(filters = it)) } }
            .distinctUntilChanged()
            .transformLatest { params ->
                if (params == null) {
                    emit(null)
                    return@transformLatest
                }
                delay(TYPING_DEBOUNCE_MS)
                val found = search(params, offset = 0, limit = 1)
                if (found is ApiResult.Ok) emit(found.value.totals.count)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sheet: StateFlow<SheetUi?> =
        combine(draft, draftCount) { d, count -> d?.let { SheetUi(it, count) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { first.refreshIfStale() }
        viewModelScope.launch {
            first.state.map { it.data?.nextCursor }.distinctUntilChanged().collect { cursor ->
                more.start(from = cursor, loadFirst = false, fetch = ::feedPage)
            }
        }
        viewModelScope.launch {
            request.collect { r ->
                if (r.searching) startResults(r) else results.clear()
            }
        }
    }

    fun type(text: String) {
        typed.value = text
    }

    fun clearText() {
        typed.value = ""
    }

    fun choose(sport: Sport?) {
        this.sport.value = sport
    }

    fun openFilters() {
        draft.value = filters.value
    }

    fun editFilters(change: (Filters) -> Filters) {
        draft.update { it?.let(change) }
    }

    fun applyFilters() {
        draft.value?.let { filters.value = it }
        draft.value = null
    }

    fun clearFilters() {
        filters.value = Filters()
        draft.value = null
    }

    fun closeFilters() {
        draft.value = null
    }

    /** Near the end of the list: the next page. */
    fun loadMore() {
        if (request.value.searching) results.loadMore() else more.loadMore()
    }

    /** "Try again" after a failure with nothing to show. */
    fun retry() {
        val r = request.value
        if (r.searching) results.loadMore() else viewModelScope.launch { first.refresh() }
    }

    /** Pull to refresh: the feed's first page again, or the search from its start. */
    fun refresh() {
        if (pulling.value) return
        pulling.value = true
        viewModelScope.launch {
            try {
                val r = request.value
                if (r.searching) startResults(r, keep = true)?.join() else first.refresh()
            } finally {
                pulling.value = false
            }
        }
    }

    /** The finger landed on a card: its activity starts loading before the screen opens. */
    fun pressing(id: Long) = prefetch.activity(id)

    private fun startResults(r: SearchRequest, keep: Boolean = false) =
        results.start(from = null, loadFirst = true, keep = keep) { offset ->
            when (val found = search(paramsFor(r), offset ?: 0, SEARCH_PAGE)) {
                is ApiResult.Ok -> ApiResult.Ok(Chunk(found.value.items, found.value.nextOffset, found.value.totals))
                is ApiResult.Failure -> found
            }
        }

    private fun paramsFor(r: SearchRequest) = searchParams(r, LocalDate.now(), ZoneId.systemDefault())

    private suspend fun feedPage(cursor: String?): ApiResult<Chunk<String>> {
        val api = source.api ?: return ApiResult.SignedOut
        val page = call { api.service<ActivitiesApi>().listActivities(cursor = cursor, limit = FEED_PAGE) }
        reachability.note(page)
        return when (page) {
            is ApiResult.Ok -> ApiResult.Ok(Chunk(page.value.items, page.value.nextCursor))
            is ApiResult.Failure -> page
        }
    }

    private suspend fun search(p: SearchParams, offset: Int, limit: Int): ApiResult<SearchPage> {
        val api = source.api ?: return ApiResult.SignedOut
        val found = call {
            api.service<ActivitiesApi>().searchActivities(
                q = p.q,
                sport = p.sport,
                from = p.from,
                minDistance = p.minDistance,
                maxDistance = p.maxDistance,
                minDuration = p.minDuration,
                maxDuration = p.maxDuration,
                minAscent = p.minAscent,
                withRecord = p.withRecord,
                withDescription = p.withDescription,
                withGps = p.withGps,
                sort = p.sort,
                order = p.order,
                offset = offset,
                limit = limit
            )
        }
        reachability.note(found)
        return found
    }
}

/** The kept first page and the pages after it, as one list; what was kept stays shown offline. */
internal fun feedList(kept: Loadable<ActivityPage>, paged: Paging<String>): ListUi {
    val firstPage = kept.data
    val items = (firstPage?.items.orEmpty() + paged.items).distinctBy { it.id }
    return ListUi(
        items = items,
        skeleton = kept.loading,
        empty = firstPage != null && items.isEmpty(),
        failure = kept.failure.takeIf { firstPage == null },
        loadingMore = paged.loading
    )
}
