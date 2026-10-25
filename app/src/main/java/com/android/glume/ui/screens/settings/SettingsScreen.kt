package com.android.glume.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.android.glume.BuildConfig
import com.android.glume.data.ImmichRepository
import com.android.glume.data.Prefs
import com.android.glume.data.SettingKey
import com.android.glume.data.SettingsStore
import com.android.glume.data.auth.Account
import com.android.glume.data.auth.AccountStore
import com.android.glume.data.auth.ServerUrl
import com.android.glume.domain.ServerInfo
import com.android.glume.ui.app.AppController
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.onFocusChanged
import com.android.glume.ui.app.InterceptBack
import com.android.glume.ui.app.LocalApp
import com.android.glume.ui.app.ScreenFocus
import com.android.glume.ui.components.FocusMemory
import com.android.glume.ui.components.GIcon
import com.android.glume.ui.components.Ic
import com.android.glume.ui.components.ListRow
import com.android.glume.ui.components.ProgressBar
import com.android.glume.ui.components.RowSub
import com.android.glume.ui.components.T
import com.android.glume.ui.components.Toggle
import com.android.glume.ui.components.remembered
import com.android.glume.ui.nav.ConnectRoute
import com.android.glume.ui.nav.ProfilesRoute
import com.android.glume.ui.nav.SlideMode
import com.android.glume.ui.nav.SlideshowRequest
import com.android.glume.ui.screens.Session
import com.android.glume.ui.theme.Dims
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel

class SettingsViewModel(private val repo: ImmichRepository, private val settings: SettingsStore, private val accounts: AccountStore, private val context: Context) : ViewModel() {
    val focus = FocusMemory()
    private val _server = MutableStateFlow<ServerInfo?>(null)
    val server = _server.asStateFlow()
    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes = _cacheBytes.asStateFlow()

    init {
        viewModelScope.launch { _server.value = repo.serverInfo() }
        refreshCache()
    }

    fun set(key: SettingKey, value: Any) = viewModelScope.launch { settings.set(key, value) }

    private fun refreshCache() = viewModelScope.launch {
        _cacheBytes.value = withContext(Dispatchers.IO) { SingletonImageLoader.get(context).diskCache?.size ?: 0L }
    }

    fun clearCache(then: (Long) -> Unit) = viewModelScope.launch {
        val freed = _cacheBytes.value
        withContext(Dispatchers.IO) {
            val loader = SingletonImageLoader.get(context)
            loader.diskCache?.clear()
            loader.memoryCache?.clear()
        }
        refreshCache()
        then(freed)
    }

    /** Forgets this account on the TV; returns whether other accounts remain. */
    fun signOut(account: Account?, then: (othersLeft: Boolean) -> Unit) = viewModelScope.launch {
        if (account != null) accounts.remove(account.id)
        settings.clearUser()
        then(accounts.accounts.value.orEmpty().any { it.id != account?.id })
    }
}

/** One row on the right: either a [SettingKey] (toggle/cycle) or a fixed info/action row. */
private sealed interface Row_ {
    val label: String
    val sub: String?
    data class Setting(val key: SettingKey, override val label: String, override val sub: String? = null) : Row_
    data class Info(override val label: String, val value: String, override val sub: String? = null, val warn: Boolean = false) : Row_
    data class Storage(override val label: String, val value: String, val fraction: Float, override val sub: String?) : Row_
    data class Action(override val label: String, override val sub: String? = null, val run: () -> Unit) : Row_
}

private data class Category(val label: String, val icon: String, val rows: List<Row_>)

private fun gb(bytes: Long) = if (bytes < 1_000_000) "${bytes / 1000} KB" else if (bytes < 1_000_000_000) "${bytes / 1_000_000} MB" else "%.2f GB".format(bytes / 1e9)

