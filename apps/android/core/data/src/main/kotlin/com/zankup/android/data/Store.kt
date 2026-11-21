package com.zankup.android.data

import com.zankup.android.network.ApiResult
import com.zankup.android.network.ZankupApi
import com.zankup.android.network.ZankupJson
import com.zankup.android.session.ApiSource
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer

/** What a screen shows: what was kept (or fetched), whether it is being refreshed, and the last failure. */
data class Loadable<T>(val data: T? = null, val refreshing: Boolean = false, val failure: ApiResult.Failure? = null) {
    /** Nothing to show yet and nothing went wrong: the skeleton. */
    val loading get() = data == null && failure == null
}

/**
 * One answer of the API kept on the phone: its state to show, and a way to fetch it again. The same key
 * gives the same [Query] to every screen, and two fetches of one key never overlap.
 */
class Query<T> internal constructor(val state: Flow<Loadable<T>>, private val fetch: suspend (Boolean) -> Unit) {
    /** Fetches it again, even if what is kept is recent: a pull to refresh, a change just made. */
    suspend fun refresh() = fetch(true)

    /** Fetches it only when what is kept is older than its time: a screen opening. */
    suspend fun refreshIfStale() = fetch(false)
}

/**
 * The phone's copy of the API (OS-1120). Every answer a screen reads is kept for a week and shown at once
 * — offline too — while a fresh one is fetched behind it; activity series have their own table, the
 * latest [KEPT_STREAMS] kept. Signing out deletes everything.
 *
 * The same key is one [Query] for the whole app (Home and the profile's avatar, the feed and the sync),
 * so a screen opening never asks again for what another one has just fetched: a fetch already running is
 * awaited, and one made within [ttlFor] is skipped. Only [Query.refresh] forces it.
 */
@Singleton
class Store
@Inject
constructor(
    private val database: ZankupDatabase,
    private val session: ApiSource,
    private val reachability: Reachability
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queries = ConcurrentHashMap<String, Query<*>>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val fetchedAt = ConcurrentHashMap<String, Long>()

    init {
        scope.launch { database.answers().forgetOlderThan(System.currentTimeMillis() - MAX_AGE_MS) }
        scope.launch {
            session.signedOut.collect { signedOut -> if (signedOut) clear() }
        }
    }

    private val api: ZankupApi? get() = session.api

    /**
     * The kept answer under [key], then [fetch]'s fresh one when it opens or is older than its time.
     * The [Query] is the same for every caller with that key.
     */
    fun <T> query(key: String, serializer: KSerializer<T>, fetch: suspend (ZankupApi) -> ApiResult<T>): Query<T> {
        @Suppress("UNCHECKED_CAST")
        return queries.computeIfAbsent(key) { make(key, serializer, fetch) } as Query<T>
    }

    private fun <T> make(
        key: String,
        serializer: KSerializer<T>,
        fetch: suspend (ZankupApi) -> ApiResult<T>
    ): Query<T> {
        val status = MutableStateFlow(Status())
        val kept =
            database.answers().observe(key).map { answer ->
                answer?.let { runCatching { ZankupJson.decodeFromString(serializer, it.json) }.getOrNull() }
            }
        val state = combine(kept, status) { data, now -> Loadable(data, now.refreshing, now.failure) }
        return Query(state) { force -> refresh(key, serializer, fetch, status, force) }
    }

    private suspend fun <T> refresh(
        key: String,
        serializer: KSerializer<T>,
        fetch: suspend (ZankupApi) -> ApiResult<T>,
        status: MutableStateFlow<Status>,
        force: Boolean
    ) {
        // When the caller asked, before waiting for the one already running: a fetch that ends after
        // this moment already answers the question.
        val asked = System.currentTimeMillis()
        locks.getOrPut(key) { Mutex() }.withLock {
            val last = fetchedAt[key] ?: database.answers().get(key)?.savedAt ?: 0L
            if (!force && asked - last < ttlFor(key)) return@withLock
            if (force && last > asked) return@withLock
            val api = api ?: return@withLock
            status.value = status.value.copy(refreshing = true)
            val result = fetch(api)
            reachability.note(result)
            when (result) {
                is ApiResult.Ok -> {
                    put(key, serializer, result.value)
                    status.value = Status()
                }
                is ApiResult.Failure -> status.value = Status(failure = result)
            }
        }
    }

    /** Keeps an answer under [key], as a screen or the background sync fetched it. */
    suspend fun <T> put(key: String, serializer: KSerializer<T>, value: T) {
        val now = System.currentTimeMillis()
        val json = withContext(Dispatchers.Default) { ZankupJson.encodeToString(serializer, value) }
        database.answers().put(Answer(key, json, now))
        fetchedAt[key] = now
    }

    /** An activity's series as kept, if they are. */
    suspend fun <T> streams(activityId: Long, serializer: KSerializer<T>): T? = withContext(Dispatchers.Default) {
        database.streams().get(activityId)?.let {
            runCatching { ZankupJson.decodeFromString(serializer, it.json) }.getOrNull()
        }
    }

    suspend fun <T> putStreams(activityId: Long, serializer: KSerializer<T>, value: T) {
        val now = System.currentTimeMillis()
        val json = withContext(Dispatchers.Default) { ZankupJson.encodeToString(serializer, value) }
        database.streams().put(Streams(activityId, json, now))
        database.streams().keepLatest(KEPT_STREAMS)
    }

    suspend fun hasStreams(activityId: Long) = database.streams().get(activityId) != null

    suspend fun clear() {
        database.answers().clear()
        database.streams().clear()
        fetchedAt.clear()
    }

    private data class Status(val refreshing: Boolean = false, val failure: ApiResult.Failure? = null)

    companion object {
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        const val KEPT_STREAMS = 60

        private const val MINUTE = 60_000L

        /**
         * How long a kept answer is trusted before a screen opening fetches it again: the feed and the
         * week change with every sync; the profile and the settings rarely. Pull to refresh skips this.
         */
        fun ttlFor(key: String): Long = when {
            key == "auth/me" || key == CacheKeys.SETTINGS -> 30 * MINUTE
            key == CacheKeys.FEED_FIRST || key.startsWith("week/") -> 2 * MINUTE
            key.startsWith("activities/") -> 10 * MINUTE
            key.startsWith("stats/") || key.startsWith("progress/") -> 10 * MINUTE
            key.startsWith("health/") || key.startsWith("wellness/") -> 10 * MINUTE
            key.startsWith("tools/events") || key.startsWith("tools/activities") -> 5 * MINUTE
            else -> 5 * MINUTE
        }
    }
}

/** The keys answers are kept under, shared by the screens and the background sync. */
object CacheKeys {
    /** The feed's first page (the rest is paged and not kept). */
    const val FEED_FIRST = "activities/first"

    fun week(day: String) = "week/$day"

    fun activity(id: Long) = "activities/$id"

    fun context(id: Long) = "activities/$id/context"

    const val SETTINGS = "settings"
}
