package com.zankup.android.feed

import com.zankup.android.api.model.ActivityPage
import com.zankup.android.api.model.ActivitySummary
import com.zankup.android.api.model.SearchTotals
import com.zankup.android.data.Loadable
import com.zankup.android.network.ApiResult
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun activity(id: Int) = ActivitySummary(
    id = id,
    name = "Run $id",
    sport = "run",
    subSport = null,
    source = "garmin",
    startUtc = "2026-11-18T07:30:00Z",
    timezone = null,
    durationS = 1800.0,
    movingS = 1750.0,
    distanceM = 5000.0,
    ascentM = 20.0,
    avgHr = 150,
    avgPower = null,
    hasGps = true,
    route = null
)

private fun ids(paging: Paging<*>) = paging.items.map { it.id }

@OptIn(ExperimentalCoroutinesApi::class)
class PagerTest {
    @Test
    fun cursorPagesFollowTheKeptFirstOne() = runTest {
        val pager = Pager<String>(backgroundScope)
        val asked = mutableListOf<String?>()
        pager.start(from = "c1", loadFirst = false) { cursor ->
            asked += cursor
            when (cursor) {
                "c1" -> ApiResult.Ok(Chunk(listOf(activity(3), activity(4)), "c2"))
                else -> ApiResult.Ok(Chunk(listOf(activity(4), activity(5)), null))
            }
        }
        assertTrue(pager.paging.value.loaded)
        assertTrue(pager.paging.value.items.isEmpty())

        pager.loadMore()
        runCurrent()
        assertEquals(listOf(3, 4), ids(pager.paging.value))
        pager.loadMore()
        runCurrent()
        // Activity 4 came twice (a new one shifted the pages): listed once.
        assertEquals(listOf(3, 4, 5), ids(pager.paging.value))
        assertTrue(pager.paging.value.end)

        pager.loadMore()
        runCurrent()
        assertEquals(listOf("c1", "c2"), asked)
    }

    @Test
    fun noCursorMeansTheFeedEndsAtItsFirstPage() = runTest {
        val pager = Pager<String>(backgroundScope)
        var calls = 0
        pager.start(from = null, loadFirst = false) {
            calls++
            ApiResult.Ok(Chunk(emptyList(), null))
        }
        pager.loadMore()
        runCurrent()
        assertEquals(0, calls)
    }

    @Test
    fun offsetPagesKeepTheFirstTotals() = runTest {
        val pager = Pager<Int>(backgroundScope)
        val totals = SearchTotals(count = 3, distanceM = 15_000.0, movingS = 5_000.0, ascentM = 60.0)
        val asked = mutableListOf<Int?>()
        pager.start(from = null, loadFirst = true) { offset ->
            asked += offset
            if (offset == null) {
                ApiResult.Ok(Chunk(listOf(activity(1), activity(2)), 2, totals))
            } else {
                ApiResult.Ok(Chunk(listOf(activity(3)), null, totals.copy(count = 99)))
            }
        }
        assertFalse(pager.paging.value.loaded)
        runCurrent()
        pager.loadMore()
        runCurrent()
        assertEquals(listOf(1, 2, 3), ids(pager.paging.value))
        assertEquals(3, pager.paging.value.totals?.count)
        assertEquals(listOf(null, 2), asked)
    }

    @Test
    fun aNewSearchDropsTheOldOne() = runTest {
        val pager = Pager<Int>(backgroundScope)
        val slow = CompletableDeferred<ApiResult<Chunk<Int>>>()
        pager.start(from = null, loadFirst = true) { slow.await() }
        runCurrent()
        pager.start(from = null, loadFirst = true) { ApiResult.Ok(Chunk(listOf(activity(7)), null)) }
        runCurrent()
        slow.complete(ApiResult.Ok(Chunk(listOf(activity(1)), null)))
        runCurrent()
        assertEquals(listOf(7), ids(pager.paging.value))
    }

    @Test
    fun aFailureIsKeptAndRetried() = runTest {
        val pager = Pager<Int>(backgroundScope)
        var fail = true
        pager.start(from = null, loadFirst = true) {
            if (fail) ApiResult.Offline(IOException()) else ApiResult.Ok(Chunk(listOf(activity(1)), null))
        }
        runCurrent()
        assertTrue(pager.paging.value.failure is ApiResult.Offline)
        assertFalse(pager.paging.value.loaded)
        fail = false
        pager.loadMore()
        runCurrent()
        assertNull(pager.paging.value.failure)
        assertEquals(listOf(1), ids(pager.paging.value))
    }

    @Test
    fun refreshingKeepsWhatIsShownUntilTheNewPage() = runTest {
        val pager = Pager<Int>(backgroundScope)
        pager.start(from = null, loadFirst = true) { ApiResult.Ok(Chunk(listOf(activity(1)), null)) }
        runCurrent()
        val next = CompletableDeferred<ApiResult<Chunk<Int>>>()
        pager.start(from = null, loadFirst = true, keep = true) { next.await() }
        runCurrent()
        assertEquals(listOf(1), ids(pager.paging.value))
        next.complete(ApiResult.Ok(Chunk(listOf(activity(2)), null)))
        runCurrent()
        assertEquals(listOf(2), ids(pager.paging.value))
    }
}

class FeedListTest {
    @Test
    fun skeletonOnlyWhileNothingIsKept() {
        assertTrue(feedList(Loadable(), Paging()).skeleton)
        val kept = Loadable(ActivityPage(listOf(activity(1)), "c"))
        assertFalse(feedList(kept, Paging()).skeleton)
    }

    @Test
    fun offlineKeepsWhatWasKept() {
        val failure = ApiResult.Offline(IOException())
        val kept = Loadable(ActivityPage(listOf(activity(1), activity(2)), "c"), failure = failure)
        val list = feedList(kept, Paging(items = listOf(activity(2), activity(3))))
        assertNull(list.failure)
        assertEquals(listOf(1, 2, 3), list.items.map { it.id })
        assertEquals(failure, feedList(Loadable(failure = failure), Paging()).failure)
    }

    @Test
    fun emptyOnlyOnceTheFirstPageSaysSo() {
        assertFalse(feedList(Loadable(), Paging()).empty)
        assertTrue(feedList(Loadable(ActivityPage(emptyList(), null)), Paging()).empty)
    }
}
