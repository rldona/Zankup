package com.zankup.android.signin

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private val decode: (String) -> ByteArray = { Base64.getUrlDecoder().decode(it) }

    private fun jwt(payload: String) =
        "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()) + ".sig"

    @Test
    fun emails() {
        assertTrue(Rules.validEmail(" me@zankup.com "))
        assertFalse(Rules.validEmail("me@zankup"))
        assertFalse(Rules.validEmail("me zankup.com"))
    }

    @Test
    fun codeKeepsSixDigits() {
        assertEquals("123456", Rules.codeDigits("12 34-56 78"))
        assertEquals("", Rules.codeDigits("abc"))
    }

    @Test
    fun keyInGroupsOfFour() {
        assertEquals("JBSW Y3DP EHPK 3PXP", Rules.groupKey("JBSWY3DPEHPK3PXP"))
        assertEquals("ABCD E", Rules.groupKey("ABCDE"))
    }

    @Test
    fun challengeExpiry() {
        val now = 1_800_000_000_000L
        assertTrue(Rules.challengeExpired(jwt("""{"exp":1799999000}"""), now, decode))
        assertFalse(Rules.challengeExpired(jwt("""{"exp":1800000300}"""), now, decode))
        assertFalse(Rules.challengeExpired("not-a-jwt", now, decode))
    }

    @Test
    fun passwordLength() {
        assertFalse(Rules.longEnough("short"))
        assertTrue(Rules.longEnough("twelve chars"))
    }
}
