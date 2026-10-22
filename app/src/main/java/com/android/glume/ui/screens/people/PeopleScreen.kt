@file:OptIn(ExperimentalFoundationApi::class)

package com.android.glume.ui.screens.people

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.glume.data.ImmichRepository
import com.android.glume.domain.Person
import com.android.glume.ui.app.LocalApp
import com.android.glume.ui.app.MenuTarget
import com.android.glume.ui.app.ScreenFocus
import com.android.glume.ui.components.Ic
import com.android.glume.ui.components.PersonAvatar
import com.android.glume.ui.components.SkeletonBox
import com.android.glume.ui.components.T
import com.android.glume.ui.components.onPlayPause
import com.android.glume.ui.components.rememberPivot
import com.android.glume.ui.components.remembered
import com.android.glume.ui.nav.SlideMode
import com.android.glume.ui.nav.SlideshowRequest
import com.android.glume.ui.screens.LoadViewModel
import com.android.glume.ui.screens.ScreenTitle
import com.android.glume.ui.screens.dataOrNull
import com.android.glume.ui.theme.Dims
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeType
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class PeopleViewModel(private val repo: ImmichRepository) : LoadViewModel<List<Person>>() {
    init { refresh() }
    override suspend fun load() = repo.people()
}

/** People (design board 06): named faces, then unnamed faces under their own heading. */
@Composable
fun PeopleScreen(vm: PeopleViewModel = koinViewModel()) {
    val app = LocalApp.current
    val state by vm.state.collectAsStateWithLifecycle()
    val people = state.dataOrNull
    val named = people?.filter { it.name != null }.orEmpty()
    val unnamed = people?.filter { it.name == null }.orEmpty()
    val c = Glume.colors
    Box(Modifier.fillMaxSize()) {
        ScreenTitle("People", people?.let { "${named.size} people · ${unnamed.size} unnamed" })
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(340.dp, band = 340.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.padding(top = 140.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = Dims.CONTENT_LEFT.dp, end = Dims.SAFE_X.dp, top = 24.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(58.dp),
                verticalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                if (people == null) {
                    items(14) { SkeletonBox(Modifier.size(180.dp).clip(CircleShape)) }
                    return@LazyVerticalGrid
                }
                items(named, key = { it.id }) { p -> Face(p, vm, onClick = { app.openPerson(p.id) }) }
                if (unnamed.isNotEmpty()) {
                    item(key = "unnamed", span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.padding(top = 30.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            T("Unnamed faces", GlumeType.h2)
                            T("Name them in the Immich app to group them here", GlumeType.sec, color = c.text2)
                        }
                    }
                    items(unnamed, key = { it.id }) { p ->
                        Face(p, vm, onClick = { app.toast("Name this face in the Immich app to group their photos", Ic.info) })
                    }
                }
            }
        }
    }
    ScreenFocus(vm.focus, people != null, named.firstOrNull()?.id)
}

@Composable
private fun Face(p: Person, vm: PeopleViewModel, onClick: () -> Unit) {
    val app = LocalApp.current
    PersonAvatar(
        p, 180.dp, onClick = onClick, width = 180.dp, showCount = true,
        onLongClick = if (p.name != null) ({ app.showMenu(MenuTarget.PersonT(p)) }) else null,
        avatarModifier = Modifier.remembered(vm.focus, p.id).onPlayPause {
            if (p.name != null) app.scope.launch { app.slideshow(SlideshowRequest(app.repo.personAssets(p.id), SlideMode.NORMAL, sourceLabel = "Person: ${p.name}")) }
        },
    )
}
