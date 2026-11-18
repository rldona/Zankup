package com.zankup.android.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore("session")

/**
 * What the session keeps on the phone, outside the Keystore: the instance, the sealed refresh token
 * (useless without its Keystore key) and the lock settings. Excluded from backups (data_extraction_rules).
 */
@Singleton
class SessionPrefs
@Inject
constructor(@param:ApplicationContext private val context: Context) {
    private object Keys {
        val instance = stringPreferencesKey("instance")
        val sealed = stringPreferencesKey("sealed_refresh")
        val protection = stringPreferencesKey("protection")
        val biometricLock = booleanPreferencesKey("biometric_lock")
        val weakLock = booleanPreferencesKey("weak_lock")
    }

    private suspend fun prefs() = context.sessionStore.data.first()

    suspend fun instance(): String? = prefs()[Keys.instance]

    suspend fun setInstance(url: String) {
        context.sessionStore.edit { it[Keys.instance] = url }
    }

    suspend fun sealed(): Sealed? {
        val p = prefs()
        val blob = p[Keys.sealed] ?: return null
        val protection = Protection.entries.firstOrNull { it.name == p[Keys.protection] } ?: return null
        return Sealed.decode(blob, protection)
    }

    suspend fun setSealed(sealed: Sealed?) {
        context.sessionStore.edit {
            if (sealed == null) {
                it.remove(Keys.sealed)
                it.remove(Keys.protection)
            } else {
                it[Keys.sealed] = sealed.encode()
                it[Keys.protection] = sealed.protection.name
            }
        }
    }

    /** Whether opening Zankup asks for the fingerprint: on unless turned off in Settings. */
    suspend fun biometricLock(): Boolean = prefs()[Keys.biometricLock] ?: true

    suspend fun setBiometricLock(on: Boolean) {
        context.sessionStore.edit { it[Keys.biometricLock] = on }
    }

    /** On phones with only weak biometrics (face unlock), use them as an app lock. Off by default. */
    suspend fun weakLock(): Boolean = prefs()[Keys.weakLock] ?: false

    suspend fun setWeakLock(on: Boolean) {
        context.sessionStore.edit { it[Keys.weakLock] = on }
    }
}
