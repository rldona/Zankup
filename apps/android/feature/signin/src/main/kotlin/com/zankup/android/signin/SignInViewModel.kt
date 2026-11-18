package com.zankup.android.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.AuthApi
import com.zankup.android.api.endpoints.DefaultApi
import com.zankup.android.api.endpoints.SyncApi
import com.zankup.android.api.model.AccountRequest
import com.zankup.android.api.model.AppTokens
import com.zankup.android.api.model.ForgotRequest
import com.zankup.android.api.model.MobileSignIn
import com.zankup.android.api.model.SyncRequest
import com.zankup.android.api.model.TotpConfirm
import com.zankup.android.api.model.TotpRequest
import com.zankup.android.api.model.ZankupAuthPasskeysLoginRequest
import com.zankup.android.api.model.ZankupAuthRoutesLoginRequest
import com.zankup.android.network.ApiResult
import com.zankup.android.network.ZankupApi
import com.zankup.android.network.ZankupJson
import com.zankup.android.network.call
import com.zankup.android.session.Prompter
import com.zankup.android.session.Session
import com.zankup.android.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Signing in (OS-1110): the instance, then Google, a passkey or e-mail + password + code, or the first
 * account of a new instance. Every way ends in [Session.signedIn], which keeps the session behind the
 * fingerprint; the app then leaves this screen on its own.
 */
