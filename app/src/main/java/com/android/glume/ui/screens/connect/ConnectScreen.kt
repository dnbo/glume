package com.android.glume.ui.screens.connect

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.android.glume.data.ImmichRepository
import com.android.glume.data.SettingsStore
import com.android.glume.data.mock.MockImmichRepository
import com.android.glume.domain.ServerCheck
import com.android.glume.ui.app.KeyboardDialog
import com.android.glume.ui.app.LocalApp
import com.android.glume.ui.app.PulseDot
import com.android.glume.ui.components.Field
import com.android.glume.ui.components.FocusSurface
import com.android.glume.ui.components.GIcon
import com.android.glume.ui.components.Ic
import com.android.glume.ui.components.Label
import com.android.glume.ui.components.PillButton
import com.android.glume.ui.components.T
import com.android.glume.ui.components.drawPlaceholder
import com.android.glume.ui.components.rememberThumbHash
import com.android.glume.ui.nav.ProfilesRoute
import com.android.glume.ui.theme.FocusScale
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

enum class ConnectMode { FORM, CONNECTING, ERROR }
enum class QrState { WAIT, SCANNED, APPROVED }

data class ConnectState(
    val mode: ConnectMode = ConnectMode.FORM,
    val url: String = MockImmichRepository.DEMO_URL,
    val email: String = MockImmichRepository.DEMO_EMAIL,
    val password: String = MockImmichRepository.DEMO_PASSWORD,
    val qr: QrState = QrState.WAIT,
    val error: String = "",
    val backdropHash: String? = null,
    val done: Boolean = false,
) {
    val host get() = url.removePrefix("https://").removePrefix("http://")
}

class ConnectViewModel(private val repo: ImmichRepository, private val settings: SettingsStore) : ViewModel() {
    private val _s = MutableStateFlow(ConnectState())
    val state = _s.asStateFlow()

    init {
        // Demo only: there is no library to borrow a backdrop from before signing in to a real server.
        if (repo.isDemo) viewModelScope.launch {
            val hash = repo.memories().firstOrNull()?.assets?.firstOrNull()?.thumbhash
            _s.update { it.copy(backdropHash = hash) }
        }
    }

    fun setUrl(v: String) = _s.update { it.copy(url = v) }
    fun setEmail(v: String) = _s.update { it.copy(email = v) }
    fun setPassword(v: String) = _s.update { it.copy(password = v) }
    fun editAddress() = _s.update { it.copy(mode = ConnectMode.FORM) }

    fun connect() {
        if (_s.value.mode == ConnectMode.CONNECTING) return
        _s.update { it.copy(mode = ConnectMode.CONNECTING) }
        viewModelScope.launch {
            val s = _s.value
            when (val r = repo.connect(s.url, s.email, s.password)) {
                ServerCheck.Ok -> { settings.signIn(s.url.trim().removeSuffix("/")); _s.update { it.copy(mode = ConnectMode.FORM, done = true) } }
                is ServerCheck.Unreachable -> _s.update { it.copy(mode = ConnectMode.ERROR, error = r.reason) }
                ServerCheck.InvalidCredentials -> _s.update { it.copy(mode = ConnectMode.ERROR, error = "Email or password is incorrect") }
            }
        }
    }

    /** QR pairing. Demo mode simulates the phone scanning and approving. */
    fun pairWithPhone() {
        if (_s.value.qr != QrState.WAIT) return
        viewModelScope.launch {
            _s.update { it.copy(qr = QrState.SCANNED) }
            delay(1000)
            _s.update { it.copy(qr = QrState.APPROVED) }
            delay(900)
            settings.signIn(MockImmichRepository.DEMO_URL)
            _s.update { it.copy(qr = QrState.WAIT, done = true) }
        }
    }
}

