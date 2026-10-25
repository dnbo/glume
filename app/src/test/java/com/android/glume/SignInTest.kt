package com.android.glume

import com.android.glume.data.api.ImmichAuthApi
import com.android.glume.data.auth.Account
import com.android.glume.data.auth.ServerUrl
import com.android.glume.data.auth.SignInError
import com.android.glume.data.auth.SignInResult
import com.android.glume.data.auth.SignInService
import com.android.glume.data.auth.TV_PERMISSIONS
import com.android.glume.data.pairing.PairingSession
import com.android.glume.data.pairing.PhonePage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    /** A fake Immich server; records the calls made. */
    private fun fakeServer(calls: MutableList<String>, loginOk: Boolean = true, keyStatus: HttpStatusCode = HttpStatusCode.Created) = HttpClient(MockEngine { req ->
        val path = req.url.encodedPath
        calls += "${req.method.value} $path"
        when (path) {
            "/api/server/ping" -> respond("""{"res":"pong"}""", headers = json)
            "/api/auth/login" -> if (loginOk) respond(
                """{"accessToken":"session-token","userId":"u1","userEmail":"demo@immich.app","name":"Jane Doe","profileImagePath":"","isAdmin":true,"isOnboarded":true,"shouldChangePassword":false}""",
                HttpStatusCode.Created, json,
            ) else respond("""{"message":"Incorrect email or password"}""", HttpStatusCode.Unauthorized, json)
            "/api/api-keys" -> {
                calls += "body " + (req.body as TextContent).text
                if (keyStatus == HttpStatusCode.Created) respond("""{"id":"k1","secret":"limited-secret","name":"Glume TV (test)","permissions":[]}""", keyStatus, json)
                else respond("""{"message":"Missing required permission"}""", keyStatus, json)
            }
            "/api/users/me" -> {
                calls += "key " + req.headers["x-api-key"]
                respond("""{"id":"u1","email":"demo@immich.app","name":"Jane Doe","profileImagePath":"","avatarColor":"yellow"}""", headers = json)
            }
            "/api/api-keys/old-key" -> { respond("", HttpStatusCode.NoContent) }
            "/api/auth/logout" -> {
                calls += "logout-auth " + req.headers[HttpHeaders.Authorization]
                respond("""{"successful":true}""", headers = json)
            }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }) {
        install(ContentNegotiation) { json(ImmichAuthApi.json) }
        expectSuccess = false
    }

    @Test
    fun `sign-in swaps the session for a limited key and logs out`() = runBlocking {
        val calls = mutableListOf<String>()
        var saved: Pair<Account, String>? = null
        val svc = SignInService(ImmichAuthApi(fakeServer(calls)), { a, k -> saved = a to k }, "test")
        val r = svc.signIn("demo.immich.app/", "demo@immich.app", "demo")

        assertTrue(r is SignInResult.Success)
        assertEquals("limited-secret", saved!!.second)
        assertEquals("https://demo.immich.app", saved!!.first.serverUrl)
        assertEquals("Jane Doe", saved!!.first.name)
        // The user is read with the new key, and the password session is ended.
        assertTrue(calls.contains("key limited-secret"))
        assertTrue(calls.contains("logout-auth Bearer session-token"))
        assertEquals("POST /api/auth/logout", calls.filter { it.startsWith("POST") || it.startsWith("GET") }.last())
    }

    @Test
    fun `signing in again revokes the TV's previous key for that account`() = runBlocking {
        val calls = mutableListOf<String>()
        SignInService(ImmichAuthApi(fakeServer(calls)), { _, _ -> }, "test", { "old-key" })
            .signIn("https://demo.immich.app", "demo@immich.app", "demo")
        assertTrue(calls.contains("DELETE /api/api-keys/old-key"))
    }

    @Test
    fun `the key never asks for delete, upload or edit permissions`() {
        val forbidden = TV_PERMISSIONS.filter {
            it.endsWith(".delete") || it.endsWith(".create") && it != "albumAsset.create" ||
                it == "asset.upload" || it.startsWith("asset.edit") || it.startsWith("apiKey") || it.startsWith("admin")
        }
        assertEquals(emptyList<String>(), forbidden)
        assertTrue("albumAsset.create" in TV_PERMISSIONS)
        assertTrue("asset.update" in TV_PERMISSIONS)
    }

    @Test
    fun `wrong password reports invalid credentials and saves nothing`() = runBlocking {
        val calls = mutableListOf<String>()
        var saved = false
        val r = SignInService(ImmichAuthApi(fakeServer(calls, loginOk = false)), { _, _ -> saved = true }, "test")
            .signIn("https://demo.immich.app", "demo@immich.app", "nope")
        assertEquals(SignInError.InvalidCredentials, (r as SignInResult.Failed).reason)
        assertFalse(saved)
    }

    @Test
    fun `key creation refused still logs the session out`() = runBlocking {
        val calls = mutableListOf<String>()
        val r = SignInService(ImmichAuthApi(fakeServer(calls, keyStatus = HttpStatusCode.Forbidden)), { _, _ -> }, "test")
            .signIn("https://demo.immich.app", "demo@immich.app", "demo")
        assertEquals(SignInError.KeyNotAllowed, (r as SignInResult.Failed).reason)
        assertTrue(calls.contains("logout-auth Bearer session-token"))
    }

    @Test
    fun `server addresses are normalized`() {
        assertEquals("https://photos.local:2283", ServerUrl.normalize(" photos.local:2283/ "))
        assertEquals("http://192.168.1.10:2283", ServerUrl.normalize("http://192.168.1.10:2283/api"))
        assertEquals("https://demo.immich.app", ServerUrl.normalize("https://demo.immich.app/api/"))
    }

    @Test
    fun `pairing links work once and expire`() {
        val s = PairingSession.create(now = 1_000, ttlMs = 10_000)
        assertTrue(s.isValid(s.token, now = 5_000))
        assertFalse(s.isValid("wrong", now = 5_000))
        assertFalse(s.isValid(s.token, now = 11_001))
        s.markUsed()
        assertFalse(s.isValid(s.token, now = 5_000))
        assertTrue(Regex("^[A-Z2-9]{3}-[A-Z2-9]{3}$").matches(s.code))
        assertEquals(36, s.token.length)
    }

    @Test
    fun `phone page escapes user input`() {
        val html = PhonePage.form("ABC-123", defaultServer = "\"><script>x</script>", error = "<b>")
        assertFalse(html.contains("<script>x</script>"))
        assertTrue(html.contains("&lt;b&gt;"))
    }
}
