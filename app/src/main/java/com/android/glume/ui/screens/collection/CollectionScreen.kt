package com.android.glume.ui.screens.collection
import androidx.compose.runtime.Composable
import com.android.glume.data.ImmichRepository
import com.android.glume.ui.screens.LoadViewModel
sealed interface CollectionSource { data object Favorites : CollectionSource; data class Album(val id: String) : CollectionSource; data class Person(val id: String) : CollectionSource; data class Place(val title: String, val names: List<String>, val country: String) : CollectionSource }
class CollectionViewModel(val source: CollectionSource, private val repo: ImmichRepository) : LoadViewModel<Unit>() { override suspend fun load() = Unit }
@Composable fun CollectionScreen(source: CollectionSource) = Unit
