package com.zankup.android.data

import androidx.room.Room
import com.zankup.android.network.ApiResult
import com.zankup.android.network.MemoryTokenStore
import com.zankup.android.network.ZankupApi
import com.zankup.android.network.ZankupJson
import com.zankup.android.session.ApiSource
import java.io.IOException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoreTest {
    @Serializable
    data class Totals(val km: Double)

    private val database =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ZankupDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private val signedOut = MutableStateFlow(false)
    private val source =
        object : ApiSource {
            override val api = ZankupApi("https://zankup.test/".toHttpUrl(), MemoryTokenStore())
            override val signedOut = this@StoreTest.signedOut
        }
    private val reachability = Reachability()
    private val store = Store(database, source, reachability)

    @After fun close() = database.close()

    @Test
    fun showsWhatWasKeptAndThenTheFreshAnswer() = runBlocking {
        store.put("week", Totals.serializer(), Totals(10.0))
        val query = store.query("week", Totals.serializer()) { ApiResult.Ok(Totals(12.5)) }
        assertEquals(Totals(10.0), query.state.first().data)
        query.refresh()
        assertEquals(Totals(12.5), query.state.first { it.data == Totals(12.5) }.data)
        assertFalse(reachability.unreachable.value)
    }

    @Test
    fun offlineKeepsWhatWasKeptAndSaysSo() = runBlocking {
        store.put("week", Totals.serializer(), Totals(10.0))
        val query = store.query("week", Totals.serializer()) { ApiResult.Offline(IOException("no network")) }
        query.refresh()
        val state = query.state.first { it.failure != null }
        assertEquals(Totals(10.0), state.data)
        assertTrue(reachability.unreachable.value)
    }

    @Test
    fun aNotFoundIsNotBeingOffline() = runBlocking {
        val query = store.query("gone", Totals.serializer()) { ApiResult.Http(404, "Not found") }
        query.refresh()
        assertNull(query.state.first { it.failure != null }.data)
        assertFalse(reachability.unreachable.value)
    }

    @Test
    fun keepsTheLatestSeriesOnly() = runBlocking {
        repeat(Store.KEPT_STREAMS + 5) { id ->
            store.putStreams(id.toLong(), Totals.serializer(), Totals(id.toDouble()))
            Thread.sleep(1)
        }
        assertNull(store.streams(0, Totals.serializer()))
        assertEquals(Totals(64.0), store.streams(64, Totals.serializer()))
    }

    @Test
    fun signingOutForgetsEverything() = runBlocking {
        store.put("week", Totals.serializer(), Totals(10.0))
        store.putStreams(1, Totals.serializer(), Totals(1.0))
        signedOut.value = true
        val query = store.query("week", Totals.serializer()) { ApiResult.Ok(Totals(0.0)) }
        assertNull(query.state.first { it.data == null }.data)
        assertNull(store.streams(1, Totals.serializer()))
    }

    @Test
    fun oneKeyIsOneQueryAndOneFetch() = runBlocking {
        var calls = 0
        val first = store.query("week", Totals.serializer()) {
            calls++
            delay(100)
            ApiResult.Ok(Totals(12.0))
        }
        val second = store.query("week", Totals.serializer()) {
            calls++
            ApiResult.Ok(Totals(99.0))
        }
        assertSame(first, second)
        coroutineScope {
            launch { first.refresh() }
            launch { second.refresh() }
        }
        assertEquals(1, calls)
        assertEquals(Totals(12.0), first.state.first { it.data == Totals(12.0) }.data)
    }

    @Test
    fun aFreshAnswerIsNotFetchedWhenAScreenOpens() = runBlocking {
        var calls = 0
        store.put("week", Totals.serializer(), Totals(10.0))
        val query = store.query("week", Totals.serializer()) {
            calls++
            ApiResult.Ok(Totals(12.0))
        }
        query.refreshIfStale()
        assertEquals(0, calls)
        query.refresh()
        assertEquals(1, calls)
    }

    @Test
    fun anOldAnswerIsFetchedWhenAScreenOpens() = runBlocking {
        var calls = 0
        val old = System.currentTimeMillis() - 11 * 60_000L
        database.answers().put(Answer("week", ZankupJson.encodeToString(Totals.serializer(), Totals(10.0)), old))
        val query = store.query("week", Totals.serializer()) {
            calls++
            ApiResult.Ok(Totals(12.0))
        }
        query.refreshIfStale()
        assertEquals(1, calls)
    }

    @Test
    fun theFeedGoesStaleSoonerThanTheProfile() {
        assertEquals(2 * 60_000L, Store.ttlFor(CacheKeys.FEED_FIRST))
        assertTrue(Store.ttlFor("auth/me") > Store.ttlFor("stats/estimates"))
        assertEquals(5 * 60_000L, Store.ttlFor("something/else"))
    }
}
