package com.zankup.android.settings.basic

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.TransfersApi
import com.zankup.android.api.model.ExportOut
import com.zankup.android.api.model.KeyStatus
import com.zankup.android.data.Store
import com.zankup.android.designsystem.components.ListRow
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import com.zankup.android.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** Settings › Your data: the export (a ZIP prepared in the background, downloaded in the browser). */
@HiltViewModel
class DataModel
@Inject
constructor(store: Store, private val source: ApiSource) : ViewModel() {
    val exports = store.query("settings/exports", ListSerializer(ExportOut.serializer())) { api ->
        call { api.service<TransfersApi>().listExports() }
    }

    init {
        viewModelScope.launch { exports.refresh() }
    }

    suspend fun prepare(): Boolean {
        val api = source.api ?: return false
        val ok = call { api.service<TransfersApi>().createExport() } is ApiResult.Ok
        exports.refresh()
        return ok
    }

    /** The download's address on the instance (the API gives a path). */
    fun absolute(url: String): String = source.api?.baseUrl?.resolve(url)?.toString() ?: url
}

@Composable
internal fun DataSection(
    onImport: () -> Unit,
    onGear: () -> Unit,
    onSync: () -> Unit,
    model: DataModel = hiltViewModel()
) {
    SettingsBody {
        SettingsGroup(
            rows = listOf(
                {
                    ListRow(
                        stringResource(R.string.set_import),
                        subtitle = stringResource(R.string.set_import_note),
                        onClick = onImport
                    )
                },
                {
                    ListRow(
                        stringResource(R.string.set_gear),
                        subtitle = stringResource(R.string.set_gear_note),
                        onClick = onGear
                    )
                },
                {
                    ListRow(
                        stringResource(R.string.set_sync),
                        subtitle = stringResource(R.string.set_sync_note),
                        onClick = onSync
                    )
                }
            )
        )
        ExportCard(model)
        SettingsCard(stringResource(R.string.set_backup_title)) { BackupSettings() }
    }
}

@Composable
private fun ExportCard(model: DataModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by model.exports.state.collectAsStateWithLifecycle(initialValue = null)
    val latest = state?.data?.firstOrNull()
    val busy = latest?.busy == true
    var starting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // Asks again every few seconds while it is being prepared.
    LaunchedEffect(busy) {
        while (busy) {
            delay(3_000)
            model.exports.refresh()
        }
    }
    SettingsCard(stringResource(R.string.set_export_title)) {
        SettingsNote(stringResource(R.string.set_export_help))
        OutlinedButton(enabled = !busy && !starting, modifier = Modifier.fillMaxWidth(), onClick = {
            starting = true
            failed = false
            scope.launch {
                failed = !model.prepare()
                starting = false
            }
        }) { Text(stringResource(R.string.set_export_start)) }
        if (busy) SettingsNote(stringResource(R.string.set_export_preparing), live = true)
        val link = latest?.downloadUrl?.let(model::absolute)
        if (latest?.status == "done" && link != null) {
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
                } catch (_: ActivityNotFoundException) {
                    failed = true
                }
            }) {
                Text(stringResource(R.string.set_export_download, megabytes((latest.propertySize ?: 0).toLong())))
            }
            latest.expiresAt?.let { SettingsNote(stringResource(R.string.set_export_until, mediumDate(it))) }
        }
        if (latest?.status == "error" || failed) SettingsNote(stringResource(R.string.set_error), live = true)
    }
}

private val S3_FIELDS =
    listOf(
        "backup_s3_endpoint" to R.string.set_s3_endpoint,
        "backup_s3_bucket" to R.string.set_s3_bucket,
        "backup_s3_region" to R.string.set_s3_region,
        "backup_s3_access_key_id" to R.string.set_s3_access_key_id
    )

/** When the daily backup runs, how many days are kept and where they go (S3), and its secret key. */
@Composable
private fun BackupSettings(model: InstanceModel = hiltViewModel()) {
    val state by model.instance.state.collectAsStateWithLifecycle(initialValue = null)
    val instance = state?.data ?: return
    val hour = instance.setting("backup_hour_utc") ?: return
    val keep = instance.setting("backup_keep_days") ?: return
    val scope = rememberCoroutineScope()
    // What was changed and not saved yet, by name; text fields keep what was typed.
    var hourChange by remember { mutableStateOf<Int?>(null) }
    var texts by remember { mutableStateOf(mapOf<String, String>()) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<ApiResult.Failure?>(null) }
    fun text(name: String) = texts[name] ?: instance.setting(name)?.text.orEmpty()
    SettingsNote(stringResource(R.string.set_backup_help))
    HourStepper(stringResource(R.string.set_backup_hour), hourChange ?: hour.number ?: 0) { hourChange = it }
    SettingOrigin(hour)
    OutlinedTextField(
        text("backup_keep_days"),
        { texts = texts + ("backup_keep_days" to digitsOnly(it)) },
        label = { Text(stringResource(R.string.set_keep_days)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    SettingOrigin(keep)
    S3_FIELDS.forEach { (name, label) ->
        OutlinedTextField(
            text(name),
            { texts = texts + (name to it) },
            label = { Text(stringResource(label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
    }
    SaveButton(
        dirty = hourChange != null || texts.isNotEmpty(),
        pending = busy,
        error = failure?.let { failureText(it) },
        onSave = {
            val changes = buildMap<String, JsonElement> {
                hourChange?.let { put("backup_hour_utc", JsonPrimitive(it)) }
                texts.forEach { (name, value) ->
                    val v = value.trim()
                    put(
                        name,
                        when {
                            v.isEmpty() -> JsonNull
                            name == "backup_keep_days" -> JsonPrimitive(v.toIntOrNull() ?: 0)
                            else -> JsonPrimitive(v)
                        }
                    )
                }
            }
            busy = true
            scope.launch {
                val r = model.save(changes)
                failure = r as? ApiResult.Failure
                if (r is ApiResult.Ok) {
                    hourChange = null
                    texts = emptyMap()
                }
                busy = false
            }
        }
    )
    SettingsNote(stringResource(R.string.set_s3_secret_help))
    ServiceKey(KeyStatus.Name.BACKUP_S3)
}
