package com.android.glume.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.glume.data.ImmichRepository
import com.android.glume.data.SettingsStore
import com.android.glume.domain.User
import com.android.glume.ui.components.FocusMemory
import com.android.glume.ui.components.GIcon
import com.android.glume.ui.components.T
import com.android.glume.ui.theme.Dims
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

val <T> Load<T>.dataOrNull: T? get() = (this as? Load.Ready<T>)?.data

/**
 * Base for screens that load one thing. Holds the screen's [FocusMemory] so focus survives
 * navigating away and back (the ViewModel lives as long as the back stack entry).
 */
abstract class LoadViewModel<T> : ViewModel() {
    val focus = FocusMemory()
    private val _state = MutableStateFlow<Load<T>>(Load.Loading)
    val state: StateFlow<Load<T>> = _state.asStateFlow()

    protected abstract suspend fun load(): T

    fun refresh() {
        viewModelScope.launch {
            _state.value = runCatching { Load.Ready(load()) }.getOrElse { Load.Failed(it.message ?: "Something went wrong") }
        }
    }
}

/** The signed-in Immich user, resolved from the saved user id. */
class Session(repo: ImmichRepository, settings: SettingsStore, scope: CoroutineScope) {
    val user: StateFlow<User?> = settings.prefs
        .map { it.userId }
        .distinctUntilChanged()
        .map { id -> id?.let { repo.user(it) } }
        .stateIn(scope, SharingStarted.Eagerly, null)
}

/** "Albums   11 albums · 5 shared" at the top-left of rail screens. */
@Composable
fun ScreenTitle(title: String, sub: String?, modifier: Modifier = Modifier) {
    Row(
        modifier.offset(x = Dims.CONTENT_LEFT.dp, y = Dims.SAFE_Y.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        T(title, GlumeType.h1)
        if (sub != null) T(sub, GlumeType.sec, color = Glume.colors.text2, tabular = true, modifier = Modifier.offset(y = (-8).dp))
    }
}

/** Centered icon + headline + body, for empty and error states. */
@Composable
fun EmptyState(icon: String, title: String, body: String, modifier: Modifier = Modifier, iconBox: Dp = 160.dp, actions: @Composable () -> Unit = {}) {
    val c = Glume.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Box(Modifier.size(iconBox).clip(RoundedCornerShape(iconBox / 4)).background(c.surface2), contentAlignment = Alignment.Center) {
            GIcon(icon, iconBox / 2, strokeWidth = 1.5f, tint = c.text3)
        }
        T(title, GlumeType.h1, align = TextAlign.Center)
        T(body, GlumeType.body, color = c.text2, maxLines = 3, align = TextAlign.Center, modifier = Modifier.widthIn(max = 900.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.offset(y = 18.dp)) { actions() }
    }
}
