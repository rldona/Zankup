package com.zankup.android.settings.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.KeyStatus
import com.zankup.android.api.model.Me
import com.zankup.android.api.model.SettingOut
import com.zankup.android.api.model.SourceStatus
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.network.ApiResult
import com.zankup.android.session.BiometricLevel
import com.zankup.android.settings.R
import kotlinx.coroutines.launch

/**
 * Settings › Account (OS-1161), as the React Native app has it: the sign-in methods, passkeys, the app
 * lock, Garmin, the signed-in devices and "Sign out". A body for the section screen; [onSignedOut] runs
 * once this phone is signed out.
 */
@Composable
fun AccountSection(onSignedOut: () -> Unit, model: AccountModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    val me by model.me.state.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(Unit) { model.refresh() }
    var signingOut by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val person = me?.data
        when {
            person != null -> SignInMethods(person, model)
            me?.failure != null -> Note(failureText(me?.failure))
            else -> Skeleton(Modifier.fillMaxWidth(), height = 120.dp)
        }
        Passkeys(model)
        Security(model)
        Garmin(model)
        Sessions(model)
        Button(enabled = !signingOut, modifier = Modifier.fillMaxWidth(), onClick = {
            signingOut = true
            scope.launch {
                model.signOut()
                onSignedOut()
            }
        }) { Text(stringResource(R.string.setm_nav_signOut)) }
    }
}

@Composable
private fun SignInMethods(me: Me, model: AccountModel) {
    val scope = rememberCoroutineScope()
    val providers by model.providers.state.collectAsStateWithLifecycle(initialValue = null)
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<UnlinkOutcome?>(null) }
    MoreCard(stringResource(R.string.setm_profile_signIn)) {
        SelectionContainer {
            Text(
                me.email,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = ZankupTheme.colors.fg
            )
        }
        Note(
            stringResource(R.string.setm_profile_passwordTotp) + ": " +
                if (me.totpEnabled) stringResource(R.string.setm_profile_on) else "—"
        )
        if (providers?.data?.google == true) {
            Text(
                stringResource(R.string.setm_profile_google),
                style = MaterialTheme.typography.titleSmall,
                color = ZankupTheme.colors.fg
            )
            if (me.googleLinked == true) {
                Note(stringResource(R.string.setm_profile_googleLinked))
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    outcome = null
                    scope.launch {
                        outcome = model.unlinkGoogle(me)
                        busy = false
                    }
                }) { Text(stringResource(R.string.setm_profile_googleUnlink)) }
            } else {
                Note(stringResource(R.string.setm_mobile_googleLinkOnWeb))
            }
            when (outcome) {
                UnlinkOutcome.NeedsOther -> Note(stringResource(R.string.setm_profile_googleNeedsOther), live = true)
                UnlinkOutcome.Failed -> Note(stringResource(R.string.setm_common_error), live = true)
                else -> Unit
            }
        }
    }
}

@Composable
private fun Passkeys(model: AccountModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val passkeys by model.passkeys.state.collectAsStateWithLifecycle(initialValue = null)
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<PasskeyOutcome?>(null) }
    var removing by remember { mutableStateOf<Pair<Int, String>?>(null) }
    MoreCard(stringResource(R.string.setm_profile_passkeys)) {
        Note(stringResource(R.string.setm_profile_passkeysHelp))
        Note(stringResource(R.string.setm_mobile_passkeysOnWeb))
        val list = passkeys?.data
        when {
            list == null && passkeys?.failure != null -> Note(failureText(passkeys?.failure))
            list == null -> Skeleton(Modifier.fillMaxWidth(), height = 48.dp)
            list.isEmpty() -> Note(stringResource(R.string.setm_profile_noPasskeys))
            else -> list.forEach { p ->
                val used = p.lastUsedAt?.let { stringResource(R.string.setm_profile_lastUsed, day(it)) }
                    ?: stringResource(R.string.setm_profile_neverUsed)
                ItemRow(p.name, stringResource(R.string.setm_profile_passkeyMeta, day(p.createdAt), used)) {
                    OutlinedButton(enabled = !busy, onClick = { removing = p.id to p.name }) {
                        Text(stringResource(R.string.setm_profile_removePasskey))
                    }
                }
            }
        }
        if (model.passkeysSupported) {
            OutlinedButton(enabled = !busy, onClick = {
                val activity = context.findActivity() ?: return@OutlinedButton
                busy = true
                outcome = null
                scope.launch {
                    outcome = model.addPasskey(activity)
                    busy = false
                }
            }) { Text(stringResource(R.string.setm_profile_addPasskey)) }
        }
        when (outcome) {
            PasskeyOutcome.Added -> Note(stringResource(R.string.setm_profile_passkeyAdded), live = true)
            PasskeyOutcome.Exists -> Note(stringResource(R.string.setm_profile_passkeyExists), live = true)
            PasskeyOutcome.Failed -> Note(stringResource(R.string.setm_profile_passkeyFailed), live = true)
            else -> Unit
        }
    }
    removing?.let { (id, name) ->
        Confirm(
            title = name,
            text = stringResource(R.string.setm_profile_removePasskey),
            action = stringResource(R.string.setm_profile_removePasskey),
            onConfirm = {
                busy = true
                scope.launch {
                    model.removePasskey(id)
                    busy = false
                }
            },
            onDismiss = { removing = null }
        )
    }
}

