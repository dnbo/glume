@file:OptIn(ExperimentalFoundationApi::class)

package com.android.glume.ui.screens.timeline

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.android.glume.data.ImmichRepository
import com.android.glume.domain.Asset
import com.android.glume.domain.TimeBucket
import com.android.glume.domain.YearMonthKey
import com.android.glume.ui.app.LocalApp
import com.android.glume.ui.app.MenuTarget
import com.android.glume.ui.app.ScreenFocus
import com.android.glume.ui.components.PhotoTile
import com.android.glume.ui.components.TileSkeleton
import com.android.glume.ui.components.T
import com.android.glume.ui.components.onPlayPause
import com.android.glume.ui.components.rememberPivot
import com.android.glume.ui.components.remembered
import com.android.glume.ui.nav.TimelineRoute
import com.android.glume.ui.screens.LoadViewModel
import com.android.glume.ui.screens.ScreenTitle
import com.android.glume.ui.screens.dataOrNull
import com.android.glume.ui.theme.Dims
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeType
import com.android.glume.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val COLS = 6

/** Flattened grid entries: a month header followed by that month's tiles. */
private sealed interface Entry {
    val bucket: Int
    data class Header(override val bucket: Int) : Entry
    data class Tile(override val bucket: Int, val i: Int) : Entry
}

class TimelineViewModel(private val repo: ImmichRepository) : LoadViewModel<List<TimeBucket>>() {
    private val _assets = MutableStateFlow<Map<YearMonthKey, List<Asset>>>(emptyMap())
    val assets = _assets.asStateFlow()
    private val inFlight = HashSet<YearMonthKey>()

    init { refresh() }

    override suspend fun load() = repo.timelineBuckets()

    /** Loads a month's assets the first time it scrolls into view. */
    fun ensure(key: YearMonthKey) {
        if (key in _assets.value || !inFlight.add(key)) return
        viewModelScope.launch {
            val list = repo.timelineBucket(key)
            _assets.update { it + (key to list) }
            inFlight.remove(key)
        }
    }

    /** Everything loaded so far, in timeline order, for the viewer. */
    fun loadedAssets(buckets: List<TimeBucket>): List<Asset> = buckets.flatMap { _assets.value[it.key].orEmpty() }
}

