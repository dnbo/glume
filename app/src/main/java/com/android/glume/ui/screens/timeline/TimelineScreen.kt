package com.android.glume.ui.screens.timeline
import androidx.compose.runtime.Composable
import com.android.glume.data.ImmichRepository
import com.android.glume.ui.nav.TimelineRoute
import com.android.glume.ui.screens.LoadViewModel
class TimelineViewModel(private val repo: ImmichRepository) : LoadViewModel<Unit>() { override suspend fun load() = Unit }
@Composable fun TimelineScreen(route: TimelineRoute) = Unit
