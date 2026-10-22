@file:OptIn(ExperimentalFoundationApi::class, FlowPreview::class)

package com.android.glume.ui.screens.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.android.glume.data.ImmichRepository
import com.android.glume.data.mock.MockLibrary
import com.android.glume.domain.Asset
import com.android.glume.ui.app.Caret
import com.android.glume.ui.app.Kb
import com.android.glume.ui.app.LocalApp
import com.android.glume.ui.app.MenuTarget
import com.android.glume.ui.app.ScreenFocus
import com.android.glume.ui.components.Chip
import com.android.glume.ui.components.FocusMemory
import com.android.glume.ui.components.GIcon
import com.android.glume.ui.components.Ic
import com.android.glume.ui.components.IconTile
import com.android.glume.ui.components.KeyButton
import com.android.glume.ui.components.ListRow
import com.android.glume.ui.components.PhotoTile
import com.android.glume.ui.components.T
import com.android.glume.ui.components.onPlayPause
import com.android.glume.ui.components.rememberPivot
import com.android.glume.ui.components.remembered
import com.android.glume.ui.theme.Dims
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeType
import com.android.glume.util.Format
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class SearchState(
    val query: String = "",
    val filters: List<Int> = List(5) { 0 },
    val results: List<Asset>? = null,
) {
    val active get() = query.isNotBlank() || filters.any { it > 0 }
}

class SearchViewModel(private val repo: ImmichRepository) : ViewModel() {
    val focus = FocusMemory()
    val filters = repo.searchFilters()
    private val _s = MutableStateFlow(SearchState(filters = List(filters.size) { 0 }))
    val state = _s.asStateFlow()

    init {
        viewModelScope.launch {
            _s.map { it.query to it.filters }.distinctUntilChanged().debounce(250).collect { (q, f) ->
                if (q.isBlank() && f.none { it > 0 }) _s.update { it.copy(results = null) }
                else { val r = repo.search(q, f); _s.update { if (it.query == q && it.filters == f) it.copy(results = r) else it } }
            }
        }
    }

    fun type(key: String) = _s.update { it.copy(query = Kb.apply(it.query, key)) }
    fun setQuery(q: String) = _s.update { it.copy(query = q) }
    fun cycleFilter(i: Int) = _s.update { s -> s.copy(filters = s.filters.toMutableList().also { it[i] = (it[i] + 1) % filters[i].options.size }) }
}

