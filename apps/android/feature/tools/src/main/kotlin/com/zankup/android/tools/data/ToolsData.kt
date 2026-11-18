package com.zankup.android.tools.data

import com.zankup.android.api.endpoints.ActivitiesApi
import com.zankup.android.api.endpoints.AuthApi
import com.zankup.android.api.endpoints.LibraryApi
import com.zankup.android.api.endpoints.ProfileApi
import com.zankup.android.api.endpoints.StatsApi
import com.zankup.android.api.endpoints.TransfersApi
import com.zankup.android.api.model.ActivityPage
import com.zankup.android.api.model.EstimateOut
import com.zankup.android.api.model.FitnessDay
import com.zankup.android.api.model.GearOut
import com.zankup.android.api.model.ImportCreate
import com.zankup.android.api.model.ImportOut
import com.zankup.android.api.model.Me
import com.zankup.android.api.model.Week
import com.zankup.android.data.CacheKeys
import com.zankup.android.data.Store
import com.zankup.android.network.ApiResult
import com.zankup.android.network.ZankupApi
import com.zankup.android.network.ZankupJson
import com.zankup.android.network.call
import com.zankup.android.tools.partUrl
import com.zankup.android.tools.parts
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The key gear is kept under by the activity's edit sheet (feature/activity), refreshed after a change. */
private const val ACTIVITY_GEAR_KEY = "gear"

/** A file being uploaded, read part by part so a large export never sits whole in memory. */
internal interface PartSource {
    val name: String
    val size: Long

    /** Reads [length] bytes from [offset]. */
    suspend fun read(offset: Long, length: Int): ByteArray
}

/** How many times a part is sent before the upload gives up, waiting a second more each time. */
internal const val PART_ATTEMPTS = 3

/**
 * Uploading an import (OS-1163), as the React Native app does: `POST /api/imports`, then each part
 * `PUT {url}/{index}?token=` with its raw bytes (outside the OpenAPI document), each retried, then
 * `POST /api/imports/{id}/finish`. The parts carry their own token, so they go without the session's.
 */
internal class Uploader(private val api: ZankupApi) {
    private val client: OkHttpClient =
        api.http
            .newBuilder()
            .apply {
                interceptors().clear()
                networkInterceptors().clear()
            }.authenticator(Authenticator.NONE)
            .writeTimeout(2, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .build()

    suspend fun upload(source: PartSource, onProgress: (Long) -> Unit): ApiResult<ImportOut> {
        if (source.size > Int.MAX_VALUE) return ApiResult.Http(413, null)
        val transfers = api.service<TransfersApi>()
        val created = call { transfers.createImport(ImportCreate(source.name, source.size.toInt())) }
        if (created !is ApiResult.Ok) return created as ApiResult.Failure
        val target = created.value.upload
        for (part in parts(source.size, target.partSize)) {
            val bytes = source.read(part.offset, part.length)
            val url = partUrl(target.url, api.baseUrl.toString(), part.index, target.token)
            var attempt = 1
            while (true) {
                val sent = put(url, bytes)
                if (sent is ApiResult.Ok) break
                if (attempt >= PART_ATTEMPTS) return sent as ApiResult.Failure
                delay(1000L * attempt)
                attempt++
            }
            onProgress(minOf(source.size, part.offset + part.length))
        }
        return call { transfers.finishImport(created.value.import.id) }
    }

    private suspend fun put(url: String, bytes: ByteArray): ApiResult<Unit> {
        val body = bytes.toRequestBody(OCTET_STREAM)
        val request = Request.Builder().url(url).header("User-Agent", "Zankup-Android").put(body).build()
        return send(client, request).let { if (it is ApiResult.Ok) ApiResult.Ok(Unit) else it as ApiResult.Failure }
    }
}

private val OCTET_STREAM = "application/octet-stream".toMediaType()
private val JSON = "application/json".toMediaType()

/** Runs a request by hand: the body's text, or the failure as [call] would say it. */
private suspend fun send(client: OkHttpClient, request: Request): ApiResult<String> = withContext(Dispatchers.IO) {
    try {
        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> ApiResult.Ok(response.body.string())
                response.code == 401 -> ApiResult.SignedOut
                else -> ApiResult.Http(response.code, null, response.header("Retry-After"))
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        ApiResult.Offline(e)
    }
}

/**
 * `PATCH /api/gear/{id}` with the JSON as written, nulls included: the generated client leaves nulls
 * out (`explicitNulls = false`), so it could never clear a brand or a limit.
 */
internal suspend fun patchGear(api: ZankupApi, id: Int, body: JsonObject): ApiResult<GearOut> {
    val request =
        Request
            .Builder()
            .url(api.baseUrl.resolve("api/gear/$id")!!)
            .patch(body.toString().toRequestBody(JSON))
            .build()
    return when (val sent = send(api.http, request)) {
        is ApiResult.Ok -> runCatching { ApiResult.Ok(ZankupJson.decodeFromString(GearOut.serializer(), sent.value)) }
            .getOrElse { ApiResult.Unexpected(it) }
        is ApiResult.Failure -> sent
    }
}

/** After gear changed: the activity's gear picker keeps its own copy. */
internal suspend fun refreshActivityGear(store: Store) {
    store.query(ACTIVITY_GEAR_KEY, ListSerializer(GearOut.serializer())) { api ->
        call { api.service<LibraryApi>().listGear() }
    }.refresh()
}

/**
 * After a sync or an import brought activities: the feed's first page, this week, the profile and the
 * estimates and fitness Home shows, under the keys those screens keep them.
 */
internal suspend fun refreshAfterNewActivities(store: Store) {
    val today = LocalDate.now().toString()
    store.query(CacheKeys.FEED_FIRST, ActivityPage.serializer()) { api ->
        call { api.service<ActivitiesApi>().listActivities() }
    }.refresh()
    store.query(CacheKeys.week(today), Week.serializer()) { api ->
        call { api.service<ProfileApi>().getWeek(today) }
    }.refresh()
    store.query("auth/me", Me.serializer()) { api -> call { api.service<AuthApi>().getMe() } }.refresh()
    store.query("stats/estimates", ListSerializer(EstimateOut.serializer())) { api ->
        call { api.service<StatsApi>().getEstimates() }
    }.refresh()
    store.query("stats/fitness", ListSerializer(FitnessDay.serializer())) { api ->
        call { api.service<StatsApi>().getFitness() }
    }.refresh()
}
