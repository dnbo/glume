package com.android.glume.ui.screens.places
import androidx.compose.runtime.Composable
import com.android.glume.data.ImmichRepository
import com.android.glume.ui.nav.PlacesRoute
import com.android.glume.ui.screens.LoadViewModel
class PlacesViewModel(private val repo: ImmichRepository) : LoadViewModel<Unit>() { override suspend fun load() = Unit }
@Composable fun PlacesScreen(route: PlacesRoute) = Unit
