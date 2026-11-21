package com.zankup.android.signin

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.designsystem.brand.Mark
import com.zankup.android.designsystem.brand.Wordmark
import com.zankup.android.designsystem.components.SectionDivider
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.session.Prompter
import kotlinx.coroutines.launch

/** Signing in and creating the first account: one screen, one step at a time. */
@Composable
fun SignInScreen(viewModel: SignInViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as FragmentActivity
    val prompter = remember(activity) { Prompter(activity) }
    val credentials = remember(activity) { Credentials(activity) }
    val colors = ZankupTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.page)
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val formEnd = remember { FormEnd() }
        CompositionLocalProvider(LocalFormEnd provides formEnd) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ZankupTheme.spacing.l, vertical = ZankupTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.m)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Mark(height = 32.dp, color = colors.accent)
                    Wordmark(height = 20.dp)
                }
                when (ui.step) {
                    Step.INSTANCE -> InstanceStep(ui, viewModel)
                    Step.PASSWORD -> PasswordStep(ui, viewModel, onGoogle = {
                        viewModel.google(credentials, prompter)
                    }) {
                        viewModel.passkey(credentials, prompter)
                    }
                    Step.CODE -> CodeStep(ui, viewModel) { viewModel.submitCode(prompter) }
                    Step.FORGOT -> ForgotStep(ui, viewModel)
                    Step.SETUP_ACCOUNT -> SetupAccountStep(ui, viewModel)
                    Step.SETUP_TOTP -> SetupTotpStep(ui, viewModel)
                    Step.SETUP_CODES -> SetupCodesStep(ui, viewModel) { viewModel.finishSetup(prompter) }
                }
                ui.error?.let { ErrorText(it) }
                if (ui.notKept) {
                    Text(
                        stringResource(R.string.signin_not_kept),
                        color = colors.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // The end of the form: a focused field brings everything down to here above the keyboard.
                Spacer(Modifier.height(ZankupTheme.spacing.s).formEnd())
            }
        }
    }
}

@Composable
private fun Title(title: String, sub: String? = null) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.xs),
        modifier = Modifier.padding(top = ZankupTheme.spacing.l)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = ZankupTheme.colors.fg)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = ZankupTheme.colors.muted)
    }
}

@Composable
private fun ErrorText(message: Message) {
    val text =
        when (message) {
            is Message.Res -> stringResource(message.id, *message.args.toTypedArray())
            is Message.Text -> message.text
        }
    Text(text, color = ZankupTheme.colors.formNegative, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Primary(text: String, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(2.dp)) else Text(text)
    }
}

@Composable
private fun ColumnScope.InstanceStep(ui: SignInUi, vm: SignInViewModel) {
    Title(stringResource(R.string.signin_instance_title))
    Field(
        value = ui.instanceInput,
        onValue = vm::onInstance,
        label = stringResource(R.string.signin_instance),
        keyboard = KeyboardType.Uri,
        imeAction = ImeAction.Go,
        onDone = vm::submitInstance,
        help = stringResource(R.string.signin_instance_help)
    )
    Primary(stringResource(R.string.signin_continue), ui.busy, vm::submitInstance)
}

@Composable
private fun ColumnScope.PasswordStep(ui: SignInUi, vm: SignInViewModel, onGoogle: () -> Unit, onPasskey: () -> Unit) {
    Title(stringResource(R.string.signin_welcome), stringResource(R.string.signin_instance_is, ui.instance?.host ?: ""))
    if (ui.googleClientId != null) {
        OutlinedButton(
            onClick = onGoogle,
            enabled = !ui.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            Text(stringResource(R.string.signin_google))
        }
    }
    OutlinedButton(onClick = onPasskey, enabled = !ui.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.signin_passkey))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionDivider(Modifier.weight(1f))
        Text(
            stringResource(R.string.signin_or),
            color = ZankupTheme.colors.muted,
            modifier = Modifier.padding(horizontal = ZankupTheme.spacing.m)
        )
        SectionDivider(Modifier.weight(1f))
    }
    Field(
        value = ui.email,
        onValue = vm::onEmail,
        label = stringResource(R.string.signin_email),
        keyboard = KeyboardType.Email,
        contentType = ContentType.Username + ContentType.EmailAddress
    )
    Field(
        value = ui.password,
        onValue = vm::onPassword,
        label = stringResource(R.string.signin_password),
        keyboard = KeyboardType.Password,
        imeAction = ImeAction.Go,
        onDone = vm::submitPassword,
        contentType = ContentType.Password,
        secret = true,
        shown = ui.showPassword,
        onToggle = vm::togglePassword
    )
    Primary(stringResource(R.string.signin_submit), ui.busy, vm::submitPassword)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = vm::forgot) { Text(stringResource(R.string.signin_forgot)) }
        TextButton(onClick = vm::changeInstance) { Text(stringResource(R.string.signin_change_instance)) }
    }
}

@Composable
private fun ColumnScope.CodeStep(ui: SignInUi, vm: SignInViewModel, onSubmit: () -> Unit) {
    Title(stringResource(R.string.signin_code_title), stringResource(R.string.signin_code_sub))
    CodeBoxes(ui.code, vm::onCode, onComplete = { if (!ui.busy) onSubmit() })
    Primary(stringResource(R.string.signin_verify), ui.busy, onSubmit)
    TextButton(onClick = vm::backToPassword) { Text(stringResource(R.string.signin_back)) }
}

