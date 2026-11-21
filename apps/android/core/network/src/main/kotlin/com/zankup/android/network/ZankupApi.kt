package com.zankup.android.network

import com.zankup.android.api.model.AppTokens
import com.zankup.android.api.model.RefreshRequest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

/** How the app reads the API's JSON: fields it does not know yet are ignored, so a newer API never breaks it. */
val ZankupJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

/**
 * The API of one instance. `api.service<ActivitiesApi>()` gives any generated endpoint interface; every
 * request carries the access token, and a 401 refreshes it once (`POST /api/auth/refresh` with
 * `rotate: false`, since the refresh token lives under the fingerprint) before giving up.
 */
class ZankupApi(
    val baseUrl: HttpUrl,
    private val tokens: TokenStore,
    debug: Boolean = false,
    /** Called when the session cannot be renewed, so the app returns to sign-in. */
    private val onSignedOut: () -> Unit = {},
    /** The HTTP cache (the app's, in its own directory): a 304 revalidates without the body. */
    httpCache: Cache? = null
) {
    private val plain =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(ClientHeader)
            .apply {
                if (httpCache != null) cache(httpCache)
                if (debug) {
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                    addInterceptor(RequestCounter.interceptor)
                }
            }
            .build()

    /** The client every endpoint uses: [plain] with the token and the refresh. */
    val http: OkHttpClient =
        plain
            .newBuilder()
            .addInterceptor(BearerInterceptor())
            .authenticator(RefreshAuthenticator())
            .build()

    val retrofit: Retrofit =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(http)
            .addConverterFactory(EnumQueries)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(ZankupJson.asConverterFactory("application/json".toMediaType()))
            .build()

    inline fun <reified T> service(): T = retrofit.create(T::class.java)

    private inner class BearerInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.header("Authorization") != null) return chain.proceed(request)
            // The session keeps the access token in memory: no need to suspend a network thread for it.
            val access = tokens.peek()?.access?.takeIf { it.isNotEmpty() } ?: return chain.proceed(request)
            return chain.proceed(request.newBuilder().header("Authorization", "Bearer $access").build())
        }
    }

    private inner class RefreshAuthenticator : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            // Only once per request, and never for the refresh itself.
            if (response.priorResponse != null ||
                response.request.url.encodedPath.endsWith("/api/auth/refresh")
            ) {
                return null
            }
            val sent = response.request.header("Authorization")
            // Another request may have renewed it already: retry with that one instead of queueing.
            tokens.peek()?.let { current ->
                if (sent != null && sent != "Bearer ${current.access}") {
                    return response.request
                        .newBuilder()
                        .header("Authorization", "Bearer ${current.access}")
                        .build()
                }
            }
            val renewed = synchronized(this) { runBlocking { renew(sent) } }
            if (renewed == null) {
                onSignedOut()
                return null
            }
            return response.request
                .newBuilder()
                .header("Authorization", "Bearer ${renewed.access}")
                .build()
        }

        /** A new access token, unless another request renewed it meanwhile (then that one is used). */
        private suspend fun renew(sent: String?): Tokens? {
            val current = tokens.read() ?: return null
            // Another request renewed it meanwhile: use that one.
            if (current.access.isNotEmpty() && sent != "Bearer ${current.access}") return current
            val body = ZankupJson.encodeToString(
                RefreshRequest.serializer(),
                RefreshRequest(current.refresh, rotate = false)
            )
            val request =
                Request
                    .Builder()
                    .url(baseUrl.resolve("api/auth/refresh")!!)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
            return try {
                plain.newCall(request).execute().use { answer ->
                    if (!answer.isSuccessful) {
                        if (answer.code in 400..499) tokens.clear()
                        return null
                    }
                    val fresh = ZankupJson.decodeFromString(AppTokens.serializer(), answer.body.string())
                    Tokens(fresh.accessToken, fresh.refreshToken).also { tokens.save(it) }
                }
            } catch (_: java.io.IOException) {
                null
            }
        }
    }

    private object ClientHeader : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
            chain
                .request()
                .newBuilder()
                .header("User-Agent", "Zankup-Android")
                .build()
        )
    }
}
