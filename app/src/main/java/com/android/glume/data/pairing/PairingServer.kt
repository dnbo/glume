package com.android.glume.data.pairing

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import com.android.glume.BuildConfig
import com.android.glume.data.auth.Account
import com.android.glume.data.auth.SignInResult
import com.android.glume.data.auth.SignInService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Inet4Address

sealed interface PairingState {
    data object Starting : PairingState
    data class Waiting(val url: String, val code: String) : PairingState
    data class PhoneConnected(val url: String, val code: String) : PairingState
    data class SigningIn(val url: String, val code: String) : PairingState
    data class SignedIn(val account: Account) : PairingState
    data class Failed(val url: String, val code: String, val message: String) : PairingState
    /** No Wi-Fi/Ethernet address, so the phone can't reach the TV. */
    data object NoNetwork : PairingState
}

/**
 * Serves the phone sign-in page on the local network while the Connect screen is open.
 *
 * Security: the page is plain HTTP on the LAN (a TV can't get a trusted certificate), so the link
 * carries a one-time 144-bit token, expires after 10 minutes, accepts one successful sign-in, and
 * the server only runs while the pairing screen is visible. The password goes straight to Immich
 * and is never stored.
 */
class PairingServer(private val context: Context, private val signIn: SignInService, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<PairingState>(PairingState.Starting)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private var server: EmbeddedServer<*, *>? = null
    private var session: PairingSession? = null
    private var url = ""
    private val lock = Mutex()

    fun start() {
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                if (server != null) return@withLock
                val ip = lanAddress() ?: run { _state.value = PairingState.NoNetwork; return@withLock }
                val s = PairingSession.create(SystemClock.elapsedRealtime())
                session = s
                val srv = runCatching { createServer(PREFERRED_PORT).also { it.start(wait = false) } }
                    .getOrElse { createServer(0).also { it.start(wait = false) } }
                server = srv
                val port = srv.engine.resolvedConnectors().first().port
                url = "http://$ip:$port/p/${s.token}"
                // Debug only: lets a developer open the phone page from adb without scanning.
                if (BuildConfig.DEBUG) android.util.Log.i("GlumePairing", url)
                _state.value = PairingState.Waiting(url, s.code)
            }
        }
    }

    fun stop() {
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                server?.stop(200, 500)
                server = null
                session = null
                _state.value = PairingState.Starting
            }
        }
    }

    /** New QR for the next person (after a sign-in or when the link expired). */
    fun restart() {
        scope.launch(Dispatchers.IO) {
            lock.withLock { server?.stop(200, 500); server = null; session = null }
            _state.value = PairingState.Starting
            start()
        }
    }

    private fun createServer(port: Int) = embeddedServer(CIO, port = port, host = "0.0.0.0") {
        routing {
            get("/p/{token}") {
                val s = session
                val ok = s != null && s.isValid(call.parameters["token"].orEmpty(), SystemClock.elapsedRealtime())
                if (!ok) { call.respondText(PhonePage.expired(), ContentType.Text.Html, HttpStatusCode.Gone); return@get }
                if (_state.value is PairingState.Waiting) _state.value = PairingState.PhoneConnected(url, s!!.code)
                call.respondText(PhonePage.form(s!!.code, defaultServer = if (BuildConfig.DEBUG) "https://demo.immich.app" else ""), ContentType.Text.Html)
            }
            post("/p/{token}") {
                val s = session
                if (s == null || !s.isValid(call.parameters["token"].orEmpty(), SystemClock.elapsedRealtime())) {
                    call.respondText(PhonePage.expired(), ContentType.Text.Html, HttpStatusCode.Gone); return@post
                }
                val p = call.receiveParameters()
                val server = p["server"].orEmpty()
                val email = p["email"].orEmpty()
                val password = p["password"].orEmpty()
                _state.value = PairingState.SigningIn(url, s.code)
                when (val r = signIn.signIn(server, email, password)) {
                    is SignInResult.Success -> {
                        s.markUsed()
                        _state.value = PairingState.SignedIn(r.account)
                        call.respondText(PhonePage.success(r.account.name), ContentType.Text.Html)
                    }
                    is SignInResult.Failed -> {
                        _state.value = PairingState.Failed(url, s.code, r.reason.message)
                        call.respondText(PhonePage.form(s.code, server, email, r.reason.message), ContentType.Text.Html)
                    }
                }
            }
        }
    }

    private fun lanAddress(): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }

    companion object {
        const val PREFERRED_PORT = 8942
    }
}
