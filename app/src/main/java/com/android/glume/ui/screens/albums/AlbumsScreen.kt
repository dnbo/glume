package com.android.glume.ui.screens.albums
import androidx.compose.runtime.Composable
import com.android.glume.data.ImmichRepository
import com.android.glume.ui.screens.LoadViewModel
class AlbumsViewModel(private val repo: ImmichRepository) : LoadViewModel<Unit>() { override suspend fun load() = Unit }
@Composable fun AlbumsScreen() = Unit