@Composable
private fun Security(model: AccountModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val level = remember { model.biometrics() }
    var lock by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { lock = model.lock() }
    val (on, weak) = lock ?: return

    // The session is sealed again to match, which may ask for the fingerprint; if that is cancelled,
    // nothing changes and the switch stays as it was.
    fun set(biometric: Boolean, weakLock: Boolean) {
        val activity = context.findActivity() ?: return
        busy = true
        failed = false
        scope.launch {
            if (model.setLock(biometric, weakLock, activity)) lock = biometric to weakLock else failed = true
            busy = false
        }
    }
    MoreCard(stringResource(R.string.setm_mobile_security)) {
        if (on || level == BiometricLevel.NONE) {
            Note(
                stringResource(
                    when (level) {
                        BiometricLevel.STRONG -> R.string.setm_mobile_biometricsStrong
                        BiometricLevel.WEAK -> R.string.setm_mobile_biometricsWeak
                        BiometricLevel.NONE -> R.string.setm_mobile_biometricsNone
                    }
                )
            )
        }
        if (level != BiometricLevel.NONE) {
            SwitchLine(stringResource(R.string.setm_mobile_biometricLock), on, { set(it, weak) }, enabled = !busy)
        }
        if (!on) Note(stringResource(R.string.setm_mobile_biometricLockOff))
        if (failed) Note(stringResource(R.string.setm_common_error), live = true)
        if (level == BiometricLevel.WEAK && on) {
            SwitchLine(stringResource(R.string.setm_mobile_weakLock), weak, { set(on, it) }, enabled = !busy)
        }
        Note(stringResource(R.string.setm_mobile_instanceIs, model.instanceUrl))
    }
}

@Composable
private fun Garmin(model: AccountModel) {
    val sources by model.sources.state.collectAsStateWithLifecycle(initialValue = null)
    val keys by model.service.keys.collectAsStateWithLifecycle()
    val garmin = sources?.data?.firstOrNull { it.kind == SourceStatus.Kind.GARMIN } ?: return
    val key = keys.data?.firstOrNull { it.name == KeyStatus.Name.GARMIN }
    MoreCard(stringResource(R.string.setm_settings_garmin)) {
        val expires = garmin.keyExpiresOn
        if (garmin.expiringSoon && expires != null) {
            Note(stringResource(R.string.setm_settings_garminSoon, day(expires)), live = true)
        }
        val synced = garmin.lastSyncAt?.let { stringResource(R.string.setm_settings_lastSync, moment(it)) }
            ?: stringResource(R.string.setm_settings_never)
        val error = garmin.lastError?.let { " " + stringResource(R.string.setm_settings_lastError, it) }.orEmpty()
        Note(synced + error)
        SyncInterval(model)
        Note(stringResource(R.string.setm_keys_garminHelp))
        if (key != null) KeyEditor(key, model::saveGarminKey) { model.deleteGarminKey() }
    }
}

@Composable
private fun SyncInterval(model: AccountModel) {
    val scope = rememberCoroutineScope()
    val instance by model.service.instance.collectAsStateWithLifecycle()
    val setting = instance.data?.settings?.firstOrNull { it.name == "sync_interval_minutes" } ?: return
    val current = MoreLogic.number(setting.value)?.toInt() ?: 0
    var failure by remember { mutableStateOf<ApiResult.Failure?>(null) }
    val off = stringResource(R.string.setm_instance_syncOff)
    Note(stringResource(R.string.setm_instance_syncEvery))
    ChipPicker(
        options = MoreLogic.intervalOptions(current),
        selected = current,
        label = { MoreLogic.intervalLabel(it) ?: off },
        onSelect = { minutes ->
            failure = null
            scope.launch { (model.setInterval(minutes) as? ApiResult.Failure)?.let { failure = it } }
        }
    )
    if (setting.source == SettingOut.Source.ENV) {
        Note(stringResource(R.string.setm_instance_fromServer))
    }
    failure?.let { Note(failureText(it), live = true) }
}

@Composable
private fun Sessions(model: AccountModel) {
    val scope = rememberCoroutineScope()
    val devices by model.devices.state.collectAsStateWithLifecycle(initialValue = null)
    var busy by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf<Pair<Int, String>?>(null) }
    MoreCard(stringResource(R.string.setm_settings_sessions)) {
        val list = devices?.data
        when {
            list == null && devices?.failure != null -> Note(failureText(devices?.failure))
            list == null -> Skeleton(Modifier.fillMaxWidth(), height = 48.dp)
            else -> list.forEach { d ->
                val platform = stringResource(
                    if (d.platform ==
                        "android"
                    ) {
                        R.string.setm_settings_platform_android
                    } else {
                        R.string.setm_settings_platform_web
                    }
                )
                val seen = d.lastSeen?.let {
                    " · " + stringResource(R.string.setm_settings_lastSeen, day(it))
                }.orEmpty()
                ItemRow(d.name, platform + seen) {
                    if (d.current) {
                        Badge(stringResource(R.string.setm_settings_thisDevice))
                    } else {
                        OutlinedButton(enabled = !busy, onClick = { closing = d.id to d.name }) {
                            Text(stringResource(R.string.setm_settings_signOutDevice))
                        }
                    }
                }
            }
        }
    }
    closing?.let { (id, name) ->
        Confirm(
            title = name,
            text = null,
            action = stringResource(R.string.setm_settings_signOutDevice),
            onConfirm = {
                busy = true
                scope.launch {
                    model.signOutDevice(id)
                    busy = false
                }
            },
            onDismiss = { closing = null }
        )
    }
}
