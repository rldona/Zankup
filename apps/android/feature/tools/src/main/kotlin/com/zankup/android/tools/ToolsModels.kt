package com.zankup.android.tools

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.LibraryApi
import com.zankup.android.api.endpoints.StatsApi
import com.zankup.android.api.endpoints.SyncApi
import com.zankup.android.api.endpoints.TrainingApi
import com.zankup.android.api.endpoints.TransfersApi
import com.zankup.android.api.model.Comparison
import com.zankup.android.api.model.EventOut
import com.zankup.android.api.model.GearIn
import com.zankup.android.api.model.GearOut
import com.zankup.android.api.model.ImportOut
import com.zankup.android.api.model.SyncRequest
import com.zankup.android.api.model.SyncStatus
import com.zankup.android.data.Loadable
import com.zankup.android.data.Query
import com.zankup.android.data.Store
import com.zankup.android.domain.Units
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import com.zankup.android.tools.data.PartSource
import com.zankup.android.tools.data.Uploader
import com.zankup.android.tools.data.patchGear
import com.zankup.android.tools.data.refreshActivityGear
import com.zankup.android.tools.data.refreshAfterNewActivities
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** A query kept on the phone under "tools/[key]", as a state the screen collects. */
private fun <T> ViewModel.kept(query: Query<T>): StateFlow<Loadable<T>> =
    query.state.stateIn(viewModelScope, SharingStarted.Eagerly, Loadable())

// ---- Gear ----

/** What the gear form holds, as typed: distances in the person's unit. */
data class GearForm(
    val name: String = "",
    val kind: String = "shoe",
    val brand: String = "",
    val model: String = "",
    val sports: Set<String> = emptySet(),
    val habitual: Boolean = false,
    val initial: String = "0",
    val limit: String = "800"
) {
    companion object {
        fun of(gear: GearOut, units: Units) = GearForm(
            name = gear.name,
            kind = gear.kind,
            brand = gear.brand.orEmpty(),
            model = gear.model.orEmpty(),
            sports = gear.sports.toSet(),
            habitual = gear.habitual,
            initial = metresToField(gear.initialM, units),
            limit = gear.limitM?.let { metresToField(it, units) }.orEmpty()
        )
    }
}

/** Gear (OS-1163): shoes and bikes with their mileage, and adding, editing, retiring and deleting them. */
@HiltViewModel
class GearModel
@Inject
constructor(private val store: Store, private val source: ApiSource) : ViewModel() {
    private val query = store.query("tools/gear", ListSerializer(GearOut.serializer())) { api ->
        call { api.service<LibraryApi>().listGear() }
    }
    val gear: StateFlow<Loadable<List<GearOut>>> = kept(query)

    fun refresh() {
        viewModelScope.launch { query.refresh() }
    }

    /**
     * Saves the form: a new gear when [id] is null, else its changes ([retired] when it is retired or used
     * again). Null when it went well, else the failure.
     */
    suspend fun save(id: Int?, form: GearForm, units: Units, retired: Boolean? = null): ApiResult.Failure? {
        val api = source.api ?: return ApiResult.SignedOut
        val initial = fieldToMetres(form.initial.ifBlank { "0" }, units) ?: 0.0
        val limit = form.limit.takeIf { it.isNotBlank() }?.let { fieldToMetres(it, units) }
        val result =
            if (id == null) {
                call {
                    api.service<LibraryApi>().createGear(
                        GearIn(
                            name = form.name.trim(),
                            kind = GearIn.Kind.entries.firstOrNull { it.value == form.kind } ?: GearIn.Kind.SHOE,
                            brand = form.brand.trim().ifBlank { null },
                            model = form.model.trim().ifBlank { null },
                            sports = GearIn.Sports.entries.filter { it.value in form.sports },
                            initialM = initial,
                            limitM = limit,
                            habitual = form.habitual
                        )
                    )
                }
            } else {
                val body = buildJsonObject {
                    put("name", JsonPrimitive(form.name.trim()))
                    put("kind", JsonPrimitive(form.kind))
                    put("brand", form.brand.trim().ifBlank { null }?.let(::JsonPrimitive) ?: JsonNull)
                    put("model", form.model.trim().ifBlank { null }?.let(::JsonPrimitive) ?: JsonNull)
                    put("sports", JsonArray(form.sports.sorted().map(::JsonPrimitive)))
                    put("initial_m", JsonPrimitive(initial))
                    put("limit_m", limit?.let(::JsonPrimitive) ?: JsonNull)
                    put("habitual", JsonPrimitive(form.habitual))
                    if (retired != null) put("retired", JsonPrimitive(retired))
                }
                patchGear(api, id, body)
            }
        if (result is ApiResult.Failure) return result
        changed()
        return null
    }

    suspend fun delete(id: Int): Boolean {
        val api = source.api ?: return false
        val ok = call { api.service<LibraryApi>().deleteGear(id) } is ApiResult.Ok
        if (ok) changed()
        return ok
    }

    private suspend fun changed() {
        query.refresh()
        refreshActivityGear(store)
    }
}

