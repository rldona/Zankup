package com.zankup.android.signin

import androidx.annotation.StringRes
import okhttp3.HttpUrl

/** A message for the person: one of ours, or the API's own words. */
sealed interface Message {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : Message

    data class Text(val text: String) : Message
}

enum class Step { INSTANCE, PASSWORD, CODE, FORGOT, SETUP_ACCOUNT, SETUP_TOTP, SETUP_CODES }

data class SignInUi(
    val step: Step = Step.INSTANCE,
    val instanceInput: String = "https://api.zankup.com",
    val instance: HttpUrl? = null,
    /** The instance's Google web client, when it offers Google. */
    val googleClientId: String? = null,
    val email: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val challenge: String? = null,
    val code: String = "",
    val forgotSent: Boolean = false,
    // The first account (setup).
    val setupCode: String = "",
    val name: String = "",
    val accept: Boolean = false,
    val setupToken: String? = null,
    val totpSecret: String? = null,
    val totpUri: String? = null,
    val recoveryCodes: List<String> = emptyList(),
    val loadHistory: Boolean = true,
    val busy: Boolean = false,
    val error: Message? = null,
    /** Shown on the code step after the fingerprint was not given: the session was used but not kept. */
    val notKept: Boolean = false
)