@HiltViewModel
class SignInViewModel
@Inject
constructor(private val session: Session) : ViewModel() {
    private val state = MutableStateFlow(SignInUi())
    val ui: StateFlow<SignInUi> = state.asStateFlow()

    init {
        viewModelScope.launch {
            session.savedInstance()?.let { saved ->
                state.update { it.copy(instanceInput = saved.toString().trimEnd('/')) }
            }
        }
    }

    private val api: ZankupApi? get() = state.value.instance?.let(session::anonymous)
    private val auth: AuthApi? get() = api?.service()

    private fun error(message: Message?) = state.update { it.copy(error = message, busy = false) }

    private fun failure(result: ApiResult.Failure): Message = when (result) {
        is ApiResult.Offline -> Message.Res(R.string.signin_error_network)
        is ApiResult.Http ->
            when (result.code) {
                403 -> Message.Res(R.string.signin_error_no_totp)
                429 -> Message.Res(R.string.signin_error_too_many, listOf(result.retryAfter ?: "30"))
                else -> Message.Res(R.string.signin_error_wrong)
            }
        else -> Message.Res(R.string.signin_error_wrong)
    }

    private inline fun busy(crossinline block: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            block()
            state.update { it.copy(busy = false) }
        }
    }

    // --- the instance ------------------------------------------------------------------------------

    fun onInstance(value: String) = state.update { it.copy(instanceInput = value, error = null) }

    fun submitInstance() = busy {
        val url = session.parseInstance(state.value.instanceInput)
        if (url == null) return@busy error(Message.Res(R.string.signin_instance_invalid))
        val anonymous = session.anonymous(url)
        val health = call { anonymous.service<DefaultApi>().getHealth() }
        if (health !is ApiResult.Ok) return@busy error(Message.Res(R.string.signin_instance_invalid))
        val auth = anonymous.service<AuthApi>()
        val open = (call { auth.getSetupStatus() } as? ApiResult.Ok)?.value?.open == true
        val providers = (call { auth.authProviders() } as? ApiResult.Ok)?.value
        val google = providers?.googleClientId?.takeIf { providers.google }
        state.update {
            it.copy(
                instance = url,
                instanceInput = url.toString().trimEnd('/'),
                googleClientId = google,
                step = if (open) Step.SETUP_ACCOUNT else Step.PASSWORD
            )
        }
    }

    fun changeInstance() = state.update { SignInUi(instanceInput = it.instanceInput) }

    // --- e-mail, password and code -----------------------------------------------------------------

    fun onEmail(value: String) = state.update { it.copy(email = value, error = null) }

    fun onPassword(value: String) = state.update { it.copy(password = value, error = null) }

    fun togglePassword() = state.update { it.copy(showPassword = !it.showPassword) }

    fun submitPassword() {
        val ui = state.value
        if (!Rules.validEmail(ui.email)) return error(Message.Res(R.string.signin_error_invalid_email))
        if (ui.password.isEmpty()) return error(Message.Res(R.string.signin_error_no_password))
        busy {
            val request =
                ZankupAuthRoutesLoginRequest(
                    email = ui.email.trim(),
                    password = ui.password,
                    remember = true,
                    client = ZankupAuthRoutesLoginRequest.Client.ANDROID,
                    deviceName = session.deviceName
                )
            when (val result = call { auth!!.login(request) }) {
                is ApiResult.Ok -> state.update {
                    it.copy(step = Step.CODE, challenge = result.value.challenge, code = "")
                }
                is ApiResult.Failure -> error(failure(result))
            }
        }
    }

    fun onCode(value: String) = state.update { it.copy(code = Rules.codeDigits(value), error = null) }

    fun submitCode(prompter: Prompter) {
        val ui = state.value
        val challenge = ui.challenge ?: return
        if (ui.code.length < 6) return error(Message.Res(R.string.signin_error_incomplete))
        if (Rules.challengeExpired(challenge)) return expired()
        busy {
            when (val result = call { auth!!.loginTotp(TotpRequest(challenge, ui.code)) }) {
                is ApiResult.Ok -> keep(result.value, prompter)
                is ApiResult.Failure -> {
                    if (result is ApiResult.Http &&
                        result.code == 401 &&
                        Rules.challengeExpired(challenge)
                    ) {
                        return@busy expired()
                    }
                    state.update { it.copy(code = "") }
                    error(failure(result))
                }
            }
        }
    }

    private fun expired() = state.update {
        it.copy(
            step = Step.PASSWORD,
            challenge = null,
            code = "",
            busy = false,
            error = Message.Res(R.string.signin_error_expired)
        )
    }

    fun backToPassword() = state.update { it.copy(step = Step.PASSWORD, challenge = null, code = "", error = null) }

    // --- Google and passkeys ---------------------------------------------------------------------

    fun google(credentials: Credentials, prompter: Prompter) = busy {
        val clientId = state.value.googleClientId ?: return@busy
        val auth = auth!!
        val nonce =
            when (val result = call { auth.googleMobileNonce() }) {
                is ApiResult.Ok -> result.value
                is ApiResult.Failure -> return@busy error(failure(result))
            }
        val token =
            when (val result = credentials.googleIdToken(clientId, nonce.nonce)) {
                is CredentialResult.Ok -> result.value
                CredentialResult.Dismissed -> return@busy
                is CredentialResult.Failed -> return@busy error(Message.Res(R.string.signin_google_failed))
            }
        when (val result = call { auth.googleMobileSignIn(MobileSignIn(token, nonce.state, session.deviceName)) }) {
            is ApiResult.Ok -> keep(result.value, prompter)
            // The instance does not know that Google account: it is not linked to this Zankup.
            is ApiResult.Http if result.code in setOf(401, 403) -> error(Message.Res(R.string.signin_google_rejected))
            is ApiResult.Failure -> error(failure(result))
        }
    }

    fun passkey(credentials: Credentials, prompter: Prompter) = busy {
        val auth = auth!!
        val options =
            when (val result = call { auth.passkeyLoginOptions() }) {
                is ApiResult.Ok -> result.value.options
                is ApiResult.Failure -> return@busy error(failure(result))
            }
        val response =
            when (val result = credentials.passkey(JsonObject(options).toString())) {
                is CredentialResult.Ok -> result.value
                CredentialResult.Dismissed -> return@busy
                is CredentialResult.Failed -> return@busy error(Message.Res(R.string.signin_error_passkey))
            }
        val request =
            ZankupAuthPasskeysLoginRequest(
                credential = ZankupJson.parseToJsonElement(response).jsonObject,
                remember = true,
                client = ZankupAuthPasskeysLoginRequest.Client.ANDROID,
                deviceName = session.deviceName
            )
        when (val result = call { auth.passkeyLogin(request) }) {
            is ApiResult.Ok -> keep(result.value, prompter)
            is ApiResult.Http if result.code == 429 -> error(failure(result))
            is ApiResult.Http -> error(Message.Res(R.string.signin_error_passkey))
            is ApiResult.Failure -> error(failure(result))
        }
    }

    // --- forgot ------------------------------------------------------------------------------------

    fun forgot() = state.update { it.copy(step = Step.FORGOT, forgotSent = false, error = null) }

    fun submitForgot() {
        if (!Rules.validEmail(state.value.email)) return error(Message.Res(R.string.signin_error_invalid_email))
        busy {
            // The API answers the same whether or not the e-mail has an account.
            when (val result = call { auth!!.forgotPassword(ForgotRequest(state.value.email.trim())) }) {
                is ApiResult.Ok -> state.update { it.copy(forgotSent = true) }
                is ApiResult.Http if result.code == 429 -> error(failure(result))
                is ApiResult.Http -> state.update { it.copy(forgotSent = true) }
                is ApiResult.Failure -> error(failure(result))
            }
        }
    }

    // --- the first account (setup) ---------------------------------------------------------------

    fun onSetupCode(value: String) = state.update { it.copy(setupCode = value, error = null) }

    fun onName(value: String) = state.update { it.copy(name = value, error = null) }

    fun onAccept(value: Boolean) = state.update { it.copy(accept = value, error = null) }

    fun onLoadHistory(value: Boolean) = state.update { it.copy(loadHistory = value) }

    fun submitAccount() {
        val ui = state.value
        when {
            ui.name.isBlank() -> return error(Message.Res(R.string.setup_error_name))
            !Rules.validEmail(ui.email) -> return error(Message.Res(R.string.signin_error_invalid_email))
            !Rules.longEnough(ui.password) -> return error(Message.Res(R.string.setup_error_short))
            !ui.accept -> return error(Message.Res(R.string.setup_error_accept))
        }
        busy {
            val request =
                AccountRequest(ui.setupCode.trim(), ui.name.trim(), ui.email.trim(), ui.password, ui.accept)
            when (val result = call { auth!!.setupAccount(request) }) {
                is ApiResult.Ok ->
                    state.update {
                        it.copy(
                            step = Step.SETUP_TOTP,
                            setupToken = result.value.setupToken,
                            totpSecret = result.value.secret,
                            totpUri = result.value.uri,
                            code = ""
                        )
                    }
                is ApiResult.Http ->
                    error(
                        when (result.code) {
                            401 -> Message.Res(R.string.setup_error_code)
                            429 -> failure(result)
                            else -> result.detail?.let(Message::Text) ?: failure(result)
                        }
                    )
                is ApiResult.Failure -> error(failure(result))
            }
        }
    }

    /** The tokens of the new account, kept in memory until the recovery codes have been shown. */
    private var pending: AppTokens? = null

    fun submitSetupCode() {
        val ui = state.value
        val token = ui.setupToken ?: return
        if (ui.code.length < 6) return error(Message.Res(R.string.signin_error_incomplete))
        busy {
            val request =
                TotpConfirm(token, ui.code, TotpConfirm.Client.ANDROID, session.deviceName)
            when (val result = call { auth!!.setupTotp(request) }) {
                is ApiResult.Ok -> {
                    pending = result.value.tokens
                    state.update { it.copy(step = Step.SETUP_CODES, recoveryCodes = result.value.recoveryCodes) }
                }
                is ApiResult.Http ->
                    if (result.code == 401 && result.detail?.contains("too long") == true) {
                        state.update {
                            it.copy(
                                step = Step.SETUP_ACCOUNT,
                                setupToken = null,
                                busy = false,
                                error = Message.Res(R.string.setup_error_expired)
                            )
                        }
                    } else {
                        state.update { it.copy(code = "") }
                        error(result.detail?.let(Message::Text) ?: failure(result))
                    }
                is ApiResult.Failure -> error(failure(result))
            }
        }
    }

    /** After the recovery codes: the session is kept behind the fingerprint and the history may start loading. */
    fun finishSetup(prompter: Prompter) = busy {
        val tokens = pending ?: return@busy error(Message.Res(R.string.setup_error_expired))
        val instance = state.value.instance ?: return@busy
        session.signedIn(instance, tokens.accessToken, tokens.refreshToken, prompter)
        if (state.value.loadHistory) {
            (session.current.value as? SessionState.SignedIn)?.api?.let { api ->
                call { api.service<SyncApi>().startSync(SyncRequest(history = true)) }
            }
        }
    }

    private suspend fun keep(tokens: AppTokens, prompter: Prompter) {
        val instance = state.value.instance ?: return
        val kept = session.signedIn(instance, tokens.accessToken, tokens.refreshToken, prompter)
        state.update { it.copy(notKept = !kept) }
    }
}
