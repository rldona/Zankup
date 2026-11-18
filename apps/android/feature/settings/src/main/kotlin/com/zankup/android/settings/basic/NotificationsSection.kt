package com.zankup.android.settings.basic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.NotificationsApi
import com.zankup.android.api.model.KeyStatus
import com.zankup.android.api.model.Notifications
import com.zankup.android.api.model.Preferences
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import com.zankup.android.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Notifications on this phone, as the app provides them (Firebase): whether this build can receive
 * them at all, whether this phone is registered, and turning it on or off (registering the token or
 * `null`). [setEnabled] returns whether the change took effect. The screen asks Android's notification
 * permission (13+) before turning them on.
 */
data class PushControl(
    val available: Boolean,
    val enabled: suspend () -> Boolean,
    val setEnabled: suspend (Boolean) -> Boolean
)

/** Settings › Notifications: the test and which kinds (`PATCH /api/settings {notifications}`). */
@HiltViewModel
class NotificationsModel
@Inject
constructor(private val source: ApiSource, private val writer: SettingsWriter) :
    ViewModel() {
    val settings = writer.settings.query

    init {
        viewModelScope.launch { settings.refresh() }
    }

    suspend fun test(): Boolean {
        val api = source.api ?: return false
        return call { api.service<NotificationsApi>().testPush() } is ApiResult.Ok
    }

    suspend fun setKinds(prefs: Notifications): ApiResult<Preferences> =
        writer.save(JsonObject(mapOf("notifications" to prefs.toJson())))
}

/** What the phone's switch last did, to say under it. */
private enum class PushOutcome { ON, OFF, DENIED, FAILED }

@Composable
internal fun NotificationsSection(push: PushControl, model: NotificationsModel = hiltViewModel()) {
    SettingsBody {
        ThisPhone(push, model)
        Kinds(model)
        SettingsCard(stringResource(R.string.set_alerts_title)) { AlertSettings() }
    }
}

@Composable
private fun ThisPhone(push: PushControl, model: NotificationsModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<PushOutcome?>(null) }
    var test by remember { mutableStateOf<Boolean?>(null) }
    var testing by remember { mutableStateOf(false) }
    LaunchedEffect(push.available) { if (push.available) on = push.enabled() }
    fun turn(next: Boolean) {
        busy = true
        scope.launch {
            val worked = push.setEnabled(next)
            if (worked) on = next
            outcome = when {
                !worked -> PushOutcome.FAILED
                next -> PushOutcome.ON
                else -> PushOutcome.OFF
            }
            busy = false
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) turn(true) else outcome = PushOutcome.DENIED
    }
    SettingsCard(stringResource(R.string.set_notifications)) {
        SettingsNote(stringResource(R.string.set_push_help))
        if (!push.available) {
            SettingsNote(stringResource(R.string.set_push_unavailable))
        } else {
            SwitchRow(
                stringResource(R.string.set_push_this_phone),
                checked = on == true,
                enabled = on != null && !busy,
                onChange = { next ->
                    val needsAsking = next && !notificationsAllowed(context)
                    if (needsAsking) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else turn(next)
                }
            )
            outcome?.let {
                SettingsNote(
                    stringResource(
                        when (it) {
                            PushOutcome.ON -> R.string.set_push_on
                            PushOutcome.OFF -> R.string.set_push_off
                            PushOutcome.DENIED -> R.string.set_push_denied
                            PushOutcome.FAILED -> R.string.set_error
                        }
                    ),
                    live = true
                )
            }
        }
        OutlinedButton(enabled = !testing, modifier = Modifier.fillMaxWidth(), onClick = {
            test = null
            testing = true
            scope.launch {
                test = model.test()
                testing = false
            }
        }) { Text(stringResource(R.string.set_test)) }
        when (test) {
            true -> SettingsNote(stringResource(R.string.set_sent), live = true)
            false -> SettingsNote(stringResource(R.string.set_error), live = true)
            null -> Unit
        }
    }
}

@Composable
private fun Kinds(model: NotificationsModel) {
    val state by model.settings.state.collectAsStateWithLifecycle(initialValue = null)
    val prefs = state?.data?.notifications ?: return
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<ApiResult.Failure?>(null) }
    SettingsCard(stringResource(R.string.set_which)) {
        NOTIFICATION_KINDS.forEach { kind ->
            SwitchRow(
                stringResource(kindLabel(kind)),
                checked = prefs.isOn(kind),
                enabled = !busy,
                onChange = { on ->
                    busy = true
                    scope.launch {
                        failure = model.setKinds(prefs.with(kind, on)) as? ApiResult.Failure
                        busy = false
                    }
                }
            )
        }
        failure?.let { SettingsNote(failureText(it), live = true) }
    }
}

private fun notificationsAllowed(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
    PackageManager.PERMISSION_GRANTED

private fun kindLabel(kind: String) = when (kind) {
    "activity" -> R.string.set_kind_activity
    "record" -> R.string.set_kind_record
    "sync_error" -> R.string.set_kind_sync_error
    "garmin_key" -> R.string.set_kind_garmin_key
    "estimate" -> R.string.set_kind_estimate
    "import" -> R.string.set_kind_import
    else -> R.string.set_kind_weekly
}

/** Where alerts by e-mail go (`PATCH /api/instance {alert_email}`), and Resend's key to send them. */
@Composable
private fun AlertSettings(model: InstanceModel = hiltViewModel()) {
    val state by model.instance.state.collectAsStateWithLifecycle(initialValue = null)
    val email = state?.data?.setting("alert_email") ?: return
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<ApiResult.Failure?>(null) }
    val shown = value ?: email.text
    SettingsNote(stringResource(R.string.set_alerts_help))
    OutlinedTextField(
        shown,
        { value = it },
        label = { Text(stringResource(R.string.set_alert_email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth()
    )
    SettingOrigin(email)
    SaveButton(
        dirty = value != null && value?.trim() != email.text,
        pending = busy,
        error = failure?.let { failureText(it) },
        onSave = {
            busy = true
            scope.launch {
                val next = shown.trim()
                val r = model.save(mapOf("alert_email" to (if (next.isEmpty()) JsonNull else JsonPrimitive(next))))
                failure = r as? ApiResult.Failure
                if (r is ApiResult.Ok) value = null
                busy = false
            }
        }
    )
    SettingsNote(stringResource(R.string.set_resend_help))
    ServiceKey(KeyStatus.Name.RESEND)
}
