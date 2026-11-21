package com.zankup.android.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.AiApi
import com.zankup.android.api.model.AiStatus
import com.zankup.android.api.model.CoachMessage
import com.zankup.android.api.model.CoachRequest
import com.zankup.android.api.model.WeeklyOut
import com.zankup.android.api.model.WeeklyRequest
import com.zankup.android.data.Loadable
import com.zankup.android.data.Store
import com.zankup.android.network.ApiResult
import com.zankup.android.network.ZankupJson
import com.zankup.android.network.call
import com.zankup.android.network.ndjsonLines
import com.zankup.android.session.ApiSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The Coach (OS-1162): whether the server has a model and the person turned it on, the weekly report,
 * and the conversation, which lives only in this screen. Questions go to `POST /api/ai/coach` through
 * the session's client (its token, and the refresh on a 401), read line by line as they are written.
 */
@HiltViewModel
class CoachViewModel
@Inject
constructor(store: Store, private val source: ApiSource) : ViewModel() {
    private val statusQuery = store.query("coach/ai-status", AiStatus.serializer()) { api ->
        call { api.service<AiApi>().getAiStatus() }
    }
    private val weeklyQuery =
        store.query("coach/weekly", kotlinx.serialization.builtins.ListSerializer(WeeklyOut.serializer())) { api ->
            call { api.service<AiApi>().listWeeklyReports() }
        }

    val status: StateFlow<Loadable<AiStatus>> =
        statusQuery.state.stateIn(viewModelScope, SharingStarted.Eagerly, Loadable())
    val weekly: StateFlow<Loadable<List<WeeklyOut>>> =
        weeklyQuery.state.stateIn(viewModelScope, SharingStarted.Eagerly, Loadable())

    private val _chat = MutableStateFlow(Chat())
    val chat: StateFlow<Chat> = _chat.asStateFlow()

    private val _writing = MutableStateFlow(false)

    /** "Write last week's report now" is running. */
    val writing: StateFlow<Boolean> = _writing.asStateFlow()

    private val _writeFailed = MutableStateFlow(false)
    val writeFailed: StateFlow<Boolean> = _writeFailed.asStateFlow()

    private var answering: Job? = null

    fun refresh() {
        viewModelScope.launch { statusQuery.refreshIfStale() }
        viewModelScope.launch { weeklyQuery.refreshIfStale() }
    }

    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || _chat.value.busy) return
        val history = _chat.value.history() + Turn(Speaker.USER, text)
        _chat.update { it.ask(text) }
        var job: Job? = null
        job = viewModelScope.launch {
            try {
                stream(history)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Stop cancels the call (an IOException): that is no failure.
                if (isActive && answering === job) _chat.update { it.fail() }
            } finally {
                // A conversation cleared meanwhile has its own state: this one no longer touches it.
                if (answering === job) {
                    _chat.update { it.finish() }
                    answering = null
                }
            }
        }
        answering = job
    }

    /** Stop: the answer stays as far as it got. */
    fun stop() {
        answering?.cancel()
    }

    /** A new conversation; an answer being written stops first. */
    fun clear() {
        answering?.cancel()
        answering = null
        _chat.value = Chat()
    }

    private suspend fun stream(history: List<Turn>) {
        val api = source.api ?: throw IOException("signed out")
        val body =
            ZankupJson.encodeToString(
                CoachRequest.serializer(),
                CoachRequest(
                    history.map {
                        CoachMessage(
                            if (it.role == Speaker.USER) CoachMessage.Role.USER else CoachMessage.Role.ASSISTANT,
                            it.content
                        )
                    }
                )
            )
        val request =
            Request
                .Builder()
                .url(api.baseUrl.resolve("api/ai/coach")!!)
                .header("Accept", "application/x-ndjson")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        // The session's client (token, refresh on 401), with time for the tools between lines.
        val client = api.http.newBuilder().readTimeout(STREAM_READ_TIMEOUT_S, TimeUnit.SECONDS).build()
        val call = client.newCall(request)
        val job = kotlin.coroutines.coroutineContext[Job]
        val cancelOnStop = job?.invokeOnCompletion { call.cancel() }
        try {
            val response = withContext(Dispatchers.IO) { call.execute() }
            if (!response.isSuccessful) {
                response.close()
                throw IOException("HTTP ${response.code}")
            }
            val pending = StringBuilder()
            var flushedAt = System.currentTimeMillis()
            fun flush() {
                if (pending.isEmpty()) return
                _chat.update { it.apply(CoachEvent.Text(pending.toString())) }
                pending.setLength(0)
                flushedAt = System.currentTimeMillis()
            }
            response.body.ndjsonLines().collect { line ->
                when (val event = parseEvent(line)) {
                    null -> Unit
                    is CoachEvent.Text -> {
                        // Tokens do not redraw the text one by one: a few go together every TEXT_FLUSH_MS.
                        pending.append(event.text)
                        if (System.currentTimeMillis() - flushedAt >= TEXT_FLUSH_MS) flush()
                    }
                    else -> {
                        flush()
                        _chat.update { it.apply(event) }
                    }
                }
            }
            flush()
        } finally {
            cancelOnStop?.dispose()
        }
    }

    /** "Write last week's report now": `POST /api/ai/weekly`, then the reports again. */
    fun writeWeekly() {
        if (_writing.value) return
        viewModelScope.launch {
            val api = source.api ?: return@launch
            _writing.value = true
            _writeFailed.value = false
            val result = call { api.service<AiApi>().createWeeklyReport(WeeklyRequest()) }
            if (result is ApiResult.Ok) weeklyQuery.refresh() else _writeFailed.value = true
            _writing.value = false
        }
    }

    private companion object {
        const val STREAM_READ_TIMEOUT_S = 120L

        /** How long tokens are gathered before the answer is redrawn. */
        const val TEXT_FLUSH_MS = 60L
    }
}

/** The AI assistants page: Zankup's MCP address on this instance. */
@HiltViewModel
class AssistantsViewModel
@Inject
constructor(source: ApiSource) : ViewModel() {
    val address: String = mcpAddress(source.api?.baseUrl?.toString())
}
