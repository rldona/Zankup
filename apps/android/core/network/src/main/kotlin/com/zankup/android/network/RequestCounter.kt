package com.zankup.android.network

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.HttpUrl
import okhttp3.Interceptor

/**
 * Debug only: counts the API's requests by method and path, so the ones the app asks twice (or more)
 * show up in logcat (`adb logcat -s ZankupNet`). It changes nothing on the wire.
 */
object RequestCounter {
    private val total = AtomicInteger()
    private val calls = ConcurrentHashMap<String, AtomicInteger>()

    /** One line per request: its place in the session, the same call repeated, and the session's totals. */
    fun note(method: String, url: HttpUrl): String {
        val query = url.query?.let { "?$it" }.orEmpty()
        val key = "$method ${url.encodedPath}$query"
        val seen = calls.getOrPut(key) { AtomicInteger() }.incrementAndGet()
        val n = total.incrementAndGet()
        val repeated = calls.values.sumOf { it.get() - 1 }
        return "#$n $key ($seen×) total=$n repeated=$repeated"
    }

    val interceptor =
        Interceptor { chain ->
            val request = chain.request()
            Log.i("ZankupNet", note(request.method, request.url))
            chain.proceed(request)
        }
}
