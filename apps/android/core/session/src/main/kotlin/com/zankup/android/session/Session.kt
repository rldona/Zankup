package com.zankup.android.session

import android.content.Context
import android.os.Build
import android.security.keystore.KeyPermanentlyInvalidatedException
import com.zankup.android.api.endpoints.AuthApi
import com.zankup.android.network.Instance
import com.zankup.android.network.TokenStore
import com.zankup.android.network.Tokens
import com.zankup.android.network.ZankupApi
import com.zankup.android.network.call
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl

/** Where the app is: deciding, signed out, locked behind the fingerprint, or in. */
sealed interface SessionState {
    data object Starting : SessionState

    data object SignedOut : SessionState

    /** The token is on the phone but the fingerprint was not given (cancelled, timed out): "Unlock". */
    data object Locked : SessionState

    data class SignedIn(val api: ZankupApi) : SessionState
}

/** What the data layer needs from the session: the signed-in client, and when everything must be forgotten. */
interface ApiSource {
    /** The client while signed in; null otherwise. */
    val api: ZankupApi?

    /** True once signed out (the kept data goes with it). */
    val signedOut: Flow<Boolean>
}

/**
 * The session (OS-1111). The refresh token is the only secret kept: sealed by a Keystore key that the
 * fingerprint releases ([Protection.STRONG]) or, without strong biometrics, by a plain Keystore key. The
 * access token lives in memory; the API client gets a fresh one with the first request after unlocking.
 * The refresh token is not rotated (`rotate: false`), since sealing a new one would need the sensor again.
 */
