package com.zankup.android.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstanceTest {
    private fun parsed(input: String, allowHttp: Boolean = false) = Instance.parse(input, allowHttp)?.toString()

    @Test
    fun addsHttpsAndTheSlash() {
        assertEquals("https://api.zankup.com/", parsed("api.zankup.com"))
        assertEquals("https://api.zankup.com/", parsed("  https://api.zankup.com/  "))
        assertEquals("https://me.up.railway.app/", parsed("me.up.railway.app/api"))
        assertEquals("https://example.org/zankup/", parsed("https://example.org/zankup/api/?x=1#y"))
    }

    @Test
    fun plainHttpOnlyWhenAllowed() {
        assertNull(parsed("http://localhost:8000"))
        assertEquals("http://localhost:8000/", parsed("http://localhost:8000", allowHttp = true))
    }

    @Test
    fun refusesWhatIsNotAnInstance() {
        assertNull(parsed(""))
        assertNull(parsed("zankup"))
        assertNull(parsed("api zankup.com"))
        assertNull(parsed("ftp://api.zankup.com"))
    }
}