/** Settings (design board 11 and St-HighContrast). */
@Composable
fun SettingsScreen(vm: SettingsViewModel = koinViewModel(), session: Session = koinInject()) {
    val app = LocalApp.current
    val prefs by app.settings.prefs.collectAsStateWithLifecycle()
    val server by vm.server.collectAsStateWithLifecycle()
    val cache by vm.cacheBytes.collectAsStateWithLifecycle()
    val user by session.user.collectAsStateWithLifecycle()
    val account by session.account.collectAsStateWithLifecycle()
    val c = Glume.colors
    var cat by remember { mutableIntStateOf(0) }
    var rowsFocused by remember { mutableStateOf(false) }
    val cats = categories(app, vm, prefs, server, cache, account)

    Box(Modifier.fillMaxSize()) {
        T("Settings", GlumeType.h1, Modifier.offset(x = Dims.CONTENT_LEFT.dp, y = Dims.SAFE_Y.dp))
        Column(Modifier.offset(x = Dims.CONTENT_LEFT.dp, y = 160.dp).width(440.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cats.forEachIndexed { i, k ->
                ListRow(
                    onClick = { vm.focus.focus("row:$i:0") },
                    modifier = Modifier.remembered(vm.focus, "cat:$i").onKeyEvent { e ->
                        // ▶ enters the category at its first row.
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) vm.focus.focus("row:$i:0") else false
                    },
                    selected = i == cat, minHeight = 76.dp,
                    onFocused = { cat = i },
                ) {
                    GIcon(k.icon, 28.dp)
                    T(k.label, GlumeType.body.copy(fontWeight = FontWeight(650)))
                }
            }
        }
        val current = cats[cat]
        // Back from a row returns to its category, one level up.
        InterceptBack(rowsFocused) { vm.focus.focus("cat:$cat") }
        Column(
            Modifier.offset(x = 720.dp, y = 160.dp).width(1104.dp).onFocusChanged { rowsFocused = it.hasFocus },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            T(current.label, GlumeType.h2, Modifier.padding(start = 28.dp, bottom = 18.dp))
            current.rows.forEachIndexed { r, row ->
                SettingRow(
                    row, prefs, vm,
                    Modifier.remembered(vm.focus, "row:$cat:$r").onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft -> if (row is Row_.Setting && !row.key.isToggle) { vm.set(row.key, row.key.next(prefs, -1)); true } else { vm.focus.focus("cat:$cat"); true }
                            Key.DirectionRight -> if (row is Row_.Setting) { vm.set(row.key, row.key.next(prefs, 1)); true } else true
                            else -> false
                        }
                    },
                )
            }
        }
    }
    ScreenFocus(vm.focus, true, "cat:0")
}

@Composable
private fun SettingRow(row: Row_, prefs: Prefs, vm: SettingsViewModel, modifier: Modifier) {
    val c = Glume.colors
    ListRow(
        onClick = {
            when (row) {
                is Row_.Setting -> vm.set(row.key, row.key.next(prefs, 1))
                is Row_.Action -> row.run()
                else -> Unit
            }
        },
        modifier = modifier.padding(vertical = 0.dp),
        focusScale = 1.03f,
    ) { focused ->
        Column(Modifier.weight(1f).padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(row.label, GlumeType.body.copy(fontWeight = FontWeight(650)))
            row.sub?.let { RowSub(it, focused) }
        }
        when (row) {
            is Row_.Setting -> if (row.key.isToggle) Toggle(row.key.read(prefs) as Boolean, focused) else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("‹", GlumeType.body, color = if (focused) c.focusInk else Color.Transparent)
                    T(row.key.display(prefs), GlumeType.body)
                    T("›", GlumeType.body, color = if (focused) c.focusInk else Color.Transparent)
                }
            }
            is Row_.Info -> T(row.value, GlumeType.body, color = if (row.warn) c.danger else androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.8f), tabular = true)
            is Row_.Storage -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProgressBar(row.fraction, Modifier.width(260.dp).clip(RoundedCornerShape(6.dp)), track = Color(0x4D7F7F8C), height = 12.dp)
                T(row.value, GlumeType.body, tabular = true)
            }
            is Row_.Action -> GIcon(Ic.chevR, 28.dp, strokeWidth = 2.4f)
        }
    }
}