/** Search (design boards 08, 08b, St-SearchEmpty). */
@Composable
fun SearchScreen(vm: SearchViewModel = koinViewModel()) {
    val app = LocalApp.current
    val s by vm.state.collectAsStateWithLifecycle()
    val favs by app.repo.favoriteIds.collectAsStateWithLifecycle()
    val c = Glume.colors
    val ctx = LocalContext.current
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.setQuery(it.lowercase()) }
        }
    }
    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Try “sunset at the beach 2023” or “Emma at the lake”")
        if (intent.resolveActivity(ctx.packageManager) != null) voice.launch(intent)
        else app.toast("Voice search isn't available on this device", Ic.info)
    }

    Box(Modifier.fillMaxSize()) {
        // Left: field, mic, keyboard
        Column(
            Modifier.offset(x = Dims.CONTENT_LEFT.dp, y = Dims.SAFE_Y.dp).width(600.dp)
                .onPreviewKeyEvent { e ->
                    // A USB/Bluetooth keyboard types straight into the field.
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        e.key == Key.Backspace -> { vm.type("del"); true }
                        e.utf16CodePoint in 33..126 -> { vm.type(e.utf16CodePoint.toChar().lowercase()); true }
                        e.key == Key.Spacebar -> { vm.type("space"); true }
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(18.dp)).background(c.surface2)
                        .border(2.dp, c.accent, RoundedCornerShape(18.dp)).padding(horizontal = 28.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    GIcon(Ic.search, 30.dp, strokeWidth = 2.2f, tint = c.text2)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        T(s.query.ifEmpty { "Search your photos" }, GlumeType.body, color = if (s.query.isEmpty()) c.text3 else c.text, modifier = Modifier.weight(1f, fill = false))
                        Caret()
                    }
                }
                IconTile(Ic.mic, ::startVoice, Modifier.remembered(vm.focus, "mic"))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kb.ROWS.forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { col, k -> KeyButton(k, 76.dp, { vm.type(k) }, Modifier.remembered(vm.focus, "k$r-$col")) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KeyButton("Space", 248.dp, { vm.type("space") }, Modifier.remembered(vm.focus, "k-space"))
                    KeyButton("⌫", 162.dp, { vm.type("del") }, Modifier.remembered(vm.focus, "k-del"))
                    KeyButton("Search", 162.dp, { s.results?.firstOrNull()?.let { vm.focus.focus("r:${it.id}") } }, Modifier.remembered(vm.focus, "k-done"))
                }
            }
            T("Type, or press the mic and just say it, like “sunset at the beach 2023”.", GlumeType.sec, color = c.text2, maxLines = 2)
        }
        // Right: filters, then suggestions / results / empty state
        Column(Modifier.offset(x = 880.dp, y = Dims.SAFE_Y.dp).width(944.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                vm.filters.forEachIndexed { i, f ->
                    val sel = s.filters[i]
                    Chip(if (sel > 0) f.options[sel] else f.name, { vm.cycleFilter(i) }, Modifier.remembered(vm.focus, "chip$i"), icon = Ic.byName(f.icon), on = sel > 0)
                }
            }
            val results = s.results
            when {
                !s.active -> Column(Modifier.padding(top = 56.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("Try searching for", GlumeType.title, modifier = Modifier.padding(bottom = 12.dp))
                    MockLibrary.SUGGESTIONS.forEach { sug ->
                        ListRow({ vm.setQuery(sug) }, Modifier.remembered(vm.focus, "sug:$sug")) {
                            GIcon(Ic.sparkle, 28.dp)
                            T(sug, GlumeType.body)
                        }
                    }
                }
                results == null -> Unit
                results.isEmpty() -> Column(Modifier.padding(top = 120.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    Box(Modifier.size(120.dp).clip(RoundedCornerShape(32.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
                        GIcon(Ic.search, 60.dp, strokeWidth = 1.8f, tint = c.text3)
                    }
                    T("No results for “${s.query.trim()}”", GlumeType.h2, maxLines = 2)
                    T("Try fewer words, or search for a place, a person or a year. Smart search runs on your server, so new photos may take a moment to be indexed.", GlumeType.body, color = c.text2, maxLines = 3, align = TextAlign.Start)
                }
                else -> {
                    Row(Modifier.padding(top = 36.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        val filt = vm.filters.indices.filter { s.filters[it] > 0 }.map { vm.filters[it].options[s.filters[it]] }
                        T(if (s.query.isNotBlank()) "Results for “${s.query.trim()}”" else "Filtered: ${filt.joinToString(", ")}", GlumeType.title, modifier = Modifier.weight(1f, fill = false))
                        T("${results.size}${if (results.size == 60) "+" else ""} found", GlumeType.sec, color = c.text2, tabular = true)
                    }
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(239.dp)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.padding(top = 24.dp).offset(x = (-20).dp).width(984.dp).height(776.dp),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            itemsIndexed(results, key = { _, a -> a.id }) { i, a ->
                                PhotoTile(
                                    a, favorite = a.id in favs,
                                    onClick = { app.openViewer(results, i) },
                                    onLongClick = { app.showMenu(MenuTarget.Photo(a, results, i)) },
                                    onFocused = { app.ambient = a.thumbhash },
                                    inset = 10.dp,
                                    caption = Format.monthYearShort(a.date),
                                    modifier = Modifier.fillMaxWidth().height(199.dp).remembered(vm.focus, "r:${a.id}").onPlayPause { app.slideshowFrom(results, i) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    ScreenFocus(vm.focus, true, "k0-0")
}