// ---- Import ----

/** The file being uploaded and how much of it went. */
data class UploadProgress(val name: String, val sent: Long, val size: Long)

/**
 * Import (OS-1163): FIT, TCX, GPX or ZIP files uploaded in parts, the imports with their counts and
 * problems (polled while one is being worked on), and the comparison with Strava after an export.
 */
@HiltViewModel
class ImportModel
@Inject
constructor(
    private val store: Store,
    private val source: ApiSource,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private val importsQuery = store.query("tools/imports", ListSerializer(ImportOut.serializer())) { api ->
        call { api.service<TransfersApi>().listImports() }.also { latest = (it as? ApiResult.Ok)?.value }
    }
    private val comparisonQuery = store.query("tools/strava-comparison", Comparison.serializer()) { api ->
        call { api.service<StatsApi>().getStravaComparison() }
    }
    val imports: StateFlow<Loadable<List<ImportOut>>> = kept(importsQuery)
    val comparison: StateFlow<Loadable<Comparison>> = kept(comparisonQuery)

    private val _progress = MutableStateFlow<UploadProgress?>(null)
    val progress: StateFlow<UploadProgress?> = _progress.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    @Volatile private var latest: List<ImportOut>? = null
    private var polling: Job? = null
    private var sending: Job? = null

    fun refresh() {
        viewModelScope.launch { comparisonQuery.refresh() }
        poll()
    }

    /** Fetches the imports, again every 3 s while one is waiting or running. */
    private fun poll() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (true) {
                importsQuery.refresh()
                if (latest?.any { importActive(it.status) } != true) break
                delay(IMPORT_POLL_MS)
            }
        }
    }

    /** Uploads the files picked, one after the other. */
    fun upload(uris: List<Uri>) {
        if (uris.isEmpty() || sending?.isActive == true) return
        sending = viewModelScope.launch {
            _failed.value = false
            val api = source.api
            if (api == null) {
                _failed.value = true
                return@launch
            }
            val uploader = Uploader(api)
            try {
                for (uri in uris) {
                    val file = withContext(Dispatchers.IO) { UriSource.open(context, uri) }
                    if (file == null) {
                        _failed.value = true
                        break
                    }
                    _progress.value = UploadProgress(file.name, 0, file.size)
                    val done = try {
                        uploader.upload(file) { sent -> _progress.value = UploadProgress(file.name, sent, file.size) }
                    } finally {
                        file.close()
                    }
                    poll()
                    if (done !is ApiResult.Ok) {
                        _failed.value = true
                        break
                    }
                }
            } finally {
                _progress.value = null
                polling?.cancel()
                poll()
                refreshAfterNewActivities(store)
            }
        }
    }

    private companion object {
        const val IMPORT_POLL_MS = 3_000L
    }
}

