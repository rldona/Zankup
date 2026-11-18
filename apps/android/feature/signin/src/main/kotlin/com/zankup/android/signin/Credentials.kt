package com.zankup.android.signin

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** What Android's account sheets gave back. */
sealed interface CredentialResult {
    data class Ok(val value: String) : CredentialResult

    /** The sheet was dismissed, or there was nothing to offer (the sheet already said so). */
    data object Dismissed : CredentialResult

    data class Failed(val cause: Exception) : CredentialResult
}

/** Google and passkeys through Android's Credential Manager; it needs the activity that shows the sheet. */
class Credentials(private val activity: Activity) {
    private val manager = CredentialManager.create(activity)

    /**
     * A fresh Google ID token for the instance's web client ([serverClientId]), carrying the instance's
     * [nonce]: the API checks both, so a token cannot be replayed.
     */
    suspend fun googleIdToken(serverClientId: String, nonce: String): CredentialResult {
        val option =
            GetGoogleIdOption
                .Builder()
                .setServerClientId(serverClientId)
                .setNonce(nonce)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()
        return get(GetCredentialRequest(listOf(option))) { credential ->
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                null
            }
        }
    }

    /** Signs the server's WebAuthn challenge ([requestJson]) with a passkey; returns the response JSON. */
    suspend fun passkey(requestJson: String): CredentialResult =
        get(GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestJson)))) { credential ->
            (credential as? PublicKeyCredential)?.authenticationResponseJson
        }

    private suspend fun get(
        request: GetCredentialRequest,
        read: (androidx.credentials.Credential) -> String?
    ): CredentialResult = try {
        val value = read(manager.getCredential(activity, request).credential)
        if (value !=
            null
        ) {
            CredentialResult.Ok(value)
        } else {
            CredentialResult.Failed(IllegalStateException("unexpected credential"))
        }
    } catch (_: GetCredentialCancellationException) {
        CredentialResult.Dismissed
    } catch (_: NoCredentialException) {
        CredentialResult.Dismissed
    } catch (e: GetCredentialException) {
        CredentialResult.Failed(e)
    }
}
