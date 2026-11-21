package com.zankup.android.settings.basic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.KeysApi
import com.zankup.android.api.model.InstanceOut
import com.zankup.android.api.model.KeyIn
import com.zankup.android.api.model.KeyStatus
import com.zankup.android.api.model.Preferences
import com.zankup.android.api.model.SettingOut
import com.zankup.android.data.CacheKeys
import com.zankup.android.data.Store
import com.zankup.android.data.UserSettings
import com.zankup.android.designsystem.components.RowDivider
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import com.zankup.android.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PATCH

// ============================================================================================
// Data shared by the sections (and by settings.more): the settings, the instance, the keys.
// ============================================================================================

/** `PATCH /api/settings` with a JSON object, so a null (clearing a value) reaches the API. */
internal interface SettingsPatchApi {
    @PATCH("api/settings")
    suspend fun update(@Body changes: JsonObject): Response<Preferences>
}

/**
 * Saves some of the person's settings (`PATCH /api/settings`) and keeps the answer as
 * [UserSettings.query]'s, so every screen (units, zones…) switches at once. Pass only the fields
 * that change, with `JsonNull` to clear one.
 */
@Singleton
class SettingsWriter
@Inject
constructor(
    private val store: Store,
    private val source: ApiSource,
    val settings: UserSettings
) {
    suspend fun save(changes: JsonObject): ApiResult<Preferences> {
        val api = source.api ?: return ApiResult.SignedOut
        val result = call { api.service<SettingsPatchApi>().update(changes) }
        if (result is ApiResult.Ok) store.put(CacheKeys.SETTINGS, Preferences.serializer(), result.value)
        return result
    }
}

const val INSTANCE_KEY = "settings/instance"
const val KEYS_KEY = "settings/keys"

/** One setting of the instance by its name (`alert_email`, `backup_hour_utc`…). */
fun InstanceOut.setting(name: String): SettingOut? = settings.firstOrNull { it.name == name }

/** A setting's value as text ("" when unset). */
val SettingOut.text: String
    get() = (value as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content.orEmpty()

/** A setting's value as a whole number, if it is one. */
val SettingOut.number: Int?
    get() = (value as? JsonPrimitive)?.takeUnless { it is JsonNull }
        ?.let { it.intOrNull ?: it.content.toDoubleOrNull()?.toInt() }

/** A setting's value as a switch. */
val SettingOut.on: Boolean
    get() = (value as? JsonPrimitive)?.takeUnless { it is JsonNull }?.booleanOrNull ?: false

/**
 * The instance's settings saved from the app (`GET/PATCH /api/instance`): alerts, backups, the
 * Coach's model and budget, Garmin's sync interval, Google Health. Each card saves its own fields; a
 * value the server refuses comes back as a [ApiResult.Http] with its message.
 */
@HiltViewModel
class InstanceModel
@Inject
constructor(private val store: Store, private val source: ApiSource) : ViewModel() {
    val instance = store.query(INSTANCE_KEY, InstanceOut.serializer()) { api ->
        call { api.service<KeysApi>().getInstance() }
    }

    init {
        viewModelScope.launch { instance.refresh() }
    }

    suspend fun save(changes: Map<String, JsonElement>): ApiResult<InstanceOut> {
        val api = source.api ?: return ApiResult.SignedOut
        val result = call { api.service<KeysApi>().updateInstance(changes) }
        if (result is ApiResult.Ok) store.put(INSTANCE_KEY, InstanceOut.serializer(), result.value)
        return result
    }
}

/** The service keys (`GET /api/keys`, `PUT/DELETE /api/keys/{name}`): only their last characters come back. */
@HiltViewModel
class ServiceKeysModel
@Inject
constructor(private val store: Store, private val source: ApiSource) : ViewModel() {
    private val serializer = ListSerializer(KeyStatus.serializer())

    val keys = store.query(KEYS_KEY, serializer) { api -> call { api.service<KeysApi>().listKeys() } }

    init {
        viewModelScope.launch { keys.refresh() }
    }

    /** Saves [value] (tried by the server first); [expiresOn] is an ISO day, Garmin only. */
    suspend fun save(name: KeyStatus.Name, value: String, expiresOn: String?): ApiResult<KeyStatus> {
        val api = source.api ?: return ApiResult.SignedOut
        return keep(call { api.service<KeysApi>().saveKey(name.value, KeyIn(value, expiresOn)) })
    }

    suspend fun remove(name: KeyStatus.Name): ApiResult<KeyStatus> {
        val api = source.api ?: return ApiResult.SignedOut
        return keep(call { api.service<KeysApi>().deleteKey(name.value) })
    }

    private suspend fun keep(result: ApiResult<KeyStatus>): ApiResult<KeyStatus> {
        if (result is ApiResult.Ok) {
            val list = keys.state.first().data.orEmpty()
            val next = if (list.any { it.name == result.value.name }) {
                list.map { if (it.name == result.value.name) result.value else it }
            } else {
                list + result.value
            }
            store.put(KEYS_KEY, serializer, next)
        }
        return result
    }
}

// ============================================================================================
// UI shared by the sections
// ============================================================================================

/**
 * The scrolling body of a section, under [SettingsSectionScreen]'s top bar: 16 dp around, 12 dp between
 * cards, as the React Native app's screens. Every section (and every slot) draws its content with it.
 */
@Composable
fun SettingsBody(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ZankupTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.sm),
        content = content
    )
}

