package com.zankup.android.settings.more

import androidx.lifecycle.ViewModel
import com.zankup.android.api.endpoints.AiApi
import com.zankup.android.api.model.AiPreferences
import com.zankup.android.api.model.AiStatus
import com.zankup.android.api.model.ApiTokenCreated
import com.zankup.android.api.model.ApiTokenOut
import com.zankup.android.api.model.ApiTokenRequest
import com.zankup.android.api.model.KeyStatus
import com.zankup.android.api.model.PreferencesUpdate
import com.zankup.android.data.Store
import com.zankup.android.data.UserSettings
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement

/** Settings › AI assistant (OS-1161): Anthropic's key, the Coach's model and budget, the AI switches and MCP tokens. */
@HiltViewModel
class AiModel
@Inject
constructor(
    store: Store,
    private val source: ApiSource,
    val service: ServiceSettings,
    userSettings: UserSettings
) : ViewModel() {
    val settings = userSettings.query

    val status = store.query("settingsm/ai/status", AiStatus.serializer()) { api ->
        call { api.service<AiApi>().getAiStatus() }
    }

    val tokens = store.query("settingsm/ai/tokens", ListSerializer(ApiTokenOut.serializer())) { api ->
        call { api.service<AiApi>().listApiTokens() }
    }

    /** The instance's address, which the MCP URL is built on. */
    val instanceUrl: String? get() = source.api?.baseUrl?.toString()?.trimEnd('/')

    /** The MCP server's address: the one a new token came with, else the instance's `/api/mcp`. */
    fun mcpUrl(created: String?): String {
        val base = source.api?.baseUrl ?: return created.orEmpty()
        return (created?.let(base::resolve) ?: base.resolve("api/mcp"))?.toString() ?: created.orEmpty()
    }

    suspend fun refresh() {
        service.refreshKeys()
        service.refreshInstance()
        settings.refresh()
        status.refresh()
        tokens.refresh()
    }

    /** A key changes whether the Coach is available. */
    suspend fun saveKey(value: String, expiresOn: String?) =
        service.saveKey(KeyStatus.Name.ANTHROPIC, value, expiresOn).also { status.refresh() }

    suspend fun deleteKey() = service.deleteKey(KeyStatus.Name.ANTHROPIC).also { status.refresh() }

    suspend fun saveCoach(changes: Map<String, JsonElement>) = service.patchInstance(changes).also { status.refresh() }

    suspend fun saveAi(prefs: AiPreferences) =
        service.patchSettings(PreferencesUpdate(ai = prefs)).also { status.refresh() }

    suspend fun createToken(name: String): ApiResult<ApiTokenCreated> {
        val api = source.api ?: return ApiResult.SignedOut
        return call { api.service<AiApi>().createApiToken(ApiTokenRequest(name)) }.also { tokens.refresh() }
    }

    suspend fun revokeToken(id: Int): Boolean {
        val api = source.api ?: return false
        return (call { api.service<AiApi>().revokeApiToken(id) } is ApiResult.Ok).also { tokens.refresh() }
    }
}
