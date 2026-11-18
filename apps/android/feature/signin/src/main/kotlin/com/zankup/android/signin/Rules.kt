package com.zankup.android.signin

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** The sign-in's rules, on their own so they are tested without Android. */
internal object Rules {
    private val email = Regex("""^\S+@\S+\.\S+$""")

    fun validEmail(value: String) = email.matches(value.trim())

    /** Only digits, at most six: what the code boxes keep of what was typed, pasted or autofilled. */
    fun codeDigits(value: String) = value.filter(Char::isDigit).take(6)

    /** "JBSWY3DPEHPK3PXP" → "JBSW Y3DP EHPK 3PXP", easier to type into an authenticator app. */
    fun groupKey(secret: String) = secret.chunked(4).joinToString(" ")

    /** A new account's password: at least 12 characters, as the API asks. */
    fun longEnough(password: String) = password.length >= 12

    /**
     * The code's challenge is a JWT valid for five minutes: past its expiry the password is asked again.
     * Anything that cannot be read counts as not expired (the API has the last word).
     */
    fun challengeExpired(
        challenge: String,
        nowMillis: Long = System.currentTimeMillis(),
        decode: (String) -> ByteArray = { Base64.decode(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }
    ): Boolean = try {
        val payload = String(decode(challenge.split('.')[1].trimEnd('=')))
        val exp = Json.parseToJsonElement(payload).jsonObject["exp"]?.jsonPrimitive?.longOrNull
        exp != null && exp * 1000 < nowMillis
    } catch (_: Exception) {
        false
    }
}
