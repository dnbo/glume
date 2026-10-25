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
import androidx.compose.runtime.DisposableEffect
import com.android.glume.BuildConfig
import com.android.glume.data.ImmichRepository
import com.android.glume.data.auth.ServerUrl
import com.android.glume.data.auth.SignInError
import com.android.glume.data.auth.SignInResult
import com.android.glume.data.auth.SignInService
import com.android.glume.data.pairing.PairingServer
import com.android.glume.data.pairing.PairingState
import com.android.glume.ui.components.QrCode
import com.android.glume.ui.nav.HomeRoute
import com.android.glume.data.SettingsStore
import com.android.glume.ui.app.InterceptBack
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
import com.android.glume.ui.theme.FocusScale
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

enum class ConnectMode { FORM, CONNECTING, ERROR }

data class ConnectState(
    // Debug builds prefill the public Immich demo so the flow can be tried without a server.
    val url: String = if (BuildConfig.DEBUG) "https://demo.immich.app" else "",
    val email: String = if (BuildConfig.DEBUG) "demo@immich.app" else "",
    val password: String = if (BuildConfig.DEBUG) "demo" else "",
    val mode: ConnectMode = ConnectMode.FORM,
    val errorTitle: String = "",
    val error: String = "",
    val backdropHash: String? = null,
    val done: Boolean = false,
) {
    val host get() = ServerUrl.host(ServerUrl.normalize(url))
}

