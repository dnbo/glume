package com.android.glume.ui.screens.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.android.glume.data.ImmichRepository
import com.android.glume.data.SettingsStore
import com.android.glume.domain.User
import com.android.glume.ui.app.LocalApp
import com.android.glume.ui.components.FocusSurface
import com.android.glume.ui.components.GIcon
import com.android.glume.ui.components.Ic
import com.android.glume.ui.components.PillButton
import com.android.glume.ui.app.ScreenFocus
import com.android.glume.ui.components.T
import com.android.glume.ui.components.UrlImage
import com.android.glume.ui.components.drawPlaceholder
import com.android.glume.ui.components.remembered
import com.android.glume.ui.components.rememberThumbHash
import com.android.glume.ui.nav.ConnectRoute
import com.android.glume.ui.nav.HomeRoute
import com.android.glume.ui.screens.LoadViewModel
import com.android.glume.ui.screens.dataOrNull
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeType
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ProfilesData(val users: List<User>, val host: String, val backdropHash: String?)

class ProfilesViewModel(private val repo: ImmichRepository, private val settings: SettingsStore) : LoadViewModel<ProfilesData>() {
    init { refresh() }

    override suspend fun load(): ProfilesData {
        val host = (settings.prefs.value.serverUrl ?: repo.serverInfo().url).removePrefix("https://").removePrefix("http://")
        val hash = if (repo.isDemo) repo.memories().getOrNull(1)?.assets?.firstOrNull()?.thumbhash else null
        return ProfilesData(repo.users(), host, hash)
    }

    fun select(user: User, then: () -> Unit) = viewModelScope.launch { settings.selectUser(user.id); then() }
    fun signOut(then: () -> Unit) = viewModelScope.launch { settings.signOut(); then() }
}

@Composable
fun ProfilesScreen(vm: ProfilesViewModel = koinViewModel()) {
    val app = LocalApp.current
    val state by vm.state.collectAsStateWithLifecycle()
    val data = state.dataOrNull
    val c = Glume.colors
    Box(Modifier.fillMaxSize()) {
        val bmp = rememberThumbHash(data?.backdropHash)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.2f }.drawPlaceholder(bmp))
        Column(Modifier.fillMaxSize().padding(top = 150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("Who's browsing?", GlumeType.display)
            T(if (data != null) "Connected to ${data.host}" else " ", GlumeType.sec.copy(fontSize = 26.sp), color = c.text2, modifier = Modifier.padding(top = 18.dp))
            if (data != null) {
                Row(Modifier.padding(top = 110.dp), horizontalArrangement = Arrangement.spacedBy(72.dp)) {
                    data.users.forEach { u ->
                        ProfileItem(u.name, u.role, onClick = {
                            vm.select(u) { app.goTop(HomeRoute) }
                        }, modifier = Modifier.remembered(vm.focus, u.id)) {
                            if (u.avatarUrl != null) UrlImage(u.avatarUrl, Modifier.fillMaxSize())
                            else T(u.name.take(1), GlumeType.display, color = c.text2)
                        }
                    }
                    ProfileItem("Add user", "On your server", dashed = true, onClick = {
                        app.toast("Add users on your Immich server and they show up here", Ic.info)
                    }) {
                        GIcon(Ic.plus, 72.dp, strokeWidth = 1.6f)
                    }
                }
                PillButton("Sign out of this server", { vm.signOut { app.goTop(ConnectRoute) } }, Modifier.padding(top = 96.dp))
            }
        }
    }
    ScreenFocus(vm.focus, data != null, data?.users?.firstOrNull()?.id)
}

@Composable
private fun ProfileItem(
    name: String,
    sub: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dashed: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = Glume.colors
    Column(Modifier.width(240.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusSurface(
            onClick = onClick,
            modifier = modifier.size(220.dp),
            shape = CircleShape,
            containerColor = c.surface2,
            border = if (dashed) BorderStroke(3.dp, c.line) else null,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
        T(name, GlumeType.title, modifier = Modifier.padding(top = 22.dp))
        T(sub, GlumeType.sec, color = c.text2, modifier = Modifier.padding(top = 10.dp))
    }
}
