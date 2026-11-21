package com.zankup.android.activity

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.ActivitiesApi
import com.zankup.android.api.endpoints.AiApi
import com.zankup.android.api.endpoints.LibraryApi
import com.zankup.android.api.endpoints.SharingApi
import com.zankup.android.api.endpoints.StatsApi
import com.zankup.android.api.endpoints.TrainingApi
import com.zankup.android.api.model.ActivityContext
import com.zankup.android.api.model.ActivityDetail
import com.zankup.android.api.model.ActivityEdit
import com.zankup.android.api.model.ActivityStreams
import com.zankup.android.api.model.CurvePoint
import com.zankup.android.api.model.GearOut
import com.zankup.android.api.model.ReportOut
import com.zankup.android.api.model.SegmentCreate
import com.zankup.android.api.model.ShareCreate
import com.zankup.android.api.model.SummaryOut
import com.zankup.android.data.CacheKeys
import com.zankup.android.data.Store
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * An activity (OS-1134): itself, its context (fitness, matches, effort, goals, results) and its series,
 * each kept on the phone and shown at once, then refreshed. Editing, photos, sharing and saving a
 * section as a segment (OS-1135) go through here too.
 */
@HiltViewModel
class ActivityModel
@Inject
constructor(
    private val store: Store,
    private val source: ApiSource,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private var id = -1L

    lateinit var detail: com.zankup.android.data.Query<ActivityDetail>
        private set
    lateinit var activityContext: com.zankup.android.data.Query<ActivityContext>
        private set
    val allTimePower = store.query("stats/curves/power", ListSerializer(CurvePoint.serializer())) { api ->
        call { api.service<StatsApi>().getCurves(StatsApi.KindGetCurves.POWER) }
    }
    val gear = store.query("gear", ListSerializer(GearOut.serializer())) { api ->
        call { api.service<LibraryApi>().listGear() }
    }

    private val streamsState = MutableStateFlow<ActivityStreams?>(null)
    val streams: StateFlow<ActivityStreams?> = streamsState.asStateFlow()

    private val summaryState = MutableStateFlow<SummaryOut?>(null)
    val summary: StateFlow<SummaryOut?> = summaryState.asStateFlow()

    fun open(id: Long) {
        if (this.id == id) return
        this.id = id
        detail =
            store.query(CacheKeys.activity(id), ActivityDetail.serializer()) { api ->
                call { api.service<ActivitiesApi>().getActivity(id.toInt()) }
            }
        activityContext =
            store.query(CacheKeys.context(id), ActivityContext.serializer()) { api ->
                call { api.service<ActivitiesApi>().getActivityContext(id.toInt()) }
            }
        viewModelScope.launch { detail.refreshIfStale() }
        viewModelScope.launch { activityContext.refreshIfStale() }
        viewModelScope.launch { streamsState.value = streams(id) }
        viewModelScope.launch {
            val api = source.api ?: return@launch
            summaryState.value =
                (call { api.service<AiApi>().getActivitySummary(id.toInt()) } as? ApiResult.Ok)?.value
        }
    }

    private suspend fun streams(id: Long): ActivityStreams? {
        store.streams(id, ActivityStreams.serializer())?.let { return it }
        val api = source.api ?: return null
        val fresh =
            call { api.service<ActivitiesApi>().getActivityStreams(id.toInt()) } as? ApiResult.Ok ?: return null
        store.putStreams(id, ActivityStreams.serializer(), fresh.value)
        return fresh.value
    }

    /** "Try again" after a failure: everything fetched anew. */
    fun retry() {
        viewModelScope.launch { detail.refresh() }
        viewModelScope.launch { activityContext.refresh() }
        viewModelScope.launch { if (streamsState.value == null) streamsState.value = streams(id) }
    }

    private val reportState = MutableStateFlow<ReportState>(ReportState.Idle)
    val report: StateFlow<ReportState> = reportState.asStateFlow()

    /** "Tell me more": the kept report, or a new one written now (the server decides). */
    fun loadReport() {
        if (reportState.value is ReportState.Ready || reportState.value is ReportState.Writing) return
        reportState.value = ReportState.Writing
        viewModelScope.launch {
            val api = source.api
            val result = api?.let { call { it.service<AiApi>().createActivityReport(id.toInt()) } }
            reportState.value = (result as? ApiResult.Ok)?.value?.let { ReportState.Ready(it) } ?: ReportState.Failed
        }
    }

    fun refreshPowerCurve() = viewModelScope.launch { allTimePower.refresh() }

    /** "Write it": asks the instance's assistant for the activity's summary. */
    suspend fun writeSummary(): Boolean {
        val api = source.api ?: return false
        val result = call { api.service<AiApi>().createActivitySummary(id.toInt()) }
        if (result is ApiResult.Ok) summaryState.value = result.value
        return result is ApiResult.Ok
    }

    // --- editing, photos and sharing (OS-1135) ---------------------------------------------------

    suspend fun save(edit: ActivityEdit): ApiResult<*> {
        val api = source.api ?: return ApiResult.SignedOut
        val result = call { api.service<LibraryApi>().editActivity(id.toInt(), edit) }
        if (result is ApiResult.Ok) detail.refresh()
        return result
    }

    suspend fun delete(): Boolean {
        val api = source.api ?: return false
        return call { api.service<LibraryApi>().deleteActivity(id.toInt()) } is ApiResult.Ok
    }

    suspend fun removePhoto(photoId: Int): Boolean {
        val api = source.api ?: return false
        val ok = call { api.service<LibraryApi>().deletePhoto(photoId) } is ApiResult.Ok
        if (ok) detail.refresh()
        return ok
    }

    /** A photo from the picker, uploaded as the API's multipart form (`file`). */
    suspend fun addPhoto(uri: Uri): Boolean {
        val api = source.api ?: return false
        val bytes =
            withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                ?: return false
        val type = context.contentResolver.getType(uri) ?: "image/jpeg"
        val body =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "photo", bytes.toRequestBody(type.toMediaType()))
                .build()
        val request = Request.Builder().url(api.baseUrl.resolve("api/activities/$id/photos")!!).post(body).build()
        val ok =
            withContext(Dispatchers.IO) {
                runCatching { api.http.newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
            }
        if (ok) detail.refresh()
        return ok
    }

    /** A link of the web app to this activity, valid a week, with its map. */
    suspend fun shareLink(): String? {
        val api = source.api ?: return null
        val share =
            call {
                api.service<SharingApi>().createShare(id.toInt(), ShareCreate(days = 7, showMap = true))
            } as? ApiResult.Ok
                ?: return null
        val path = share.value.path ?: return null
        return webOrigin(api.baseUrl.toString()) + path.removePrefix("/")
    }

    /** What a share image of this activity shows: the shared view, privacy zones already out (OS-919). */
    suspend fun shared(): com.zankup.android.api.model.SharedActivity? {
        val api = source.api ?: return null
        return (call { api.service<SharingApi>().previewSharedActivity(id.toInt(), true) } as? ApiResult.Ok)?.value
    }

    /** Draws the card and saves it as a PNG in the cache, for the share sheet; null if it fails. */
    internal suspend fun shareImage(content: CardContent, colours: CardColours): java.io.File? =
        withContext(Dispatchers.Default) {
            runCatching {
                val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
                val file = java.io.File(dir, "zankup-$id.png")
                val bitmap = drawCard(content, colours)
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                file
            }.getOrNull()
        }

    suspend fun saveSegment(from: Int, to: Int, name: String): Boolean {
        val api = source.api ?: return false
        return call {
            api.service<TrainingApi>().createSegment(SegmentCreate(id.toInt(), from, to, name))
        } is ApiResult.Ok
    }

    /** The photo's address, read with the session's client (it needs the token). */
    fun photoUrl(photoId: Int, thumb: Boolean): String? =
        source.api?.baseUrl?.resolve("api/photos/$photoId?thumb=$thumb")?.toString()

    val http get() = source.api?.http
}

/** Where the coach's longer report is. */
sealed interface ReportState {
    data object Idle : ReportState
    data object Writing : ReportState
    data object Failed : ReportState
    data class Ready(val report: ReportOut) : ReportState
}

/** Share links open the web app: the reference instance's API is on api.zankup.com, its web on zankup.com. */
internal fun webOrigin(api: String): String =
    if (api.startsWith("https://api.zankup.com")) "https://zankup.com/" else api
