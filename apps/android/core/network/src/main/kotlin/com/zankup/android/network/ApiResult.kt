package com.zankup.android.network

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response

/** What every call returns: the value, or one of the few ways it can fail that screens tell apart. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    sealed interface Failure : ApiResult<Nothing>

    /** No connection, or the instance could not be reached. */
    data class Offline(val cause: IOException) : Failure

    /** The session is over (refreshing failed): sign in again. */
    data object SignedOut : Failure

    /** The API answered with an error; [detail] is its message when it sent one (FastAPI's `detail`). */
    data class Http(val code: Int, val detail: String?) : Failure

    /** The answer could not be read (a bug, or an API newer than the app). */
    data class Unexpected(val cause: Throwable) : Failure
}

/** Runs a generated endpoint and turns its answer or its exception into an [ApiResult]. */
suspend fun <T : Any> call(request: suspend () -> Response<T>): ApiResult<T> = try {
    val response = request()
    val body = response.body()
    when {
        response.isSuccessful && body != null -> ApiResult.Ok(body)
        // 204 No Content: the generated endpoints say Response<Unit>.
        response.isSuccessful ->
            @Suppress("UNCHECKED_CAST")
            ApiResult.Ok(Unit as T)
        response.code() == 401 -> ApiResult.SignedOut
        else -> ApiResult.Http(response.code(), detail(response.errorBody()?.string()))
    }
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    ApiResult.Offline(e)
} catch (e: Exception) {
    ApiResult.Unexpected(e)
}

/** FastAPI's error message: `{"detail": "…"}`, or the first validation error's `msg`. */
internal fun detail(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return try {
        when (val detail = ZankupJson.parseToJsonElement(body).jsonObject["detail"]) {
            is JsonPrimitive -> detail.content
            is JsonArray -> (detail.firstOrNull() as? JsonObject)?.get("msg")?.jsonPrimitive?.content
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
