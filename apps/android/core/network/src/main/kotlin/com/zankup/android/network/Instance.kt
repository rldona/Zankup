package com.zankup.android.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The instance the app talks to, typed by the person on first launch: `api.zankup.com`, their own domain
 * or their `*.up.railway.app`. HTTPS is assumed when no scheme is given; plain HTTP is only allowed for
 * a local API in debug builds (`http://localhost:8000` with `adb reverse`).
 */
object Instance {
    /** The base URL Retrofit takes (always ending in `/`), or null when [input] is not a URL. */
    fun parse(input: String, allowHttp: Boolean = false): HttpUrl? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.scheme == "http" && !allowHttp) return null
        if (url.host.isEmpty() || (!url.host.contains('.') && url.host != "localhost")) return null
        // The API lives under /api/: a URL typed with it (…/api) is the same instance.
        val path = url.encodedPath.removeSuffix("/").removeSuffix("/api")
        return url.newBuilder().encodedPath("$path/").query(null).fragment(null).build()
    }
}
