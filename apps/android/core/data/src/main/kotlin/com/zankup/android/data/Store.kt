package com.zankup.android.data

import com.zankup.android.network.ApiResult
import com.zankup.android.network.ZankupApi
import com.zankup.android.network.ZankupJson
import com.zankup.android.session.ApiSource
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
import kotlinx.serialization.KSerializer

/** What a screen shows: what was kept (or fetched), whether it is being refreshed, and the last failure. */
data class Loadable<T>(val data: T? = null, val refreshing: Boolean = false, val failure: ApiResult.Failure? = null) {
    /** Nothing to show yet and nothing went wrong: the skeleton. */
    val loading get() = data == null && failure == null
}

/** One answer of the API kept on the phone: its state to show, and a way to fetch it again. */
class Query<T> internal constructor(val state: Flow<Loadable<T>>, private val fetch: suspend () -> Unit) {
    suspend fun refresh() = fetch()
}

/**
 * The phone's copy of the API (OS-1120). Every answer a screen reads is kept for a week and shown at once
 * — offline too — while a fresh one is fetched behind it; activity series have their own table, the
 * latest [KEPT_STREAMS] kept. Signing out deletes everything.
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

    init {
        scope.launch { database.answers().forgetOlderThan(System.currentTimeMillis() - MAX_AGE_MS) }
        scope.launch {
            session.signedOut.collect { signedOut -> if (signedOut) clear() }
        }
    }

    private val api: ZankupApi? get() = session.api

    /**
     * The kept answer under [key], then [fetch]'s fresh one. A screen collects [Query.state] and calls
     * [Query.refresh] when it opens or after a sync; nothing is fetched while signed out.
     */
    fun <T> query(key: String, serializer: KSerializer<T>, fetch: suspend (ZankupApi) -> ApiResult<T>): Query<T> {
        val status = MutableStateFlow(Status())
        val kept =
            database.answers().observe(key).map { answer ->
                answer?.let { runCatching { ZankupJson.decodeFromString(serializer, it.json) }.getOrNull() }
            }
        val state =
            combine(kept, status) { data, now -> Loadable(data, now.refreshing, now.failure) }
        return Query(state) {
            val api = api ?: return@Query
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
    suspend fun <T> put(key: String, serializer: KSerializer<T>, value: T) = database.answers().put(
        Answer(key, ZankupJson.encodeToString(serializer, value), System.currentTimeMillis())
    )

    /** An activity's series as kept, if they are. */
    suspend fun <T> streams(activityId: Long, serializer: KSerializer<T>): T? =
        database.streams().get(activityId)?.let {
            runCatching { ZankupJson.decodeFromString(serializer, it.json) }.getOrNull()
        }

    suspend fun <T> putStreams(activityId: Long, serializer: KSerializer<T>, value: T) {
        database.streams().put(
            Streams(activityId, ZankupJson.encodeToString(serializer, value), System.currentTimeMillis())
        )
        database.streams().keepLatest(KEPT_STREAMS)
    }

    suspend fun hasStreams(activityId: Long) = database.streams().get(activityId) != null

    suspend fun clear() {
        database.answers().clear()
        database.streams().clear()
    }

    private data class Status(val refreshing: Boolean = false, val failure: ApiResult.Failure? = null)

    companion object {
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        const val KEPT_STREAMS = 60
    }
}

/** The keys answers are kept under, shared by the screens and the background sync. */
object CacheKeys {
    /** The feed's first page (the rest is paged and not kept). */
    const val FEED_FIRST = "activities/first"

    fun week(day: String) = "week/$day"

    fun activity(id: Long) = "activities/$id"
}
