package com.zankup.android.data

import com.zankup.android.network.ApiResult
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReachabilityTest {
    @Test
    fun onlyTheServerNotAnsweringCounts() {
        val reachability = Reachability()
        reachability.note(ApiResult.Http(404, null))
        assertFalse(reachability.unreachable.value)
        reachability.note(ApiResult.Http(503, null))
        assertTrue(reachability.unreachable.value)
        reachability.note(ApiResult.Ok(Unit))
        assertFalse(reachability.unreachable.value)
        reachability.note(ApiResult.Http(429, null, "30"))
        assertTrue(reachability.unreachable.value)
        reachability.note(ApiResult.Http(401, null))
        assertTrue(reachability.unreachable.value)
        reachability.note(ApiResult.Offline(IOException()))
        assertTrue(reachability.unreachable.value)
    }
}
