package com.zankup.android.settings.more

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import com.zankup.android.api.endpoints.AuthApi
import com.zankup.android.api.endpoints.ProfileApi
import com.zankup.android.api.model.DeviceOut
import com.zankup.android.api.model.KeyStatus
import com.zankup.android.api.model.Me
import com.zankup.android.api.model.PasskeyOut
import com.zankup.android.api.model.Providers
import com.zankup.android.api.model.RegisterRequest
import com.zankup.android.api.model.SourceStatus
import com.zankup.android.data.Store
import com.zankup.android.network.ApiResult
import com.zankup.android.network.ZankupJson
import com.zankup.android.network.call
import com.zankup.android.session.BiometricLevel
import com.zankup.android.session.Prompter
import com.zankup.android.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** How adding a passkey ended. */
sealed interface PasskeyOutcome {
    data object Added : PasskeyOutcome

    /** The sheet was dismissed: nothing to say. */
    data object Cancelled : PasskeyOutcome

    /** This phone already holds the account's passkey (the server listed it as excluded). */
    data object Exists : PasskeyOutcome

    data object Failed : PasskeyOutcome
}

/** How unlinking Google ended. */
enum class UnlinkOutcome { Done, NeedsOther, Failed }

/**
 * Settings › Account (OS-1161): the ways in (password and code, Google, passkeys), the app lock, Garmin,
 * the signed-in devices and signing out.
 */
@HiltViewModel
class AccountModel
@Inject
constructor(
    private val store: Store,
    private val session: Session,
    val service: ServiceSettings,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    val me = store.query(ME, Me.serializer()) { api -> call { api.service<AuthApi>().getMe() } }

    val providers = store.query("settingsm/providers", Providers.serializer()) { api ->
        call { api.service<AuthApi>().authProviders() }
    }

    val passkeys = store.query("settingsm/passkeys", ListSerializer(PasskeyOut.serializer())) { api ->
        call { api.service<AuthApi>().listPasskeys() }
    }

    val sources = store.query("settingsm/sources", ListSerializer(SourceStatus.serializer())) { api ->
        call { api.service<ProfileApi>().listSources() }
    }

    val devices = store.query("settingsm/devices", ListSerializer(DeviceOut.serializer())) { api ->
        call { api.service<AuthApi>().listDevices() }
    }

    val instanceUrl: String get() = session.api?.baseUrl?.toString()?.trimEnd('/').orEmpty()

    fun biometrics(): BiometricLevel = BiometricLevel.of(context)

    suspend fun refresh() {
        me.refresh()
        providers.refresh()
        passkeys.refresh()
        service.refreshKeys()
        service.refreshInstance()
        sources.refresh()
        devices.refresh()
    }

    // --- sign-in methods ------------------------------------------------------------------------

    suspend fun unlinkGoogle(current: Me): UnlinkOutcome {
        val api = session.api ?: return UnlinkOutcome.Failed
        return when (val r = call { api.service<AuthApi>().googleUnlink() }) {
            is ApiResult.Ok -> {
                store.put(ME, Me.serializer(), current.copy(googleLinked = false))
                UnlinkOutcome.Done
            }
            is ApiResult.Http -> if (r.code == 409) UnlinkOutcome.NeedsOther else UnlinkOutcome.Failed
            else -> UnlinkOutcome.Failed
        }
    }

    suspend fun removePasskey(id: Int): Boolean {
        val api = session.api ?: return false
        return (call { api.service<AuthApi>().deletePasskey(id) } is ApiResult.Ok).also { passkeys.refresh() }
    }

    /** Credential Manager makes passkeys from Android 9. */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    val passkeysSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /** The server's options → Android's passkey sheet → the new credential back to the server. */
    @SuppressLint("PublicKeyCredential") // checked by passkeysSupported
    suspend fun addPasskey(activity: Activity): PasskeyOutcome {
        if (!passkeysSupported) return PasskeyOutcome.Failed
        val api = session.api ?: return PasskeyOutcome.Failed
        val auth = api.service<AuthApi>()
        val options = (call { auth.passkeyRegisterOptions() } as? ApiResult.Ok)?.value?.options
            ?: return PasskeyOutcome.Failed
        val json =
            try {
                val response = CredentialManager
                    .create(activity)
                    .createCredential(activity, CreatePublicKeyCredentialRequest(JsonObject(options).toString()))
                (response as? CreatePublicKeyCredentialResponse)?.registrationResponseJson
                    ?: return PasskeyOutcome.Failed
            } catch (_: CreateCredentialCancellationException) {
                return PasskeyOutcome.Cancelled
            } catch (e: CreatePublicKeyCredentialDomException) {
                return if (e.domError is InvalidStateError) PasskeyOutcome.Exists else PasskeyOutcome.Failed
            } catch (_: CreateCredentialException) {
                return PasskeyOutcome.Failed
            }
        val credential = runCatching { ZankupJson.parseToJsonElement(json).jsonObject }.getOrNull()
            ?: return PasskeyOutcome.Failed
        val done = call { auth.passkeyRegister(RegisterRequest(credential, session.deviceName)) }
        passkeys.refresh()
        return if (done is ApiResult.Ok) PasskeyOutcome.Added else PasskeyOutcome.Failed
    }

    // --- the app lock ---------------------------------------------------------------------------

    /** Whether the fingerprint opens the app, and weak biometrics as an app lock. */
    suspend fun lock(): Pair<Boolean, Boolean> = session.lock()

    /** Seals the session again to match; false when the fingerprint was not given (nothing changed). */
    suspend fun setLock(biometric: Boolean, weak: Boolean, activity: FragmentActivity): Boolean =
        session.setLock(biometric, weak, Prompter(activity))

    // --- Garmin ---------------------------------------------------------------------------------

    suspend fun setInterval(minutes: Int) =
        service.patchInstance(mapOf("sync_interval_minutes" to JsonPrimitive(minutes))).also { sources.refresh() }

    suspend fun saveGarminKey(value: String, expiresOn: String?) =
        service.saveKey(KeyStatus.Name.GARMIN, value, expiresOn).also { sources.refresh() }

    suspend fun deleteGarminKey() = service.deleteKey(KeyStatus.Name.GARMIN).also { sources.refresh() }

    // --- sessions -------------------------------------------------------------------------------

    suspend fun signOutDevice(id: Int): Boolean {
        val api = session.api ?: return false
        return (call { api.service<AuthApi>().deleteDevice(id) } is ApiResult.Ok).also { devices.refresh() }
    }

    suspend fun signOut() = session.signOut()

    private companion object {
        const val ME = "settingsm/me"
    }
}
