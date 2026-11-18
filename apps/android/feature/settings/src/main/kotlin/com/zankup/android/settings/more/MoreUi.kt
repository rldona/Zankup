package com.zankup.android.settings.more

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.zankup.android.api.model.KeyStatus
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.network.ApiResult
import com.zankup.android.settings.R
import com.zankup.android.settings.basic.SettingsBadge
import com.zankup.android.settings.basic.SettingsCard
import com.zankup.android.settings.basic.SettingsNote
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

// Small pieces shared by the sections of this package. Private to it on purpose: basic/Common.kt has
// the module's public ones.

@Composable
internal fun MoreCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    SettingsCard(title, content = content)
}

@Composable
internal fun Note(text: String, live: Boolean = false) {
    SettingsNote(text, live = live)
}

@Composable
internal fun Mono(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        color = ZankupTheme.colors.fg
    )
}

/** A label with a switch, the whole row being one control for TalkBack. */
@Composable
internal fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) ZankupTheme.colors.fg else ZankupTheme.colors.muted,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** A list row with what to do on the right ("Revoke", "Remove", a badge), as the React Native app's `ListRow`. */
@Composable
internal fun ItemRow(title: String, note: String?, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = ZankupTheme.colors.fg
            )
            if (note != null) Note(note)
        }
        trailing()
    }
}

@Composable
internal fun Badge(text: String, good: Boolean? = null) = SettingsBadge(text, good)

/** A destructive action's confirmation: Cancel, and the action in red. */
@Composable
internal fun Confirm(title: String, text: String?, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(action, color = ZankupTheme.colors.formNegative) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.setm_common_cancel)) } },
        containerColor = ZankupTheme.colors.surface
    )
}

/** The API's own words when it gave any, else the generic error. */
@Composable
internal fun failureText(failure: ApiResult.Failure?): String =
    (failure as? ApiResult.Http)?.detail ?: stringResource(R.string.setm_common_error)

/**
 * A service key (Anthropic's, Garmin's): whether it is set and where from, a hidden field for a new one
 * and, for Garmin, the day it expires. [save] answers null when saved, else what to show.
 */
@Composable
internal fun KeyEditor(
    key: KeyStatus,
    save: suspend (value: String, expiresOn: String?) -> ApiResult<KeyStatus>,
    remove: suspend () -> ApiResult<KeyStatus>
) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf("") }
    var expires by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<ApiResult.Failure?>(null) }
    val invalidDate = stringResource(R.string.setm_mobile_invalidDate)
    val last = MoreLogic.lastChars(key.last4)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Badge(
            if (key.configured) {
                stringResource(
                    if (key.source ==
                        KeyStatus.Source.ENV
                    ) {
                        R.string.setm_keys_fromServer
                    } else {
                        R.string.setm_keys_fromApp
                    },
                    last
                )
            } else {
                stringResource(R.string.setm_keys_notSet)
            },
            good = key.configured
        )
        key.expiresOn?.let { Note(stringResource(R.string.setm_keys_expires, MoreLogic.toDayMonthYear(it))) }
    }
    if (key.source == KeyStatus.Source.ENV) Note(stringResource(R.string.setm_keys_envNote))
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(stringResource(if (key.configured) R.string.setm_keys_replace else R.string.setm_keys_paste)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth()
    )
    if (key.name == KeyStatus.Name.GARMIN) {
        OutlinedTextField(
            value = expires,
            onValueChange = { expires = MoreLogic.typeDayMonthYear(it) },
            label = { Text(stringResource(R.string.setm_keys_expiresOn)) },
            placeholder = { Text(stringResource(R.string.setm_mobile_dateFormat)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            enabled = MoreLogic.canSaveKey(value) && !busy,
            modifier = Modifier.weight(1f),
            onClick = {
                error = null
                failure = null
                val expiresOn = expires.takeIf { it.isNotBlank() }?.let(MoreLogic::fromDayMonthYear)
                if (expires.isNotBlank() && expiresOn == null) {
                    error = invalidDate
                    return@Button
                }
                busy = true
                scope.launch {
                    when (val r = save(value, expiresOn)) {
                        is ApiResult.Ok -> {
                            value = ""
                            expires = ""
                        }
                        is ApiResult.Failure -> failure = r
                    }
                    busy = false
                }
            }
        ) {
            Text(stringResource(if (busy) R.string.setm_keys_checking else R.string.setm_settings_save))
        }
        if (key.source == KeyStatus.Source.APP) {
            OutlinedButton(enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                error = null
                failure = null
                busy = true
                scope.launch {
                    (remove() as? ApiResult.Failure)?.let { failure = it }
                    busy = false
                }
            }) { Text(stringResource(R.string.setm_keys_remove)) }
        }
    }
    error?.let { Note(it, live = true) }
    failure?.let { Note(failureText(it), live = true) }
}

/** An hour of the day with arrows, shown in the phone's time and kept in UTC. */
@Composable
internal fun HourStepper(label: String, utc: Int, onChange: (Int) -> Unit) {
    val offset = localOffsetHours()
    val local = MoreLogic.toLocal(utc, offset)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = ZankupTheme.colors.fg)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = { onChange(MoreLogic.toUtc(local - 1, offset)) }) {
                ZankupIcon(ZIcon.ChevronLeft, contentDescription = "$label -1")
            }
            Text(
                MoreLogic.hourLabel(local),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ZankupTheme.colors.fg
            )
            IconButton(onClick = { onChange(MoreLogic.toUtc(local + 1, offset)) }) {
                ZankupIcon(ZIcon.Chevron, contentDescription = "$label +1")
            }
        }
    }
}

/** The phone's offset from UTC now, in whole hours (the API keeps whole hours). */
internal fun localOffsetHours(): Int = ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds / 3600

private val DAY = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val MOMENT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

/** An API date or timestamp as a medium date ("14 Aug 2026"); as it came when it cannot be read. */
internal fun day(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DAY)
}.recoverCatching { LocalDate.parse(iso.take(10)).format(DAY) }.getOrDefault(iso)

internal fun moment(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(MOMENT)
}.getOrDefault(iso)

/** The activity behind a Compose context (the biometric prompt and Credential Manager need it). */
internal tailrec fun Context.findActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
