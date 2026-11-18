package com.zankup.android.network

/** The session's tokens: the short-lived access token and the refresh token that renews it. */
data class Tokens(val access: String, val refresh: String)

/**
 * Where the session lives. The real one (OS-1111) keeps the refresh token under the fingerprint, in the
 * Keystore; tests use [MemoryTokenStore].
 */
interface TokenStore {
    suspend fun read(): Tokens?

    suspend fun save(tokens: Tokens)

    suspend fun clear()
}

class MemoryTokenStore(private var tokens: Tokens? = null) : TokenStore {
    override suspend fun read() = tokens

    override suspend fun save(tokens: Tokens) {
        this.tokens = tokens
    }

    override suspend fun clear() {
        tokens = null
    }
}