private fun categories(app: AppController, vm: SettingsViewModel, p: Prefs, server: ServerInfo?, cache: Long, account: Account?): List<Category> {
    val host = account?.let { ServerUrl.host(it.serverUrl) }.orEmpty()
    val storage = server?.let { "%.2f of %d TB".format(it.usedBytes / 1e12, it.totalBytes / 1_000_000_000_000L) } ?: "…"
    return listOf(
        Category("Server", Ic.server, listOf(
            Row_.Info("Server address", host),
            Row_.Info("Status", if (p.simulateOffline) "Unreachable" else "Signed in", warn = p.simulateOffline),
            Row_.Info("Access", "Read-only key", "Can view, favorite and add to albums · can't delete or upload"),
            Row_.Storage("Storage", storage, server?.let { it.usedBytes.toFloat() / it.totalBytes } ?: 0f, server?.let { "%,d photos · %,d videos".format(it.photos, it.videos) }),
            Row_.Setting(SettingKey.SimulateOffline, "Simulate server unreachable", "Demo only · shows the offline state"),
        ) + if (app.repo.isDemo) listOf(Row_.Info("Library", "Demo data", "Photos are sample data until the real library is connected")) else emptyList()),
        Category("Account", Ic.user, listOf(
            Row_.Info("Signed in as", account?.name.orEmpty(), account?.email),
            Row_.Action("Switch user") { app.goTop(ProfilesRoute) },
            Row_.Action("Sign out", "Forgets this account on the TV · revoke its key in Immich › Account Settings › API Keys") {
                vm.signOut(account) { others -> app.goTop(if (others) ProfilesRoute else ConnectRoute) }
            },
        )),
        Category("Slideshow", Ic.play, listOf(
            Row_.Setting(SettingKey.SlideSource, "Show photos from"),
            Row_.Setting(SettingKey.Shuffle, "Shuffle"),
            Row_.Setting(SettingKey.Interval, "Interval"),
            Row_.Setting(SettingKey.Transition, "Transition"),
            Row_.Setting(SettingKey.KenBurns, "Ken Burns effect"),
            Row_.Setting(SettingKey.Captions, "Captions"),
            Row_.Setting(SettingKey.IncludeVideos, "Include videos"),
        )),
        Category("Screensaver", Ic.monitor, listOf(
            Row_.Setting(SettingKey.Saver, "Screensaver", "Ambient slideshow with the clock when idle"),
            Row_.Setting(SettingKey.SaverAfter, "Start after"),
            Row_.Setting(SettingKey.SaverSource, "Show"),
            Row_.Action("Preview screensaver") {
                app.scope.launch { app.slideshow(SlideshowRequest(app.assetsForSource(p.saverSource), SlideMode.AMBIENT)) }
            },
        )),
        Category("Image quality", Ic.image, listOf(
            Row_.Setting(SettingKey.PhotoQuality, "Photo quality", "Original uses more bandwidth and memory"),
            Row_.Setting(SettingKey.VideoQuality, "Video quality"),
        )),
        Category("Storage & cache", Ic.db, listOf(
            Row_.Info("Cached thumbnails", gb(cache)),
            Row_.Action("Clear cache", "Thumbnails download again as you browse") {
                vm.clearCache { freed -> app.toast("Cache cleared · ${gb(freed)} freed") }
            },
        )),
        Category("Accessibility", Ic.eye, listOf(
            Row_.Setting(SettingKey.HighContrast, "High contrast", "Pure black background and a yellow focus ring"),
            Row_.Setting(SettingKey.LargeText, "Larger text", "Scales all text by 15 %"),
            Row_.Setting(SettingKey.ReduceMotion, "Reduce motion", "No zoom-on-focus, Ken Burns or slides"),
        )),
        Category("Language", Ic.globe, listOf(
            Row_.Setting(SettingKey.Language, "Language"),
            Row_.Setting(SettingKey.DateFormat, "Date format"),
        )),
        Category("About", Ic.info, listOf(
            Row_.Info("App", "Immich TV · ${BuildConfig.VERSION_NAME}"),
            Row_.Info("Remote tip", "Hold OK for options"),
            Row_.Action("Open-source licenses", "Manrope (OFL), ThumbHash (MIT), AndroidX (Apache 2.0)") { app.toast("Licenses are bundled with the app", Ic.info) },
        )),
    )
}