/** Timeline (design boards 04 / 04b). */
@Composable
fun TimelineScreen(route: TimelineRoute, vm: TimelineViewModel = koinViewModel()) {
    val app = LocalApp.current
    val state by vm.state.collectAsStateWithLifecycle()
    val assets by vm.assets.collectAsStateWithLifecycle()
    val favs by app.repo.favoriteIds.collectAsStateWithLifecycle()
    val buckets = state.dataOrNull
    val c = Glume.colors
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val scrubFr = remember { FocusRequester() }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubG by remember { mutableIntStateOf(0) }

    val entries = remember(buckets) {
        buckets?.flatMapIndexed { g, b -> listOf(Entry.Header(g)) + List(b.count) { Entry.Tile(g, it) } }.orEmpty()
    }
    val headerIndex = remember(entries) { entries.withIndex().filter { it.value is Entry.Header }.map { it.index } }
    val total = buckets?.sumOf { it.count } ?: 0

    fun tileKey(g: Int, i: Int) = buckets!![g].key.let { "t-${it.year}-${it.month}-$i" }

    /** Scrolls to month [g] and focuses its first tile. */
    fun jumpTo(g: Int) {
        val b = buckets ?: return
        if (g !in b.indices) return
        scope.launch {
            grid.scrollToItem(headerIndex[g])
            vm.ensure(b[g].key)
            repeat(6) {
                withFrameNanos { }
                if (vm.focus.focus(tileKey(g, 0))) return@launch
            }
        }
    }

    // Deep link from Home's "Back where you were".
    LaunchedEffect(buckets) {
        val b = buckets ?: return@LaunchedEffect
        if (route.year > 0 && vm.focus.lastKey == null) {
            val g = b.indexOfFirst { it.key.year == route.year && it.key.month == route.month }
            if (g >= 0) jumpTo(g)
        }
    }

    val firstVisible by remember { derivedStateOf { grid.firstVisibleItemIndex } }
    val stickyG = entries.getOrNull(firstVisible)?.bucket ?: 0
    val focusedG = remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        ScreenTitle("Timeline", if (buckets != null) Format.count(total, "photo") + " & videos" else null)
        if (buckets != null) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(264.dp)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(COLS),
                    state = grid,
                    modifier = Modifier.padding(top = 140.dp).width(1700.dp).fillMaxSize()
                        .onPreviewKeyEvent { e ->
                            // Hold ▲ ▼ to jump a month at a time.
                            if (e.type != KeyEventType.KeyDown || e.nativeKeyEvent.repeatCount == 0) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionUp -> { jumpTo(focusedG.intValue - 1); true }
                                Key.DirectionDown -> { jumpTo(focusedG.intValue + 1); true }
                                else -> false
                            }
                        },
                    contentPadding = PaddingValues(start = Dims.CONTENT_LEFT.dp, end = (1700 - Dims.CONTENT_LEFT - 1444).dp, top = 16.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(
                        count = entries.size,
                        key = { idx -> entries[idx].let { e -> if (e is Entry.Tile) tileKey(e.bucket, e.i) else "h-${buckets[e.bucket].key}" } },
                        span = { idx -> if (entries[idx] is Entry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                        contentType = { idx -> if (entries[idx] is Entry.Header) 0 else 1 },
                    ) { idx ->
                        when (val e = entries[idx]) {
                            is Entry.Header -> {
                                val b = buckets[e.bucket]
                                LaunchedEffect(b.key) { vm.ensure(b.key) }
                                MonthHeader(b, Modifier.padding(top = if (e.bucket == 0) 0.dp else 8.dp))
                            }
                            is Entry.Tile -> {
                                val b = buckets[e.bucket]
                                val list = assets[b.key]
                                val a = list?.getOrNull(e.i)
                                val key = tileKey(e.bucket, e.i)
                                if (a == null) {
                                    LaunchedEffect(b.key) { vm.ensure(b.key) }
                                    TileSkeleton(Modifier.size(224.dp))
                                } else {
                                    val firstOfDay = e.i == 0 || list[e.i - 1].date != a.date
                                    PhotoTile(
                                        a, favorite = a.id in favs,
                                        onClick = { val all = vm.loadedAssets(buckets); app.openViewer(all, all.indexOf(a)) },
                                        onLongClick = { app.showMenu(MenuTarget.Photo(a, list, e.i)) },
                                        onFocused = { app.ambient = a.thumbhash; focusedG.intValue = e.bucket },
                                        dayLabel = if (firstOfDay) Format.dayMonth(a.date) else null,
                                        inset = 10.dp,
                                        modifier = Modifier.size(224.dp).remembered(vm.focus, key)
                                            .onPlayPause { val all = vm.loadedAssets(buckets); app.slideshowFrom(all, all.indexOf(a)) }
                                            .onKeyEvent { ev ->
                                                // ▶ on the last column (or last tile of a month) enters the scrubber.
                                                val lastInRow = e.i % COLS == COLS - 1 || e.i == b.count - 1
                                                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionRight && lastInRow) {
                                                    scrubG = e.bucket; runCatching { scrubFr.requestFocus() }; true
                                                } else false
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val headerAtTop = entries.getOrNull(firstVisible) is Entry.Header && grid.firstVisibleItemScrollOffset < 30
            if (firstVisible > 0 && !headerAtTop) {
                Row(
                    Modifier.padding(top = 140.dp).width(1700.dp).height(100.dp)
                        .background(Brush.verticalGradient(0f to c.bg, 0.72f to c.bg.copy(alpha = 0.94f), 1f to Color.Transparent))
                        .padding(start = Dims.CONTENT_LEFT.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val b = buckets[stickyG]
                    T(Format.monthYear(b.key.year, b.key.month), GlumeType.h2)
                    T("${b.count} items", GlumeType.sec, color = c.text2, tabular = true)
                }
            }
            Scrubber(
                buckets, current = if (scrubbing) scrubG else focusedG.intValue, active = scrubbing,
                modifier = Modifier.offset(x = 1700.dp, y = 150.dp).focusRequester(scrubFr)
                    .onFocusChanged { scrubbing = it.isFocused; if (it.isFocused) scrubG = focusedG.intValue }
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent e.key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionCenter, Key.Enter)
                        when (e.key) {
                            Key.DirectionUp, Key.DirectionDown -> {
                                val dir = if (e.key == Key.DirectionUp) -1 else 1
                                scrubG = if (e.nativeKeyEvent.repeatCount > 0) {
                                    // Hold: jump a year, landing on the nearest month.
                                    val cur = buckets[scrubG].key
                                    val ty = cur.year - dir
                                    buckets.withIndex().filter { it.value.key.year == ty }.minByOrNull { kotlin.math.abs(it.value.key.month - cur.month) }?.index
                                        ?: if (dir < 0) 0 else buckets.lastIndex
                                } else (scrubG + dir).coerceIn(0, buckets.lastIndex)
                                scope.launch { grid.scrollToItem(headerIndex[scrubG]) }
                                true
                            }
                            Key.DirectionLeft, Key.DirectionCenter, Key.Enter -> { jumpTo(scrubG); true }
                            else -> false
                        }
                    }
                    .focusable(),
            )
        }
    }
    ScreenFocus(vm.focus, buckets != null && assets.isNotEmpty(), buckets?.let { tileKey(0, 0) })
}

@Composable
private fun MonthHeader(b: TimeBucket, modifier: Modifier) {
    Row(modifier.height(88.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        T(Format.monthYear(b.key.year, b.key.month), GlumeType.h2)
        T(if (b.count == 1) "1 item" else "${b.count} items", GlumeType.sec, color = Glume.colors.text2, tabular = true)
    }
}

/** Right-edge scrubber: year labels, a thumb at the current month and a bubble while focused. */
@Composable
private fun Scrubber(buckets: List<TimeBucket>, current: Int, active: Boolean, modifier: Modifier) {
    val c = Glume.colors
    val g = (buckets.size - 1).coerceAtLeast(1)
    fun pos(i: Int) = i.toFloat() / g * 840f + 10f
    val thumbY by animateFloatAsState(pos(current), tween(180), label = "thumb")
    val curYear = buckets.getOrNull(current)?.key?.year
    Box(modifier.size(124.dp, 860.dp)) {
        Box(Modifier.offset(x = 98.dp).width(if (active) 8.dp else 4.dp).fillMaxHeightCompat().clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = if (active) 0.35f else 0.12f)))
        buckets.forEachIndexed { i, b ->
            if (i == 0 || buckets[i - 1].key.year != b.key.year) {
                T(
                    b.key.year.toString(), GlumeType.badge,
                    Modifier.offset(y = (pos(i) - 11).dp).width(82.dp),
                    color = if (b.key.year == curYear) c.text else c.text3, tabular = true,
                    align = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
        val s = if (active) 28 else 16
        Box(
            Modifier.offset(x = (if (active) 88 else 92).dp, y = (thumbY - s / 2).dp).size(s.dp).clip(CircleShape)
                .background(if (active) Color.White else c.accent),
        )
        if (active) {
            val b = buckets[current]
            Column(
                // 236 px bubble, right edge 64 px left of the track; requiredWidth overflows centered in the 124 px slot.
                Modifier.offset(x = (-120).dp, y = (thumbY - 50).dp).requiredWidth(236.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                T(Format.monthYear(b.key.year, b.key.month), GlumeType.title, color = Color(0xFF0A0C11), tabular = true)
                T("Hold ▲ ▼ to jump a year", GlumeType.badge.copy(fontWeight = FontWeight(600)), color = Color(0xFF3A4050))
            }
        }
    }
}

private fun Modifier.fillMaxHeightCompat() = this.then(Modifier.height(860.dp))