@Composable
fun ConnectScreen(vm: ConnectViewModel = koinViewModel()) {
    val app = LocalApp.current
    val s by vm.state.collectAsStateWithLifecycle()
    val c = Glume.colors
    val qrFr = remember { FocusRequester() }
    val urlFr = remember { FocusRequester() }
    val emailFr = remember { FocusRequester() }
    val passFr = remember { FocusRequester() }
    val goFr = remember { FocusRequester() }
    val retryFr = remember { FocusRequester() }

    LaunchedEffect(s.done) { if (s.done) app.goTop(ProfilesRoute) }
    LaunchedEffect(Unit) { runCatching { qrFr.requestFocus() } }
    LaunchedEffect(s.mode) { if (s.mode == ConnectMode.ERROR) runCatching { retryFr.requestFocus() } }

    fun openKeyboard(label: String, value: String, secret: Boolean, next: FocusRequester, set: (String) -> Unit) {
        app.modal = KeyboardDialog(app, label, value, secret) { set(it); runCatching { next.requestFocus() } }
    }

    Box(Modifier.fillMaxSize()) {
        val bmp = rememberThumbHash(s.backdropHash)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.22f }.drawPlaceholder(bmp))
        Row(Modifier.fillMaxSize().padding(horizontal = 120.dp, vertical = 96.dp), horizontalArrangement = Arrangement.spacedBy(112.dp)) {
            // Left: brand, headline, QR pairing card
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(44.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(c.accentStrong), contentAlignment = Alignment.Center) {
                        GIcon(Ic.aperture, 32.dp, tint = Color.White)
                    }
                    T("Immich", GlumeType.h2)
                }
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    T("Connect your photo library", GlumeType.display, maxLines = 2)
                    T("Sign in once and this TV remembers you. Your photos stay on your own server.", GlumeType.body, color = c.text2, maxLines = 2, modifier = Modifier.widthIn(max = 760.dp))
                }
                FocusSurface(
                    onClick = vm::pairWithPhone,
                    modifier = Modifier.fillMaxWidth().focusRequester(qrFr).focusProperties { right = if (s.mode == ConnectMode.ERROR) retryFr else urlFr },
                    shape = RoundedCornerShape(28.dp),
                    focusScale = FocusScale.LARGE,
                    containerColor = c.surface,
                    border = BorderStroke(2.dp, c.line),
                ) {
                    Row(Modifier.padding(36.dp), horizontalArrangement = Arrangement.spacedBy(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(300.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(18.dp)) {
                            AsyncImage(MockImmichRepository.QR_URL, "QR code for phone pairing", Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Label("Recommended")
                            T("Scan with your phone", GlumeType.h2, maxLines = 2)
                            T("Point your phone camera at the code and approve the sign-in in the Immich app. No typing on the TV.", GlumeType.sec.copy(fontSize = 24.sp), color = c.text2, maxLines = 5)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                T("Pairing code", GlumeType.sec, color = c.text2)
                                T("K7F-29Q", GlumeType.title.copy(letterSpacing = 0.14.em), tabular = true)
                            }
                            val approved = s.qr == QrState.APPROVED
                            Row(
                                Modifier.clip(RoundedCornerShape(999.dp)).background(if (approved) Color(0x297FE0B0) else Color(0x297C8CFF)).padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PulseDot(if (approved) c.ok else c.accent, pulse = !approved)
                                T(
                                    when (s.qr) {
                                        QrState.WAIT -> "Waiting for your phone…"
                                        QrState.SCANNED -> "Scanned · approve on your phone"
                                        QrState.APPROVED -> "Approved on your phone"
                                    },
                                    GlumeType.badge,
                                )
                            }
                        }
                    }
                }
            }
            // Right: manual sign-in or the error card
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically)) {
                if (s.mode != ConnectMode.ERROR) {
                    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        Label("Or sign in with your server address", c.text3)
                        val toQr = Modifier.focusProperties { left = qrFr }
                        Field("Server URL", s.url, { openKeyboard("Server URL", s.url, false, emailFr, vm::setUrl) }, toQr.focusRequester(urlFr))
                        Field("Email", s.email, { openKeyboard("Email", s.email, false, passFr, vm::setEmail) }, toQr.focusRequester(emailFr))
                        Field("Password", "•".repeat(s.password.length), { openKeyboard("Password", s.password, true, goFr, vm::setPassword) }, toQr.focusRequester(passFr), valueTracking = true)
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            PillButton("Connect", vm::connect, Modifier.focusRequester(goFr).focusProperties { left = qrFr }, primary = true)
                            T("TV and server must be on the same network", GlumeType.sec, color = c.text2)
                        }
                        if (s.mode == ConnectMode.CONNECTING) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                T("Connecting to ${s.host}…", GlumeType.sec, color = c.text2)
                                IndeterminateBar()
                            }
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surface)
                            .border(2.dp, Color(0x59FF8A7A), RoundedCornerShape(28.dp)).padding(52.dp),
                        verticalArrangement = Arrangement.spacedBy(26.dp),
                    ) {
                        Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(Color(0x24FF8A7A)), contentAlignment = Alignment.Center) {
                            GIcon(Ic.cloudOff, 48.dp, tint = c.danger)
                        }
                        T("Can't reach server", GlumeType.h1)
                        T("We couldn't connect to ${s.host}. Check the address and that this TV is on the same network as your server.", GlumeType.body, color = c.text2, maxLines = 3)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            PillButton("Retry", vm::connect, Modifier.focusRequester(retryFr).focusProperties { left = qrFr }, icon = Ic.refresh, primary = true)
                            PillButton("Edit address", {
                                vm.editAddress()
                                app.modal = KeyboardDialog(app, "Server URL", s.url, false) { vm.setUrl(it); runCatching { emailFr.requestFocus() } }
                            })
                        }
                        T(s.error, GlumeType.sec, color = c.text2)
                    }
                }
            }
        }
    }
}

@Composable
private fun IndeterminateBar() {
    val t = rememberInfiniteTransition(label = "ind")
    val x by t.animateFloat(-1f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "x")
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.1f))) {
        Box(
            Modifier.fillMaxWidth(0.33f).height(6.dp).graphicsLayer { translationX = size.width * x }
                .clip(RoundedCornerShape(3.dp)).background(Glume.colors.accent),
        )
    }
}
