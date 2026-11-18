package com.zankup.android.session

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** What this phone's enrolled biometrics can do. */
enum class BiometricLevel {
    /** Class 3 (a fingerprint): can release Keystore keys. */
    STRONG,

    /** Only class 2 (face unlock on many Samsung phones): an app lock at most. */
    WEAK,

    /** Nothing enrolled, or no sensor. */
    NONE
    ;

    companion object {
        fun of(context: Context): BiometricLevel {
            val manager = BiometricManager.from(context)
            return when {
                manager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS -> STRONG
                manager.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS -> WEAK
                else -> NONE
            }
        }
    }
}

/** How a prompt ended. */
sealed interface PromptResult {
    data class Passed(val cipher: Cipher?) : PromptResult

    /** Cancelled, timed out, locked out or failed: the token stays where it is and the person can try again. */
    data class Failed(val code: Int) : PromptResult
}

/** Shows Android's biometric prompt from an activity; one per screen that needs the fingerprint. */
class Prompter(
    private val activity: FragmentActivity,
    private val title: String = activity.getString(R.string.prompt_title),
    private val cancel: String = activity.getString(R.string.prompt_cancel)
) {
    /** Asks for strong biometrics bound to [cipher] (the Keystore key is released only for it). */
    suspend fun strong(cipher: Cipher): PromptResult = show(BIOMETRIC_STRONG, BiometricPrompt.CryptoObject(cipher))

    /** Asks for any biometric, weak included, with no key behind it: the app lock. */
    suspend fun weak(): PromptResult = show(BIOMETRIC_WEAK, null)

    private suspend fun show(authenticators: Int, crypto: BiometricPrompt.CryptoObject?): PromptResult =
        suspendCancellableCoroutine { continuation ->
            val prompt =
                BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    PromptResult.Passed(result.cryptoObject?.cipher)
                                )
                            }
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (continuation.isActive) continuation.resume(PromptResult.Failed(errorCode))
                        }
                    }
                )
            val info =
                BiometricPrompt.PromptInfo
                    .Builder()
                    .setTitle(title)
                    .setNegativeButtonText(cancel)
                    .setAllowedAuthenticators(authenticators)
                    .setConfirmationRequired(false)
                    .build()
            if (crypto != null) prompt.authenticate(info, crypto) else prompt.authenticate(info)
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
}
