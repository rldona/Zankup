package com.zankup.android.you

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.AuthApi
import com.zankup.android.api.endpoints.ProfileApi
import com.zankup.android.api.model.Me
import com.zankup.android.api.model.Preferences
import com.zankup.android.api.model.ProfileSummary
import com.zankup.android.api.model.WeightOut
import com.zankup.android.data.Loadable
import com.zankup.android.data.Store
import com.zankup.android.data.UserSettings
import com.zankup.android.network.ApiResult
import com.zankup.android.network.ZankupJson
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The You tab (OS-1160): who you are (kept under Home's "auth/me", so an edit shows everywhere), this
 * year's and all-time summary, and whether the server has a model (the Coach row). Saving the profile
 * sends a PATCH that can clear fields, so it is written by hand: the generated model drops nulls.
 */
@HiltViewModel
class YouViewModel
@Inject
constructor(
    private val store: Store,
    private val source: ApiSource,
    settings: UserSettings,
    @ApplicationContext context: Context
) : ViewModel() {
    private val meQuery = store.query(ME_KEY, Me.serializer()) { api -> call { api.service<AuthApi>().getMe() } }
    private val summaryQuery = store.query("you/summary", ProfileSummary.serializer()) { api ->
        call { api.service<ProfileApi>().getProfileSummary() }
    }
    private val settingsQuery = settings.query

    val me: StateFlow<Loadable<Me>> = meQuery.state.stateIn(viewModelScope, SharingStarted.Eagerly, Loadable())
    val summary: StateFlow<Loadable<ProfileSummary>> =
        summaryQuery.state.stateIn(viewModelScope, SharingStarted.Eagerly, Loadable())
    val settings: StateFlow<Loadable<Preferences>> =
        settingsQuery.state.stateIn(viewModelScope, SharingStarted.Eagerly, Loadable())

    private val prefs = context.getSharedPreferences("you", Context.MODE_PRIVATE)
    private val _physicalHidden = MutableStateFlow(prefs.getBoolean(PHYSICAL_HIDDEN, false))

    /** The eye on the physical data (when showing the phone), remembered on this phone. */
    val physicalHidden: StateFlow<Boolean> = _physicalHidden.asStateFlow()

    fun togglePhysical() {
        val next = !_physicalHidden.value
        _physicalHidden.value = next
        prefs.edit { putBoolean(PHYSICAL_HIDDEN, next) }
    }

    fun refresh() {
        viewModelScope.launch { meQuery.refreshIfStale() }
        viewModelScope.launch { summaryQuery.refreshIfStale() }
        viewModelScope.launch { settingsQuery.refreshIfStale() }
    }

    /** `PATCH /api/auth/me` with [body]; the answer is kept as the profile. A new weight is today's weighing too. */
    suspend fun save(body: JsonObject): ApiResult<Me> {
        val api = source.api ?: return ApiResult.SignedOut
        val before = me.value.data?.weightKg
        val request =
            Request
                .Builder()
                .url(api.baseUrl.resolve("api/auth/me")!!)
                .patch(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
        val result =
            withContext(Dispatchers.IO) {
                try {
                    api.http.newCall(request).execute().use { answer ->
                        val text = answer.body.string()
                        when {
                            answer.isSuccessful -> ApiResult.Ok(ZankupJson.decodeFromString(Me.serializer(), text))
                            answer.code == 401 -> ApiResult.SignedOut
                            else -> ApiResult.Http(answer.code, errorDetail(text))
                        }
                    }
                } catch (e: IOException) {
                    ApiResult.Offline(e)
                } catch (e: kotlinx.serialization.SerializationException) {
                    ApiResult.Unexpected(e)
                }
            }
        if (result is ApiResult.Ok) {
            store.put(ME_KEY, Me.serializer(), result.value)
            if (result.value.weightKg != before) {
                // Health's weight card and page show it at once.
                viewModelScope.launch {
                    store.query("health/weight", ListSerializer(WeightOut.serializer())) { api ->
                        call { api.service<ProfileApi>().listWeight() }
                    }.refresh()
                }
            }
        }
        return result
    }

    internal companion object {
        const val ME_KEY = "auth/me"
        const val PHYSICAL_HIDDEN = "physical_hidden"
    }
}

/** FastAPI's `detail`: a sentence, or the first validation error's `msg`. */
internal fun errorDetail(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return runCatching {
        when (val detail = ZankupJson.parseToJsonElement(body).jsonObject["detail"]) {
            is JsonPrimitive -> detail.content
            is kotlinx.serialization.json.JsonArray ->
                (detail.firstOrNull() as? JsonObject)?.get("msg")?.let { (it as? JsonPrimitive)?.content }
            else -> null
        }
    }.getOrNull()
}
