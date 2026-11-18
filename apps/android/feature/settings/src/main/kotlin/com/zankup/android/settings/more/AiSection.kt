package com.zankup.android.settings.more

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.AiPreferences
import com.zankup.android.api.model.ApiTokenCreated
import com.zankup.android.api.model.InstanceOut
import com.zankup.android.api.model.KeyStatus
import com.zankup.android.api.model.SettingOut
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.network.ApiResult
import com.zankup.android.settings.R
import com.zankup.android.settings.basic.noteStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Settings › AI assistant (OS-1161), as the React Native app has it: Anthropic's key; with a key, the
 * Coach's model, budget and Monday's hour; the AI switches; and the MCP tokens for other assistants.
 * A body for the section screen, which gives the title and the scrolling column.
 */
@Composable
fun AiSection(onAssistants: () -> Unit, model: AiModel = hiltViewModel()) {
    val keys by model.service.keys.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { model.refresh() }
    val anthropic = keys.data?.firstOrNull { it.name == KeyStatus.Name.ANTHROPIC }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MoreCard(stringResource(R.string.setm_keys_anthropic)) {
            Note(stringResource(R.string.setm_keys_anthropicHelp))
            when {
                anthropic != null -> KeyEditor(anthropic, model::saveKey) { model.deleteKey() }
                keys.failure != null -> Note(failureText(keys.failure))
                else -> Skeleton(Modifier.fillMaxWidth(), height = 56.dp)
            }
        }
        // The model and budget only with a key: without one the Coach is off.
        if (anthropic?.configured == true) CoachCard(model)
        AiCard(model)
        McpCard(model, onAssistants)
    }
}

@Composable
private fun CoachCard(model: AiModel) {
    val instance by model.service.instance.collectAsStateWithLifecycle()
    val data = instance.data ?: return
    val setting = { name: String -> data.settings.firstOrNull { it.name == name } }
    val aiModel = setting("ai_model") ?: return
    val budget = setting("ai_monthly_budget_usd") ?: return
    val hour = setting("weekly_report_hour_utc") ?: return
    MoreCard(stringResource(R.string.setm_instance_coachTitle)) {
        CoachForm(data, aiModel, budget, hour, model)
    }
}