@Singleton
class Session
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: SessionPrefs,
    @param:Named("debug") private val debug: Boolean
) : ApiSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = MutableStateFlow<SessionState>(SessionState.Starting)
    private val mutex = Mutex()
    val current: StateFlow<SessionState> = state.asStateFlow()

    override val api: ZankupApi? get() = (state.value as? SessionState.SignedIn)?.api

    override val signedOut: Flow<Boolean> = state.map { it is SessionState.SignedOut }.distinctUntilChanged()

    /** The tokens in memory, as the API client reads and renews them. */
    private val memory =
        object : TokenStore {
            @Volatile var tokens: Tokens? = null

            override suspend fun read() = tokens

            override suspend fun save(tokens: Tokens) {
                this.tokens = tokens
            }

            override suspend fun clear() {
                tokens = null
                scope.launch { forget() }
            }
        }

    val deviceName: String
        get() = listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL).joinToString(" ").trim()

    /** Plain HTTP only for a local API in debug builds. */
    fun parseInstance(input: String): HttpUrl? = Instance.parse(input, allowHttp = debug)

    /** A client with no session, for the steps before signing in. */
    fun anonymous(instance: HttpUrl) = ZankupApi(instance, TokenStoreNone, debug)

    suspend fun savedInstance(): HttpUrl? = prefs.instance()?.let(::parseInstance)

    /** Opening the app: decides between signed out, locked and in, asking for the fingerprint if needed. */
    suspend fun unlock(prompter: Prompter): SessionState = mutex.withLock {
        val instance = savedInstance()
        val sealed = prefs.sealed()
        if (instance == null || sealed == null) return@withLock set(SessionState.SignedOut)
        val refresh =
            when (sealed.protection) {
                Protection.STRONG -> {
                    val cipher =
                        try {
                            Vault.cipherFor(Protection.STRONG, sealed.iv)
                        } catch (_: KeyPermanentlyInvalidatedException) {
                            // The enrolled fingerprints changed: Android destroyed the key.
                            forgetLocked()
                            return@withLock set(SessionState.SignedOut)
                        } catch (_: Exception) {
                            return@withLock set(SessionState.Locked)
                        }
                    when (val result = prompter.strong(cipher)) {
                        is PromptResult.Failed -> return@withLock set(SessionState.Locked)
                        is PromptResult.Passed ->
                            runCatching { Vault.open(result.cipher ?: cipher, sealed) }.getOrNull()
                                ?: return@withLock set(SessionState.Locked)
                    }
                }
                Protection.DEVICE -> {
                    if (prefs.biometricLock() &&
                        prefs.weakLock() &&
                        BiometricLevel.of(context) != BiometricLevel.NONE
                    ) {
                        if (prompter.weak() is PromptResult.Failed) return@withLock set(SessionState.Locked)
                    }
                    runCatching {
                        Vault.open(Vault.cipherFor(Protection.DEVICE, sealed.iv), sealed)
                    }.getOrNull()
                        ?: run {
                            forgetLocked()
                            return@withLock set(SessionState.SignedOut)
                        }
                }
            }
        memory.tokens = Tokens(access = "", refresh = refresh)
        set(SessionState.SignedIn(client(instance)))
    }

    /**
     * After any way of signing in: keeps the instance, seals the refresh token (asking for the fingerprint
     * when it protects it) and opens the app. If the fingerprint is not given, the session is used now
     * but not kept: the next opening asks to sign in again. Returns whether it was kept.
     */
    suspend fun signedIn(instance: HttpUrl, access: String, refresh: String, prompter: Prompter): Boolean =
        mutex.withLock {
            prefs.setInstance(instance.toString())
            val protection =
                if (prefs.biometricLock() &&
                    !prefs.weakLock() &&
                    BiometricLevel.of(context) == BiometricLevel.STRONG
                ) {
                    Protection.STRONG
                } else {
                    Protection.DEVICE
                }
            val kept =
                try {
                    Vault.destroy()
                    val cipher = Vault.cipherFor(protection)
                    val ready =
                        if (protection == Protection.STRONG) {
                            (prompter.strong(cipher) as? PromptResult.Passed)?.cipher
                                ?: (null.also { prefs.setSealed(null) })
                        } else {
                            cipher
                        }
                    ready?.let { prefs.setSealed(Vault.seal(it, refresh, protection)) } != null
                } catch (_: Exception) {
                    prefs.setSealed(null)
                    false
                }
            memory.tokens = Tokens(access, refresh)
            set(SessionState.SignedIn(client(instance)))
            kept
        }

    /**
     * A client for work done in the background (the sync, the widget), with no one to touch the sensor:
     * only when the refresh token is sealed without the fingerprint; null otherwise. It shares nothing
     * with the open app's session and never changes what is stored (`rotate: false`).
     */
    suspend fun backgroundApi(): ZankupApi? {
        val instance = savedInstance() ?: return null
        val sealed = prefs.sealed()?.takeIf { it.protection == Protection.DEVICE } ?: return null
        val refresh =
            runCatching { Vault.open(Vault.cipherFor(Protection.DEVICE, sealed.iv), sealed) }.getOrNull() ?: return null
        return ZankupApi(instance, com.zankup.android.network.MemoryTokenStore(Tokens("", refresh)), debug)
    }

    /** Signs this phone out on the server too (its device row goes), then forgets everything here. */
    suspend fun signOut() {
        (state.value as? SessionState.SignedIn)?.api?.let { api -> call { api.service<AuthApi>().logout() } }
        forget()
    }

    private suspend fun forget() = mutex.withLock {
        forgetLocked()
        set(SessionState.SignedOut)
    }

    private suspend fun forgetLocked() {
        memory.tokens = null
        prefs.setSealed(null)
        runCatching { Vault.destroy() }
    }

    private fun client(instance: HttpUrl) = ZankupApi(instance, memory, debug) { scope.launch { forget() } }

    private fun set(next: SessionState): SessionState {
        state.value = next
        return next
    }

    private object TokenStoreNone : TokenStore {
        override suspend fun read(): Tokens? = null

        override suspend fun save(tokens: Tokens) = Unit

        override suspend fun clear() = Unit
    }
}