/** A card's heading, as the React Native app's `Heading`: 17 sp, bold. */
val headingStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold)

/** A muted note, as the React Native app's `Muted`: 13 sp. */
val noteStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp)

/** A card with an optional title, its content 8 dp apart (the React Native app's `Card`). */
@Composable
fun SettingsCard(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ZankupCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = headingStyle, color = ZankupTheme.colors.fg)
            content()
        }
    }
}

/**
 * A card of rows (`ListRow`s) edge to edge, a hairline between them inset from the sides, as the React
 * Native app's menu rows.
 */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, rows: List<@Composable () -> Unit>) {
    val colors = ZankupTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        contentColor = colors.fg,
        border = if (colors.line == colors.fg) BorderStroke(2.dp, colors.line) else null
    ) {
        Column {
            rows.forEachIndexed { i, row ->
                if (i > 0) {
                    RowDivider()
                }
                row()
            }
        }
    }
}

/** A muted line of help; [live] reads it out when it appears (results, errors). */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier, live: Boolean = false) {
    Text(
        text,
        style = noteStyle,
        color = ZankupTheme.colors.muted,
        modifier = if (live) modifier.semantics { liveRegion = LiveRegionMode.Polite } else modifier
    )
}

/** A heading inside a card ("Units", "Appearance"), 8 dp more above it than the card's gap. */
@Composable
fun SettingsLabel(text: String) {
    Text(text, style = headingStyle, color = ZankupTheme.colors.fg, modifier = Modifier.padding(top = 8.dp))
}

/** A label with a switch; the whole row toggles it. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    note: String? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.m)
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = ZankupTheme.colors.fg)
            if (note != null) SettingsNote(note)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A failure as text: the API's own message when it sent one, else the generic one. */
@Composable
fun failureText(failure: ApiResult.Failure): String =
    (failure as? ApiResult.Http)?.detail ?: stringResource(R.string.set_error)

/** "From the server" under a value that is not the app's (an environment variable). */
@Composable
fun SettingOrigin(setting: SettingOut?) {
    if (setting?.source == SettingOut.Source.ENV) SettingsNote(stringResource(R.string.set_from_server))
}

/** Save, disabled until something changed; "Checking…" while it saves, then the error if any. */
@Composable
fun SaveButton(dirty: Boolean, pending: Boolean, onSave: () -> Unit, error: String? = null) {
    Button(enabled = dirty && !pending, onClick = onSave, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (pending) R.string.set_checking else R.string.set_save))
    }
    if (error != null) SettingsNote(error, live = true)
}

