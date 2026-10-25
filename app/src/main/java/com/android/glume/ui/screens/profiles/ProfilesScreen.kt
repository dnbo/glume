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
import com.android.glume.data.auth.Account
import com.android.glume.data.auth.AccountStore
import com.android.glume.data.auth.ServerUrl
import com.android.glume.ui.components.immichAvatarColor
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import com.android.glume.ui.app.LocalApp
import com.android.glume.ui.components.FocusSurface
import com.android.glume.ui.components.GIcon
import com.android.glume.ui.components.Ic
import com.android.glume.ui.components.PillButton
import com.android.glume.ui.app.ScreenFocus
import com.android.glume.ui.components.T
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

data class ProfilesData(val accounts: List<Account>, val backdropHash: String?)

class ProfilesViewModel(private val repo: ImmichRepository, private val accounts: AccountStore, private val settings: SettingsStore) : LoadViewModel<ProfilesData>() {
    init {
        viewModelScope.launch { accounts.accounts.filterNotNull().collect { refresh() } }
    }

    override suspend fun load(): ProfilesData {
        val hash = if (repo.isDemo) repo.memories().getOrNull(1)?.assets?.firstOrNull()?.thumbhash else null
        return ProfilesData(accounts.accounts.filterNotNull().first(), hash)
    }

    fun select(a: Account, then: () -> Unit) = viewModelScope.launch { settings.selectUser(a.id); then() }

    /** Forgets every account on this TV. Their keys stay valid on the server until revoked there. */
    fun signOutAll(then: () -> Unit) = viewModelScope.launch {
        accounts.accounts.value.orEmpty().forEach { accounts.remove(it.id) }
        settings.clearUser()
        then()
    }
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
            val hosts = data?.accounts?.map { ServerUrl.host(it.serverUrl) }?.distinct().orEmpty()
            T(if (hosts.isNotEmpty()) "Connected to ${hosts.joinToString(", ")}" else " ", GlumeType.sec.copy(fontSize = 26.sp), color = c.text2, modifier = Modifier.padding(top = 18.dp))
            if (data != null) {
                Row(Modifier.padding(top = 110.dp), horizontalArrangement = Arrangement.spacedBy(72.dp)) {
                    data.accounts.forEach { a ->
                        ProfileItem(a.name, a.email, onClick = {
                            vm.select(a) { app.goTop(HomeRoute) }
                        }, modifier = Modifier.remembered(vm.focus, a.id), fill = immichAvatarColor(a.avatarColor)) {
                            T(a.name.take(1).uppercase(), GlumeType.display, color = Color.White)
                        }
                    }
                    ProfileItem("Add user", "Sign in another account", dashed = true, onClick = {
                        app.nav.navigate(ConnectRoute)
                    }, modifier = Modifier.remembered(vm.focus, "add")) {
                        GIcon(Ic.plus, 72.dp, strokeWidth = 1.6f)
                    }
                }
                PillButton("Sign out of this TV", { vm.signOutAll { app.goTop(ConnectRoute) } }, Modifier.padding(top = 96.dp))
            }
        }
    }
    ScreenFocus(vm.focus, data != null, data?.accounts?.firstOrNull()?.id ?: "add")
}

@Composable
private fun ProfileItem(
    name: String,
    sub: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dashed: Boolean = false,
    fill: Color? = null,
    content: @Composable () -> Unit,
) {
    val c = Glume.colors
    Column(Modifier.width(240.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusSurface(
            onClick = onClick,
            modifier = modifier.size(220.dp),
            shape = CircleShape,
            containerColor = fill ?: c.surface2,
            border = if (dashed) BorderStroke(3.dp, c.line) else null,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
        T(name, GlumeType.title, modifier = Modifier.padding(top = 22.dp))
        T(sub, GlumeType.sec, color = c.text2, modifier = Modifier.padding(top = 10.dp))
    }
}
