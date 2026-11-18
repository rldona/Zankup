package com.zankup.android.settings.more

import com.zankup.android.api.endpoints.KeysApi
import com.zankup.android.api.endpoints.SettingsApi
import com.zankup.android.api.model.InstanceOut
import com.zankup.android.api.model.KeyIn
import com.zankup.android.api.model.KeyStatus
import com.zankup.android.api.model.Preferences
import com.zankup.android.api.model.PreferencesUpdate
import com.zankup.android.api.model.SettingOut
import com.zankup.android.data.CacheKeys
import com.zankup.android.data.Loadable
import com.zankup.android.data.Reachability
import com.zankup.android.data.Store
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * The service keys (`GET /api/keys`) and the instance's settings (`GET /api/instance`), shared by the
 * sections. Unlike every other answer they are never kept on the phone (OS-1120): they live in memory
 * and go with the session.
 */
@Singleton
class ServiceSettings
@Inject
constructor(
    private val source: ApiSource,
    private val store: Store,
    private val reachability: Reachability
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val keysState = MutableStateFlow(Loadable<List<KeyStatus>>())
    private val instanceState = MutableStateFlow(Loadable<InstanceOut>())
    val keys: StateFlow<Loadable<List<KeyStatus>>> = keysState.asStateFlow()
    val instance: StateFlow<Loadable<InstanceOut>> = instanceState.asStateFlow()

    init {
        scope.launch {
            source.signedOut.collect { out ->
                if (out) {
                    keysState.value = Loadable()
                    instanceState.value = Loadable()
                }
            }
        }
    }

    suspend fun refreshKeys() {
        val api = source.api ?: return
        keysState.update { it.copy(refreshing = true) }
        val result = call { api.service<KeysApi>().listKeys() }
        reachability.note(result)
        keysState.value = when (result) {
            is ApiResult.Ok -> Loadable(result.value)
            is ApiResult.Failure -> keysState.value.copy(refreshing = false, failure = result)
        }
    }

    suspend fun refreshInstance() {
        val api = source.api ?: return
        instanceState.update { it.copy(refreshing = true) }
        val result = call { api.service<KeysApi>().getInstance() }
        reachability.note(result)
        instanceState.value = when (result) {
            is ApiResult.Ok -> Loadable(result.value)
            is ApiResult.Failure -> instanceState.value.copy(refreshing = false, failure = result)
        }
    }

    fun setting(name: String): SettingOut? = instanceState.value.data?.settings?.firstOrNull { it.name == name }

    /** Saves a key; the server tries it first and answers why when it refuses it. */
    suspend fun saveKey(name: KeyStatus.Name, value: String, expiresOn: String?): ApiResult<KeyStatus> {
        val api = source.api ?: return ApiResult.SignedOut
        val result = call { api.service<KeysApi>().saveKey(name.value, KeyIn(value.trim(), expiresOn)) }
        if (result is ApiResult.Ok) replaceKey(result.value)
        return result
    }

    suspend fun deleteKey(name: KeyStatus.Name): ApiResult<KeyStatus> {
        val api = source.api ?: return ApiResult.SignedOut
        val result = call { api.service<KeysApi>().deleteKey(name.value) }
        if (result is ApiResult.Ok) replaceKey(result.value)
        return result
    }

    private fun replaceKey(key: KeyStatus) {
        keysState.update { state ->
            state.copy(data = state.data.orEmpty().map { if (it.name == key.name) key else it })
        }
    }

    /** `PATCH /api/instance` with only the fields changed; the answer replaces what was shown. */
    suspend fun patchInstance(changes: Map<String, JsonElement>): ApiResult<InstanceOut> {
        val api = source.api ?: return ApiResult.SignedOut
        val result = call { api.service<KeysApi>().updateInstance(changes) }
        if (result is ApiResult.Ok) instanceState.value = Loadable(result.value)
        return result
    }

    /** `PATCH /api/settings`, keeping the answer where every screen reads the settings from. */
    suspend fun patchSettings(changes: PreferencesUpdate): ApiResult<Preferences> {
        val api = source.api ?: return ApiResult.SignedOut
        val result = call { api.service<SettingsApi>().updateSettings(changes) }
        if (result is ApiResult.Ok) store.put(CacheKeys.SETTINGS, Preferences.serializer(), result.value)
        return result
    }
}