@Composable
private fun CoachForm(data: InstanceOut, aiModel: SettingOut, budget: SettingOut, hour: SettingOut, model: AiModel) {
    val scope = rememberCoroutineScope()
    var changes by remember { mutableStateOf(mapOf<String, JsonElement>()) }
    var budgetText by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<ApiResult.Failure?>(null) }
    val chosen = MoreLogic.text(changes["ai_model"] ?: aiModel.value).orEmpty()
    val utc = MoreLogic.number(changes["weekly_report_hour_utc"] ?: hour.value)?.toInt() ?: 0
    Note(stringResource(R.string.setm_instance_modelHelp))
    val ids = data.models.map { it.id }
    if (ids.isNotEmpty()) {
        val modelLabel = stringResource(R.string.setm_instance_model)
        ChipPicker(
            options = ids,
            selected = chosen,
            label = { MoreLogic.shortModelName(it) },
            onSelect = { changes = changes + ("ai_model" to JsonPrimitive(it)) },
            modifier = Modifier.semantics { contentDescription = modelLabel },
            accent = true
        )
    }
    data.models.firstOrNull { it.id == chosen }?.let {
        Note(stringResource(R.string.setm_instance_price, MoreLogic.money(it.inputUsd), MoreLogic.money(it.outputUsd)))
    }
    if ("ai_model" !in changes && aiModel.source == SettingOut.Source.ENV) {
        Note(stringResource(R.string.setm_instance_fromServer))
    }
    val budgetShown = budgetText ?: budget.value?.let { MoreLogic.text(it) }.orEmpty()
    OutlinedTextField(
        value = budgetShown,
        onValueChange = { text ->
            val typed = MoreLogic.budgetInput(text)
            budgetText = typed
            val value = typed.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(typed)
            changes = changes + ("ai_monthly_budget_usd" to value)
        },
        label = { Text(stringResource(R.string.setm_instance_budget)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
    HourStepper(stringResource(R.string.setm_instance_weeklyHour), utc) {
        changes = changes + ("weekly_report_hour_utc" to JsonPrimitive(it))
    }
    Button(enabled = changes.isNotEmpty() && !busy, onClick = {
        busy = true
        failure = null
        scope.launch {
            when (val r = model.saveCoach(changes)) {
                is ApiResult.Ok -> {
                    changes = emptyMap()
                    budgetText = null
                }
                is ApiResult.Failure -> failure = r
            }
            busy = false
        }
    }) { Text(stringResource(if (busy) R.string.setm_keys_checking else R.string.setm_settings_save)) }
    failure?.let { Note(failureText(it), live = true) }
}

@Composable
private fun AiCard(model: AiModel) {
    val scope = rememberCoroutineScope()
    val status by model.status.state.collectAsStateWithLifecycle(initialValue = null)
    val settings by model.settings.state.collectAsStateWithLifecycle(initialValue = null)
    val info = status?.data ?: return
    val prefs = settings?.data?.ai ?: return
    if (!info.available) return
    // What was just switched, shown at once while the server saves it.
    var shown by remember(prefs) { mutableStateOf(prefs) }
    val enabled = shown.enabled != false
    fun set(next: AiPreferences) {
        shown = next
        scope.launch { if (model.saveAi(next) is ApiResult.Failure) shown = prefs }
    }
    MoreCard(stringResource(R.string.setm_ai_title)) {
        Note(stringResource(R.string.setm_ai_help, info.model.orEmpty()))
        SwitchLine(stringResource(R.string.setm_ai_enabled), enabled, { set(shown.copy(enabled = it)) })
        SwitchLine(
            stringResource(R.string.setm_ai_summaries),
            shown.summaries != false,
            { set(shown.copy(summaries = it)) },
            enabled = enabled
        )
        SwitchLine(
            stringResource(R.string.setm_ai_weekly),
            shown.weekly != false,
            { set(shown.copy(weekly = it)) },
            enabled = enabled
        )
        if (info.budget?.exceeded == true) Note(stringResource(R.string.setm_ai_budgetExceeded))
    }
}

@Composable
private fun McpCard(model: AiModel, onAssistants: () -> Unit) {
    val scope = rememberCoroutineScope()
    val tokens by model.tokens.state.collectAsStateWithLifecycle(initialValue = null)
    var name by remember { mutableStateOf("") }
    var created by remember { mutableStateOf<ApiTokenCreated?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var revoking by remember { mutableStateOf<Pair<Int, String>?>(null) }
    MoreCard(stringResource(R.string.setm_mcp_title)) {
        Button(onClick = onAssistants, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.setm_assistants_connect))
        }
        Note(stringResource(R.string.setm_mcp_help))
        // "URL: …", the address in monospace, as one muted line.
        SelectionContainer {
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.setm_mcp_url) + ": ")
                    withStyle(SpanStyle(color = ZankupTheme.colors.fg, fontFamily = FontFamily.Monospace)) {
                        append(model.mcpUrl(created?.url))
                    }
                },
                style = noteStyle,
                color = ZankupTheme.colors.muted
            )
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(120) },
            label = { Text(stringResource(R.string.setm_mcp_name)) },
            placeholder = { Text("Claude") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(enabled = !busy && name.isNotBlank(), onClick = {
            busy = true
            failed = false
            scope.launch {
                when (val r = model.createToken(name.trim())) {
                    is ApiResult.Ok -> {
                        created = r.value
                        name = ""
                    }
                    is ApiResult.Failure -> failed = true
                }
                busy = false
            }
        }) { Text(stringResource(R.string.setm_mcp_create)) }
        if (failed) Note(stringResource(R.string.setm_common_error), live = true)
        created?.let { CreatedToken(it.token) }
        val list = tokens?.data
        when {
            list == null && tokens?.failure != null -> Note(failureText(tokens?.failure))
            list == null -> Skeleton(Modifier.fillMaxWidth(), height = 48.dp)
            list.isEmpty() -> Note(stringResource(R.string.setm_mcp_none))
            else -> list.forEach { token ->
                val used = token.lastUsedAt?.let { stringResource(R.string.setm_mcp_lastUsed, day(it)) }
                    ?: stringResource(R.string.setm_mcp_neverUsed)
                ItemRow(token.name, "${token.prefix}… · $used") {
                    OutlinedButton(enabled = !busy, onClick = { revoking = token.id to token.name }) {
                        Text(stringResource(R.string.setm_mcp_revoke))
                    }
                }
            }
        }
    }
    revoking?.let { (id, title) ->
        Confirm(
            title = title,
            text = stringResource(R.string.setm_mcp_revokeConfirm),
            action = stringResource(R.string.setm_mcp_revoke),
            onConfirm = {
                busy = true
                scope.launch {
                    if (model.revokeToken(id)) created = null
                    busy = false
                }
            },
            onDismiss = { revoking = null }
        )
    }
}

/** A new token, shown once: copy it or share it. */
@Composable
private fun CreatedToken(token: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Note(stringResource(R.string.setm_mcp_created), live = true)
        SelectionContainer { Mono(token) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = {
                copy(context, token)
                copied = true
            }) {
                ZankupIcon(if (copied) ZIcon.Check else ZIcon.Copy, contentDescription = null, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (copied) R.string.setm_common_copied else R.string.setm_common_copy))
            }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = { share(context, token) }) {
                ZankupIcon(ZIcon.Share, contentDescription = null, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.setm_mobile_share))
            }
        }
    }
}

/** Copies a secret, marked sensitive so Android 13+ does not preview it. */
private fun copy(context: Context, value: String) {
    val clip = ClipData.newPlainText("Zankup", value)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

private fun share(context: Context, value: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, value)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
