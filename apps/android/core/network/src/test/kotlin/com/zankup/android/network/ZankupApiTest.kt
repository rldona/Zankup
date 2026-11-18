package com.zankup.android.network

import com.zankup.android.api.endpoints.AuthApi
import com.zankup.android.api.endpoints.DefaultApi
import com.zankup.android.api.model.Health
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ZankupApiTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    private fun json(body: String, code: Int = 200) = MockResponse
        .Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun api(tokens: TokenStore, onSignedOut: () -> Unit = {}) =
        ZankupApi(server.url("/"), tokens, onSignedOut = onSignedOut)

    @Test
    fun readsTheHealthCheckIgnoringNewFields() = runTest {
        server.enqueue(
            json("""{"status":"ok","version":"1.4.0","database":"ok","schema_revision":"abc","new":1}""")
        )
        val result = call { api(MemoryTokenStore()).service<DefaultApi>().getHealth() }
        val health = (result as ApiResult.Ok).value
        assertEquals(Health.Status.OK, health.status)
        assertEquals("1.4.0", health.version)
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun sendsTheTokenAndRenewsItOnceWithoutRotating() = runTest {
        val tokens = MemoryTokenStore(Tokens("old", "refresh-1"))
        server.enqueue(json("""{"detail":"expired"}""", 401))
        server.enqueue(json("""{"access_token":"new","refresh_token":"refresh-1","expires_in":900}"""))
        server.enqueue(json("""{"id":1,"email":"a@b.c","name":"A"}"""))
        call { api(tokens).service<AuthApi>().getMe() }

        assertEquals("Bearer old", server.takeRequest().headers["Authorization"])
        val refresh = server.takeRequest()
        assertEquals("/api/auth/refresh", refresh.url.encodedPath)
        val sent = refresh.body!!.utf8()
        assertTrue(sent, sent.contains("\"rotate\":false") && sent.contains("refresh-1"))
        assertEquals("Bearer new", server.takeRequest().headers["Authorization"])
        assertEquals(Tokens("new", "refresh-1"), tokens.read())
    }

    @Test
    fun signsOutWhenTheRefreshIsRefused() = runTest {
        val tokens = MemoryTokenStore(Tokens("old", "gone"))
        var signedOut = false
        server.enqueue(json("""{"detail":"expired"}""", 401))
        server.enqueue(json("""{"detail":"Invalid refresh token"}""", 401))
        val result = call { api(tokens) { signedOut = true }.service<AuthApi>().getMe() }
        assertEquals(ApiResult.SignedOut, result)
        assertTrue(signedOut)
        assertNull(tokens.read())
    }

    @Test
    fun mapsErrorsAndTheOffline() = runTest {
        server.enqueue(json("""{"detail":"Not found"}""", 404))
        server.enqueue(json("""{"detail":[{"loc":["body","x"],"msg":"Field required","type":"missing"}]}""", 422))
        val api = api(MemoryTokenStore())
        assertEquals(ApiResult.Http(404, "Not found"), call { api.service<DefaultApi>().getHealth() })
        assertEquals(ApiResult.Http(422, "Field required"), call { api.service<DefaultApi>().getHealth() })
        server.close()
        assertTrue(call { api.service<DefaultApi>().getHealth() } is ApiResult.Offline)
    }

    @Test
    fun readsNdjsonLineByLine() = runTest {
        val lines = "{\"a\":1}\n\n{\"b\":2}\n".toResponseBody().ndjsonLines().toList()
        assertEquals(listOf("{\"a\":1}", "{\"b\":2}"), lines)
    }
}
