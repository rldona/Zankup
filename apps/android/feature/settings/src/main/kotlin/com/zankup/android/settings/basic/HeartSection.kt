package com.zankup.android.settings.basic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.StatsApi
import com.zankup.android.api.endpoints.TrainingApi
import com.zankup.android.api.model.EngineStatus
import com.zankup.android.api.model.EstimateOut
import com.zankup.android.api.model.Preferences
import com.zankup.android.data.Store
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import com.zankup.android.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Settings › Heart rate and zones: max and resting HR, the zone model, threshold HR and FTP, recomputing. */
@HiltViewModel
class HeartModel
@Inject
constructor(store: Store, private val source: ApiSource, private val writer: SettingsWriter) :
    ViewModel() {
    val settings = writer.settings.query

    // The same answer Progress and Stats keep.
    val estimates = store.query("stats/estimates", ListSerializer(EstimateOut.serializer())) { api ->
        call { api.service<StatsApi>().getEstimates() }
    }

    val engine = store.query("settings/engine", EngineStatus.serializer()) { api ->
        call { api.service<TrainingApi>().getEngine() }
    }

    init {
        viewModelScope.launch { settings.refresh() }
        viewModelScope.launch { estimates.refresh() }
        viewModelScope.launch { engine.refresh() }
    }

    suspend fun save(vararg changes: Pair<String, Any?>): ApiResult<Preferences> =
        writer.save(JsonObject(changes.associate { (k, v) -> k to json(v) }))

    suspend fun recompute(): Boolean {
        val api = source.api ?: return false
        val ok = call { api.service<TrainingApi>().recompute() } is ApiResult.Ok
        engine.refresh()
        return ok
    }

    private fun json(value: Any?) = when (value) {
        null -> JsonNull
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }
}

@Composable
internal fun HeartSection(model: HeartModel = hiltViewModel()) {
    val state by model.settings.state.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    SettingsBody {
        val settings = state?.data
        when {
            settings != null -> {
                HeartRateCard(settings, model)
                ZonesCard(settings, model)
            }
            state?.failure != null ->
                ErrorState(
                    onRetry = { scope.launch { model.settings.refresh() } },
                    offline = state?.failure is ApiResult.Offline
                )
            else -> Skeleton(Modifier.fillMaxWidth(), height = 240.dp)
        }
    }
}

@Composable
private fun HeartRateCard(settings: Preferences, model: HeartModel) {
    val scope = rememberCoroutineScope()
    // Starts again from the saved values after a save or a refresh.
    var max by remember(settings.hrMax) { mutableStateOf(settings.hrMax?.toString().orEmpty()) }
    var rest by remember(settings.hrRest) { mutableStateOf(settings.hrRest?.toString().orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<ApiResult<Preferences>?>(null) }
    val estimate = settings.hrMaxEstimate
    SettingsCard(stringResource(R.string.set_hr_title)) {
        SettingsNote(
            if (estimate != null) {
                stringResource(R.string.set_hr_help, estimate)
            } else {
                stringResource(R.string.set_hr_help_none)
            }
        )
        NumberField(stringResource(R.string.set_hr_max), max, { max = it }, placeholder = estimate?.toString())
        NumberField(stringResource(R.string.set_hr_rest), rest, { rest = it })
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true
            outcome = null
            scope.launch {
                outcome = model.save("hr_max" to numberOrNull(max), "hr_rest" to numberOrNull(rest))
                busy = false
            }
        }) { Text(stringResource(R.string.set_save)) }
        when (val o = outcome) {
            is ApiResult.Ok -> SettingsNote(stringResource(R.string.set_saved), live = true)
            is ApiResult.Failure -> SettingsNote(failureText(o), live = true)
            null -> Unit
        }
    }
}

@Composable
private fun ZonesCard(settings: Preferences, model: HeartModel) {
    val scope = rememberCoroutineScope()
    val estimates by model.estimates.state.collectAsStateWithLifecycle(initialValue = null)
    val engineState by model.engine.state.collectAsStateWithLifecycle(initialValue = null)
    val engine = engineState?.data
    var failure by remember { mutableStateOf<ApiResult.Failure?>(null) }
    var recomputing by remember { mutableStateOf(false) }
    val running = engine?.running == true
    // Asks again every few seconds while the engine works.
    LaunchedEffect(running) {
        while (running) {
            delay(3_000)
            model.engine.refresh()
        }
    }
    fun save(vararg changes: Pair<String, Any?>) {
        scope.launch { failure = model.save(*changes) as? ApiResult.Failure }
    }
    val lthr = estimateOf(estimates?.data, "lthr")
    val ftp = estimateOf(estimates?.data, "ftp")
    SettingsCard(stringResource(R.string.set_zones)) {
        SettingsNote(stringResource(R.string.set_recompute_note))
        val zoneModel = stringResource(R.string.set_zone_model)
        ChipPicker(
            Preferences.ZoneModel.entries,
            settings.zoneModel,
            {
                stringResource(
                    when (it) {
                        Preferences.ZoneModel.MAX -> R.string.set_zone_max
                        Preferences.ZoneModel.RESERVE -> R.string.set_zone_reserve
                        Preferences.ZoneModel.LTHR -> R.string.set_zone_lthr
                    }
                )
            },
            { save("zone_model" to it.value) },
            // The row's name for TalkBack, as the React Native app's radio group.
            modifier = Modifier.semantics { contentDescription = zoneModel },
            accent = true
        )
        ThresholdField(stringResource(R.string.set_lthr), settings.lthr, lthr?.let { "$it bpm" }, lthr) {
            save("lthr" to it)
        }
        ThresholdField(stringResource(R.string.set_ftp), settings.ftp, ftp?.let { "$it W" }, ftp) {
            save("ftp" to it)
        }
        OutlinedButton(
            enabled = !running && !recomputing,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                recomputing = true
                scope.launch {
                    model.recompute()
                    recomputing = false
                }
            }
        ) { Text(stringResource(R.string.set_recompute)) }
        if (running && engine != null) {
            SettingsNote(stringResource(R.string.set_recomputing, engine.done, engine.total), live = true)
        }
        failure?.let { SettingsNote(failureText(it), live = true) }
    }
}

/** Saved when it loses focus (or on Done), as in the web; the estimate is the placeholder. */
@Composable
private fun ThresholdField(label: String, saved: Int?, estimate: String?, placeholder: Int?, onSave: (Int?) -> Unit) {
    var text by remember(saved) { mutableStateOf(saved?.toString().orEmpty()) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NumberField(
            label,
            text,
            { text = it },
            placeholder = placeholder?.toString(),
            onDone = { focus.clearFocus() },
            modifier = Modifier.onFocusChanged {
                if (focused && !it.isFocused) thresholdChange(text, saved)?.let { change -> onSave(change.value) }
                focused = it.isFocused
            }
        )
        estimate?.let { SettingsNote(stringResource(R.string.set_estimated, it)) }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value,
        { onChange(digitsOnly(it)) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier.fillMaxWidth()
    )
}