@Composable
private fun ColumnScope.ForgotStep(ui: SignInUi, vm: SignInViewModel) {
    Title(stringResource(R.string.signin_forgot_title), stringResource(R.string.signin_forgot_sub))
    if (ui.forgotSent) {
        Text(stringResource(R.string.signin_forgot_sent), color = ZankupTheme.colors.fg)
        Text(
            stringResource(R.string.signin_forgot_web),
            color = ZankupTheme.colors.muted,
            style = MaterialTheme.typography.bodyMedium
        )
    } else {
        Field(
            value = ui.email,
            onValue = vm::onEmail,
            label = stringResource(R.string.signin_email),
            keyboard = KeyboardType.Email,
            imeAction = ImeAction.Go,
            onDone = vm::submitForgot,
            contentType = ContentType.EmailAddress
        )
        Primary(stringResource(R.string.signin_forgot_send), ui.busy, vm::submitForgot)
    }
    TextButton(onClick = vm::backToPassword) { Text(stringResource(R.string.signin_back)) }
}

@Composable
private fun ColumnScope.SetupAccountStep(ui: SignInUi, vm: SignInViewModel) {
    Title(stringResource(R.string.setup_title), stringResource(R.string.setup_sub))
    Field(
        value = ui.setupCode,
        onValue = vm::onSetupCode,
        label = stringResource(R.string.setup_code),
        help = stringResource(R.string.setup_code_help)
    )
    Field(
        value = ui.name,
        onValue = vm::onName,
        label = stringResource(R.string.setup_name),
        contentType = ContentType.PersonFullName
    )
    Field(
        value = ui.email,
        onValue = vm::onEmail,
        label = stringResource(R.string.signin_email),
        keyboard = KeyboardType.Email,
        contentType = ContentType.EmailAddress
    )
    Field(
        value = ui.password,
        onValue = vm::onPassword,
        label = stringResource(R.string.signin_password),
        keyboard = KeyboardType.Password,
        imeAction = ImeAction.Done,
        contentType = ContentType.NewPassword,
        help = stringResource(R.string.setup_password_help),
        secret = true,
        shown = ui.showPassword,
        onToggle = vm::togglePassword
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.m)
    ) {
        Text(
            stringResource(R.string.setup_accept),
            style = MaterialTheme.typography.bodyMedium,
            color = ZankupTheme.colors.fg,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = ui.accept, onCheckedChange = vm::onAccept)
    }
    Primary(stringResource(R.string.signin_continue), ui.busy, vm::submitAccount)
    TextButton(onClick = vm::changeInstance) { Text(stringResource(R.string.signin_change_instance)) }
}

@Composable
private fun ColumnScope.SetupTotpStep(ui: SignInUi, vm: SignInViewModel) {
    val activity = LocalActivity.current
    var noApp by remember { mutableStateOf(false) }
    Title(stringResource(R.string.setup_totp_title), stringResource(R.string.setup_totp_sub))
    OutlinedButton(
        onClick = {
            try {
                activity?.startActivity(Intent(Intent.ACTION_VIEW, (ui.totpUri ?: "").toUri()))
            } catch (_: ActivityNotFoundException) {
                noApp = true
            }
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) { Text(stringResource(R.string.setup_open_authenticator)) }
    if (noApp) Text(stringResource(R.string.setup_no_authenticator), color = ZankupTheme.colors.muted)
    Text(
        stringResource(R.string.setup_type_key),
        color = ZankupTheme.colors.muted,
        style = MaterialTheme.typography.bodyMedium
    )
    SelectionContainer {
        Text(
            Rules.groupKey(ui.totpSecret ?: ""),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.titleLarge,
            color = ZankupTheme.colors.fg,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    CodeBoxes(ui.code, vm::onCode, onComplete = { if (!ui.busy) vm.submitSetupCode() })
    Primary(stringResource(R.string.setup_turn_on), ui.busy, vm::submitSetupCode)
}

@Composable
private fun ColumnScope.SetupCodesStep(ui: SignInUi, vm: SignInViewModel, onFinish: () -> Unit) {
    val clipboard = LocalClipboard.current
    val activity = LocalActivity.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val codes = ui.recoveryCodes.joinToString("\n")
    Title(stringResource(R.string.setup_codes_title), stringResource(R.string.setup_codes_sub))
    SelectionContainer {
        Text(
            codes,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.titleMedium,
            color = ZankupTheme.colors.fg,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.s)) {
        OutlinedButton(onClick = {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Zankup", codes))) }
        }) {
            Text(stringResource(R.string.setup_copy))
        }
        OutlinedButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, codes)
            activity?.startActivity(Intent.createChooser(send, null))
        }) { Text(stringResource(R.string.setup_share)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.setup_load_history), color = ZankupTheme.colors.fg, modifier = Modifier.weight(1f))
        Switch(checked = ui.loadHistory, onCheckedChange = vm::onLoadHistory)
    }
    Primary(stringResource(R.string.setup_go), ui.busy, onFinish)
}