/** A picked document, read part by part from its content URI (sequentially, reopened to go back). */
internal class UriSource private constructor(
    private val context: Context,
    private val uri: Uri,
    override val name: String,
    override val size: Long
) : PartSource {
    private var stream: InputStream? = null
    private var position = 0L

    override suspend fun read(offset: Long, length: Int): ByteArray = withContext(Dispatchers.IO) {
        if (stream == null || position != offset) {
            stream?.close()
            stream = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("unreadable")
            position = 0L
            skip(offset)
        }
        val input = stream!!
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n < 0) break
            read += n
        }
        position += read
        if (read == length) buffer else buffer.copyOf(read)
    }

    private fun skip(offset: Long) {
        val input = stream ?: return
        var left = offset
        while (left > 0) {
            val n = input.skip(left)
            if (n <= 0) break
            left -= n
        }
        position = offset - left
    }

    fun close() {
        runCatching { stream?.close() }
        stream = null
    }

    companion object {
        /** Its name and size from the provider; the size counted by reading it when the provider has none. */
        fun open(context: Context, uri: Uri): UriSource? {
            val resolver = context.contentResolver
            var name: String? = null
            var size: Long? = null
            runCatching {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val s = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (n >= 0 && !cursor.isNull(n)) name = cursor.getString(n)
                        if (s >= 0 && !cursor.isNull(s)) size = cursor.getLong(s)
                    }
                }
            }
            val bytes = size ?: runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                    }
                    total
                }
            }.getOrNull() ?: return null
            return UriSource(context, uri, name ?: uri.lastPathSegment ?: "upload", bytes)
        }
    }
}

// ---- Sync ----

/** Why a sync could not start. */
sealed interface StartFailure {
    data object NotConfigured : StartFailure

    data class Other(val detail: String?) : StartFailure
}

/**
 * Sync (OS-1163): the latest Garmin runs, polled every 2 s while one is queued or running, and starting
 * one (or the whole history). When a run ends the feed, this week, the profile and Home's stats refresh.
 */
@HiltViewModel
class SyncModel
@Inject
constructor(private val store: Store, private val source: ApiSource) : ViewModel() {
    @Volatile private var latest: List<SyncStatus>? = null

    private val query = store.query("tools/sync", ListSerializer(SyncStatus.serializer())) { api ->
        call { api.service<SyncApi>().listSyncs(limit = 10) }.also { latest = (it as? ApiResult.Ok)?.value }
    }
    val runs: StateFlow<Loadable<List<SyncStatus>>> = kept(query)

    private val _starting = MutableStateFlow(false)
    val starting: StateFlow<Boolean> = _starting.asStateFlow()

    private val _failure = MutableStateFlow<StartFailure?>(null)
    val failure: StateFlow<StartFailure?> = _failure.asStateFlow()

    private var polling: Job? = null

    /** The latest run's status the last time it was seen, to tell when it ends. */
    private var seen: SyncStatus.Status? = null

    fun refresh() {
        polling?.cancel()
        poll()
    }

    private fun poll() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (true) {
                query.refresh()
                val run = latest?.firstOrNull()
                if (runEnded(seen, run)) launch { refreshAfterNewActivities(store) }
                if (run != null) seen = run.status
                if (!runActive(run)) break
                delay(SYNC_POLL_MS)
            }
        }
    }

    fun start(history: Boolean) {
        if (_starting.value || runActive(runs.value.data?.firstOrNull())) return
        viewModelScope.launch {
            _starting.value = true
            _failure.value = null
            val api = source.api
            val result = api?.let { call { it.service<SyncApi>().startSync(SyncRequest(history = history)) } }
            when (result) {
                is ApiResult.Ok -> {
                    val run = result.value
                    val list = listOf(run) + runs.value.data.orEmpty().filter { it.id != run.id }
                    store.put("tools/sync", ListSerializer(SyncStatus.serializer()), list)
                    latest = list
                    seen = run.status
                    polling?.cancel()
                    poll()
                }
                is ApiResult.Http ->
                    _failure.value =
                        if (result.code == 409) StartFailure.NotConfigured else StartFailure.Other(result.detail)
                else -> _failure.value = StartFailure.Other(null)
            }
            _starting.value = false
        }
    }

    private companion object {
        const val SYNC_POLL_MS = 2_000L
    }
}

// ---- Notifications ----

/** Notifications (OS-1163): what the engine noticed lately, newest first. */
@HiltViewModel
class EventsModel
@Inject
constructor(store: Store) : ViewModel() {
    private val query = store.query("tools/events", ListSerializer(EventOut.serializer())) { api ->
        call { api.service<TrainingApi>().listEvents() }
    }
    val events: StateFlow<Loadable<List<EventOut>>> = kept(query)

    fun refresh() {
        viewModelScope.launch { query.refresh() }
    }
}
