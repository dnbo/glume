@file:OptIn(ExperimentalFoundationApi::class)

package com.android.glume.ui.screens.albums

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.glume.data.ImmichRepository
import com.android.glume.domain.Album
import com.android.glume.ui.app.LocalApp
import com.android.glume.ui.app.MenuTarget
import com.android.glume.ui.app.ScreenFocus
import com.android.glume.ui.components.AlbumCard
import com.android.glume.ui.components.SkeletonBox
import com.android.glume.ui.components.onPlayPause
import com.android.glume.ui.components.rememberPivot
import com.android.glume.ui.components.remembered
import com.android.glume.ui.nav.SlideMode
import com.android.glume.ui.nav.SlideshowRequest
import com.android.glume.ui.screens.LoadViewModel
import com.android.glume.ui.screens.ScreenTitle
import com.android.glume.ui.screens.dataOrNull
import com.android.glume.ui.theme.Dims
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class AlbumsViewModel(private val repo: ImmichRepository) : LoadViewModel<List<Album>>() {
    init { refresh() }
    override suspend fun load() = repo.albums()
}

/** Albums grid (design board 05): 4 columns, focused row docks one row down. */
@Composable
fun AlbumsScreen(vm: AlbumsViewModel = koinViewModel()) {
    val app = LocalApp.current
    val state by vm.state.collectAsStateWithLifecycle()
    val albums = state.dataOrNull
    Box(Modifier.fillMaxSize()) {
        ScreenTitle("Albums", albums?.let { "${it.size} albums · ${it.count { a -> a.shared }} shared" })
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(404.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.padding(top = 140.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = Dims.CONTENT_LEFT.dp, end = Dims.SAFE_X.dp, top = 20.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                if (albums == null) {
                    items(8) { SkeletonBox(Modifier.fillMaxWidth().height(252.dp).clip(RoundedCornerShape(14.dp))) }
                } else {
                    items(albums, key = { it.id }) { a ->
                        AlbumCard(
                            a, onClick = { app.openAlbum(a.id) },
                            onLongClick = { app.showMenu(MenuTarget.AlbumT(a)) },
                            onFocused = { a.cover?.let { app.ambient = it.thumbhash } },
                            tileHeight = 252.dp, avatarSize = 48.dp,
                            tileModifier = Modifier.remembered(vm.focus, a.id).onPlayPause {
                                app.scope.launch { app.slideshow(SlideshowRequest(app.repo.albumAssets(a.id), SlideMode.NORMAL, sourceLabel = "Album: ${a.title}")) }
                            },
                        )
                    }
                }
            }
        }
    }
    ScreenFocus(vm.focus, albums != null, albums?.firstOrNull()?.id)
}