class ConnectViewModel(
    private val repo: ImmichRepository,
    private val signIn: SignInService,
    val pairing: PairingServer,
    private val settings: SettingsStore,
) : ViewModel() {
    private val _s = MutableStateFlow(ConnectState())
    val state = _s.asStateFlow()

    init {
        // Demo library only: borrow a backdrop photo. A real server has nothing to show before sign-in.
        if (repo.isDemo) viewModelScope.launch {
            val hash = repo.memories().firstOrNull()?.assets?.firstOrNull()?.thumbhash
            _s.update { it.copy(backdropHash = hash) }
        }
        // A sign-in finished on the phone.
        viewModelScope.launch {
            pairing.state.collect { st -> if (st is PairingState.SignedIn) finish(st.account.id) }
        }
    }

    fun setUrl(v: String) = _s.update { it.copy(url = v) }
    fun setEmail(v: String) = _s.update { it.copy(email = v) }
    fun setPassword(v: String) = _s.update { it.copy(password = v) }
    fun editAddress() = _s.update { it.copy(mode = ConnectMode.FORM) }

    /** Sign in with the on-TV form: same login -> limited key -> logout swap as the phone page. */
    fun connect() {
        if (_s.value.mode == ConnectMode.CONNECTING) return
        _s.update { it.copy(mode = ConnectMode.CONNECTING) }
        viewModelScope.launch {
            val s = _s.value
            when (val r = signIn.signIn(s.url, s.email, s.password)) {
                is SignInResult.Success -> finish(r.account.id)
                is SignInResult.Failed -> _s.update {
                    it.copy(
                        mode = ConnectMode.ERROR,
                        errorTitle = if (r.reason == SignInError.Unreachable) "Can't reach server" else "Couldn't sign in",
                        error = r.reason.message,
                    )
                }
            }
        }
    }

    private suspend fun finish(accountId: String) {
        settings.selectUser(accountId)
        _s.update { it.copy(mode = ConnectMode.FORM, done = true) }
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

    LaunchedEffect(s.done) { if (s.done) app.goTop(HomeRoute) }
    val pairing by vm.pairing.state.collectAsStateWithLifecycle()
    // The phone page is only served while this screen is visible.
    DisposableEffect(Unit) {
        vm.pairing.restart()
        onDispose { vm.pairing.stop() }
    }
    LaunchedEffect(Unit) { runCatching { qrFr.requestFocus() } }
    LaunchedEffect(s.mode) { if (s.mode == ConnectMode.ERROR) runCatching { retryFr.requestFocus() } }

    InterceptBack(s.mode == ConnectMode.ERROR) { vm.editAddress(); runCatching { urlFr.requestFocus() } }

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
                    onClick = { if (pairing is PairingState.Failed || pairing is PairingState.NoNetwork) vm.pairing.restart() },
                    modifier = Modifier.fillMaxWidth().focusRequester(qrFr).focusProperties { right = if (s.mode == ConnectMode.ERROR) retryFr else urlFr },
                    shape = RoundedCornerShape(28.dp),
                    focusScale = FocusScale.LARGE,
                    containerColor = c.surface,
                    border = BorderStroke(2.dp, c.line),
                ) {
                    Row(Modifier.padding(36.dp), horizontalArrangement = Arrangement.spacedBy(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(300.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(18.dp), contentAlignment = Alignment.Center) {
                            val url = pairing.url()
                            if (url != null) QrCode(url, Modifier.fillMaxSize())
                            else GIcon(if (pairing is PairingState.NoNetwork) Ic.cloudOff else Ic.refresh, 64.dp, tint = Color(0xFF858DA0))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Label("Recommended")
                            T("Scan with your phone", GlumeType.title, maxLines = 1)
                            T(
                                if (pairing is PairingState.NoNetwork) "This TV isn't on a network your phone can reach. Use the form instead."
                                else "Point your phone camera at the code and sign in on your phone. No typing on the TV.",
                                GlumeType.sec.copy(fontSize = 24.sp), color = c.text2, maxLines = 3,
                            )
                            pairing.code()?.let { code ->
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    T("Pairing code", GlumeType.sec, color = c.text2)
                                    T(code, GlumeType.title.copy(letterSpacing = 0.14.em), tabular = true)
                                }
                            }
                            PairingStatus(pairing)
                            pairing.url()?.let { T(it.substringBefore("/p/"), GlumeType.sec, color = c.text3) }
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
                        T(s.errorTitle, GlumeType.h1)
                        T(s.error, GlumeType.body, color = c.text2, maxLines = 3)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            PillButton("Retry", vm::connect, Modifier.focusRequester(retryFr).focusProperties { left = qrFr }, icon = Ic.refresh, primary = true)
                            PillButton("Edit address", {
                                vm.editAddress()
                                app.modal = KeyboardDialog(app, "Server URL", s.url, false) { vm.setUrl(it); runCatching { emailFr.requestFocus() } }
                            })
                        }
                        T(s.host, GlumeType.sec, color = c.text2)
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

private fun PairingState.url(): String? = when (this) {
    is PairingState.Waiting -> url
    is PairingState.PhoneConnected -> url
    is PairingState.SigningIn -> url
    is PairingState.Failed -> url
    else -> null
}

private fun PairingState.code(): String? = when (this) {
    is PairingState.Waiting -> code
    is PairingState.PhoneConnected -> code
    is PairingState.SigningIn -> code
    is PairingState.Failed -> code
    else -> null
}

@Composable
private fun PairingStatus(state: PairingState) {
    val c = Glume.colors
    val (text, color, pulse) = when (state) {
        PairingState.Starting -> Triple("Preparing a code…", c.accent, true)
        is PairingState.Waiting -> Triple("Waiting for your phone…", c.accent, true)
        is PairingState.PhoneConnected -> Triple("Phone connected · sign in there", c.accent, true)
        is PairingState.SigningIn -> Triple("Signing in…", c.accent, true)
        is PairingState.SignedIn -> Triple("Signed in as ${state.account.name}", c.ok, false)
        is PairingState.Failed -> Triple(state.message, c.danger, false)
        PairingState.NoNetwork -> Triple("No network · press OK to retry", c.danger, false)
    }
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PulseDot(color, pulse = pulse)
        T(text, GlumeType.badge, maxLines = 2)
    }
}
