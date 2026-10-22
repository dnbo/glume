package com.android.glume.ui.screens.people
import androidx.compose.runtime.Composable
import com.android.glume.data.ImmichRepository
import com.android.glume.ui.screens.LoadViewModel
class PeopleViewModel(private val repo: ImmichRepository) : LoadViewModel<Unit>() { override suspend fun load() = Unit }
@Composable fun PeopleScreen() = Unit