/** An hour of the day with arrows, shown in the phone's time and kept in UTC. */
@Composable
fun HourStepper(label: String, utc: Int, onChange: (Int) -> Unit) {
    val offset = remember { offsetHours() }
    val local = utcToLocalHour(utc, offset)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = ZankupTheme.colors.fg)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { onChange(localToUtcHour(local - 1, offset)) }) {
                ZankupIcon(ZIcon.ChevronLeft, stringResource(R.string.set_hour_earlier, label))
            }
            Text(
                hourLabel(local),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ZankupTheme.colors.fg
            )
            IconButton(onClick = { onChange(localToUtcHour(local + 1, offset)) }) {
                ZankupIcon(ZIcon.Chevron, stringResource(R.string.set_hour_later, label))
            }
        }
    }
}

/**
 * A small round label, as the React Native app's `Badge`: the accent when [good], zone 5's red with white
 * when not (false), plain when null.
 */
@Composable
fun SettingsBadge(text: String, good: Boolean?) {
    val colors = ZankupTheme.colors
    Surface(
        shape = CircleShape,
        color = when (good) {
            true -> colors.accent
            false -> colors.zones[4]
            null -> colors.surface2
        },
        contentColor = when (good) {
            true -> colors.accentFg
            false -> Color.White
            null -> colors.fg
        }
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * A service key (Garmin, Anthropic, Resend, the backups' S3): whether it is set and where from, only
 * its last characters, a hidden field to paste a new one (and for Garmin the day it expires), Save and,
 * when it was saved from the apps, Remove. The server tries a key before saving it.
 */
@Composable
fun ServiceKey(name: KeyStatus.Name, model: ServiceKeysModel = hiltViewModel()) {
    val state by model.keys.state.collectAsStateWithLifecycle(initialValue = null)
    val key = state?.data?.firstOrNull { it.name == name } ?: return
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf("") }
    var expires by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidDate = stringResource(R.string.set_invalid_date)
    val genericError = stringResource(R.string.set_error)
    fun message(r: ApiResult<*>) = ((r as? ApiResult.Http)?.detail ?: genericError).takeIf { r is ApiResult.Failure }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val last4 = key.last4.orEmpty()
            SettingsBadge(
                when {
                    !key.configured -> stringResource(R.string.set_key_not_set)
                    key.source == KeyStatus.Source.ENV -> stringResource(R.string.set_key_from_server, last4)
                    else -> stringResource(R.string.set_key_from_app, last4)
                },
                good = key.configured
            )
            key.expiresOn?.let { SettingsNote(stringResource(R.string.set_key_expires, toDayMonthYear(it))) }
        }
        if (key.source == KeyStatus.Source.ENV) SettingsNote(stringResource(R.string.set_key_env_note))
        OutlinedTextField(
            value,
            { value = it },
            label = {
                Text(stringResource(if (key.configured) R.string.set_key_replace else R.string.set_key_paste))
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        if (name == KeyStatus.Name.GARMIN) {
            OutlinedTextField(
                expires,
                { expires = typeDayMonthYear(it) },
                label = { Text(stringResource(R.string.set_key_expires_on)) },
                placeholder = { Text(stringResource(R.string.set_date_format)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = value.trim().length >= 8 && !busy,
                onClick = {
                    val expiresOn = expires.takeIf { it.isNotEmpty() }?.let(::fromDayMonthYear)
                    if (expires.isNotEmpty() && expiresOn == null) {
                        error = invalidDate
                        return@Button
                    }
                    busy = true
                    error = null
                    scope.launch {
                        val r = model.save(name, value.trim(), expiresOn)
                        if (r is ApiResult.Ok) {
                            value = ""
                            expires = ""
                        }
                        error = message(r)
                        busy = false
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(if (busy) R.string.set_checking else R.string.set_save)) }
            if (key.source == KeyStatus.Source.APP) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            error = message(model.remove(name))
                            busy = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.set_key_remove)) }
            }
        }
        error?.let { SettingsNote(it, live = true) }
    }
}
