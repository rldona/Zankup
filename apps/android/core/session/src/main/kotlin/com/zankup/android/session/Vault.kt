package com.zankup.android.session

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** How the refresh token is protected on this phone. */
enum class Protection {
    /** By a Keystore key that only strong biometrics (class 3, the fingerprint) release, for each use. */
    STRONG,

    /** By a plain Keystore key: no strong biometrics, or the person turned the lock off or chose a weak lock. */
    DEVICE
}

/** The refresh token encrypted with its Keystore key: what is stored on disk. */
class Sealed(val iv: ByteArray, val ciphertext: ByteArray, val protection: Protection) {
    fun encode(): String = b64.encodeToString(iv) + "." + b64.encodeToString(ciphertext)

    companion object {
        private val b64 = java.util.Base64.getEncoder()
        private val b64d = java.util.Base64.getDecoder()

        fun decode(blob: String, protection: Protection): Sealed? {
            val parts = blob.split('.')
            if (parts.size != 2) return null
            return try {
                Sealed(b64d.decode(parts[0]), b64d.decode(parts[1]), protection)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

/**
 * The Android Keystore keys that seal the refresh token (AES-256-GCM, never leaving the secure hardware).
 * The [Protection.STRONG] key needs the fingerprint for every encryption and decryption (a `CryptoObject`
 * in the prompt) and Android destroys it when the enrolled fingerprints change: [cipherFor] then throws
 * `KeyPermanentlyInvalidatedException`, which means signing in again.
 */
object Vault {
    private const val STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun alias(protection: Protection) = "zankup.refresh.${protection.name.lowercase()}"

    private fun keyStore() = KeyStore.getInstance(STORE).apply { load(null) }

    private fun key(protection: Protection): SecretKey {
        (keyStore().getKey(alias(protection), null) as? SecretKey)?.let { return it }
        val spec =
            KeyGenParameterSpec
                .Builder(alias(protection), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .apply {
                    if (protection == Protection.STRONG) {
                        setUserAuthenticationRequired(true)
                        setInvalidatedByBiometricEnrollment(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                        } else {
                            @Suppress("DEPRECATION")
                            setUserAuthenticationValidityDurationSeconds(-1)
                        }
                    }
                }.build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE).run {
            init(spec)
            generateKey()
        }
    }

    /** A cipher ready to seal (iv null) or to open a [Sealed] token; for STRONG it still needs the prompt. */
    fun cipherFor(protection: Protection, iv: ByteArray? = null): Cipher = Cipher.getInstance(TRANSFORMATION).apply {
        if (iv == null) {
            init(Cipher.ENCRYPT_MODE, key(protection))
        } else {
            init(Cipher.DECRYPT_MODE, key(protection), GCMParameterSpec(128, iv))
        }
    }

    fun seal(cipher: Cipher, token: String, protection: Protection) =
        Sealed(cipher.iv, cipher.doFinal(token.toByteArray()), protection)

    fun open(cipher: Cipher, sealed: Sealed) = String(cipher.doFinal(sealed.ciphertext))

    /** Forgets both keys (signing out): a new one is made at the next sign-in. */
    fun destroy() {
        val store = keyStore()
        Protection.entries.forEach { store.deleteEntry(alias(it)) }
    }
}
