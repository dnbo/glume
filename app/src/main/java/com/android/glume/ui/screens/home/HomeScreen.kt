@file:OptIn(ExperimentalFoundationApi::class)

package com.android.glume.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.glume.data.ImmichRepository
import com.android.glume.domain.Album
import com.android.glume.domain.Asset
import com.android.glume.domain.ContinueItem
import com.android.glume.domain.ContinueKind
import com.android.glume.domain.HomeData
import com.android.glume.domain.Memory
import com.android.glume.domain.Person
import com.android.glume.domain.User
import com.android.glume.ui.app.AppController
import com.android.glume.ui.app.LocalApp
import com.android.glume.ui.app.MenuTarget
import com.android.glume.ui.components.AlbumCard
import com.android.glume.ui.components.Avatar
import com.android.glume.ui.components.immichAvatarColor
import com.android.glume.ui.components.ContinueCard
import com.android.glume.ui.components.Ic
import com.android.glume.ui.components.MemoryCard
import com.android.glume.ui.components.MemoryHero
import com.android.glume.ui.components.NoScrollSpec
import com.android.glume.ui.components.PersonAvatar
import com.android.glume.ui.components.PhotoTile
import com.android.glume.ui.components.PillButton
import com.android.glume.ui.app.ScreenFocus
import com.android.glume.ui.components.SkeletonBox
import com.android.glume.ui.components.T
import com.android.glume.ui.components.onPlayPause
import com.android.glume.ui.components.rememberPivot
import com.android.glume.ui.components.remembered
import com.android.glume.ui.nav.ProfilesRoute
import com.android.glume.ui.nav.SlideMode
import com.android.glume.ui.nav.SlideshowRequest
import com.android.glume.ui.nav.TimelineRoute
import com.android.glume.ui.screens.EmptyState
import com.android.glume.ui.screens.Load
import com.android.glume.ui.screens.LoadViewModel
import com.android.glume.ui.screens.Session
import com.android.glume.ui.theme.Dims
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeMotion
import com.android.glume.ui.theme.GlumeType
import com.android.glume.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.viewModelScope

data class HomeUi(val user: User, val data: HomeData)

class HomeViewModel(private val repo: ImmichRepository, private val session: Session) : LoadViewModel<HomeUi>() {
    init {
        viewModelScope.launch { session.user.filterNotNull().collect { refresh() } }
    }

    override suspend fun load(): HomeUi {
        val user = session.user.value ?: error("No user selected")
        return HomeUi(user, repo.home(user))
    }
}

private enum class RowType(val height: Dp, val stride: Dp) {
    CONTINUE(248.dp, 468.dp), PHOTO(224.dp, 364.dp), ALBUM(332.dp, 388.dp), PERSON(228.dp, 216.dp)
}

private sealed interface HomeRow {
    val key: String
    val title: String
    val sub: String?
    val type: RowType

    data class Continue(val items: List<ContinueItem>) : HomeRow {
        override val key = "cont"; override val title = "Continue"; override val sub = null; override val type = RowType.CONTINUE
    }
    data class Photos(override val key: String, override val title: String, override val sub: String?, val items: List<Asset>) : HomeRow {
        override val type = RowType.PHOTO
    }
    data class Albums(override val key: String, override val title: String, val items: List<Album>) : HomeRow {
        override val sub = null; override val type = RowType.ALBUM
    }
    data class People(val items: List<Person>) : HomeRow {
        override val key = "people"; override val title = "People"; override val sub = null; override val type = RowType.PERSON
    }
}

private fun rowsOf(d: HomeData): List<HomeRow> = listOfNotNull(
    HomeRow.Continue(d.continueItems).takeIf { d.continueItems.isNotEmpty() },
    HomeRow.Photos("recent", "Recently added", null, d.recent).takeIf { d.recent.isNotEmpty() },
    HomeRow.Photos("favs", "Favorites", null, d.favorites).takeIf { d.favorites.isNotEmpty() },
    HomeRow.Albums("albums", "Recent albums", d.albums).takeIf { d.albums.isNotEmpty() },
    HomeRow.People(d.people).takeIf { d.people.isNotEmpty() },
    HomeRow.Photos("partner", "${d.partnerName}’s photos", "Partner sharing", d.partnerPhotos).takeIf { d.partnerPhotos.isNotEmpty() },
    HomeRow.Albums("shared", "Shared with me", d.sharedWithMe).takeIf { d.sharedWithMe.isNotEmpty() },
)

private const val HERO_TOP = 130f
private const val HERO_H = 460f
private const val ROW_GAP = 56f
private const val ROW_HEAD = 60f
/** Rows 0-1 stay put; from row 2 the focused row docks this far from the top. */
private const val DOCK_Y = 150f

@Composable
fun HomeScreen(vm: HomeViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is Load.Ready -> if (s.data.data.isEmpty) HomeEmpty(vm) else HomeContent(vm, s.data)
        else -> HomeSkeleton()
    }
}

@Composable
private fun HomeContent(vm: HomeViewModel, ui: HomeUi) {
    val app = LocalApp.current
    val favs by app.repo.favoriteIds.collectAsStateWithLifecycle()
    val offline by app.settings.prefs.collectAsStateWithLifecycle()
    val rows = remember(ui) { rowsOf(ui.data) }
    // Row 0 is the hero; 1..n are the lazy rows.
    var focusedRow by remember { mutableIntStateOf(0) }
    val tops = remember(rows) {
        val list = ArrayList<Float>()
        var y = HERO_TOP
        list.add(y); y += HERO_H + ROW_GAP
        rows.forEach { list.add(y); y += ROW_HEAD + it.type.height.value + ROW_GAP }
        list
    }
    val targetY = if (focusedRow <= 1) 0f else tops[focusedRow] - DOCK_Y
    val scrollY by animateFloatAsState(targetY, tween(Glume.motion.scrollMs, easing = GlumeMotion.Ease), label = "homeY")

    CompositionLocalProvider(LocalBringIntoViewSpec provides NoScrollSpec) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .offset(x = (Dims.CONTENT_LEFT - 24).dp)
                    .width((1920 - Dims.CONTENT_LEFT + 24).dp)
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .graphicsLayer { translationY = -scrollY.dp.toPx() },
            ) {
                Greeting(ui.user, Modifier.padding(start = 24.dp, top = Dims.SAFE_Y.dp))
                // Hero row
                Row(
                    Modifier.padding(start = 24.dp, top = (HERO_TOP - Dims.SAFE_Y - 56).dp).onFocusChanged { if (it.hasFocus) focusedRow = 0 },
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    val memories = ui.data.memories
                    memories.firstOrNull()?.let { m ->
                        var heroFocused by remember { mutableStateOf(false) }
                        MemoryHero(
                            m,
                            onClick = { app.playMemory(m) },
                            onLongClick = { app.showMenu(MenuTarget.MemoryT(m)) },
                            onFocused = { heroFocused = true },
                            onFrame = { if (heroFocused) app.ambient = it.thumbhash },
                            modifier = Modifier.remembered(vm.focus, "hero").onPlayPause { app.playMemory(m) }
                                .onFocusChanged { heroFocused = it.isFocused; if (it.isFocused) app.ambient = m.assets.first().thumbhash },
                        )
                    }
                    memories.drop(1).take(2).forEach { m ->
                        MemoryCard(
                            m, onClick = { app.playMemory(m) }, onLongClick = { app.showMenu(MenuTarget.MemoryT(m)) },
                            onFocused = { app.ambient = m.assets.first().thumbhash },
                            modifier = Modifier.remembered(vm.focus, "mem:${m.id}").onPlayPause { app.playMemory(m) },
                        )
                    }
                }
                rows.forEachIndexed { i, row ->
                    HomeRowView(
                        row = row,
                        app = app,
                        vm = vm,
                        favs = favs,
                        offline = offline.simulateOffline && row.key == "shared",
                        modifier = Modifier.padding(top = ROW_GAP.dp).onFocusChanged { if (it.hasFocus) focusedRow = i + 1 },
                    )
                }
            }
        }
    }
    ScreenFocus(vm.focus, true, "hero")
}

@Composable
private fun Greeting(user: User, modifier: Modifier) {
    val c = Glume.colors
    val clock by produceState(Format.clock()) {
        while (true) { delay(10_000); value = Format.clock() }
    }
    Row(modifier.width((1920 - Dims.CONTENT_LEFT - Dims.SAFE_X).dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        T(Format.greeting(user.name), GlumeType.title, color = c.text2, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            T(clock, GlumeType.title, tabular = true)
            Avatar(user.avatarUrl, 52.dp, name = user.name, color = immichAvatarColor(user.avatarColor))
        }
    }
}

@Composable
private fun HomeRowView(row: HomeRow, app: AppController, vm: HomeViewModel, favs: Set<String>, offline: Boolean, modifier: Modifier) {
    val c = Glume.colors
    val listState = rememberLazyListState()
    Column(modifier) {
        Row(Modifier.padding(start = 24.dp).height(40.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            T(row.title, GlumeType.title)
            row.sub?.let { T(it, GlumeType.sec, color = c.text2) }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot((24 + row.type.stride.value).dp)) {
            LazyRow(
                state = listState,
                modifier = Modifier.padding(top = 20.dp).fillMaxWidth().height(row.type.height).focusRestorer(),
                contentPadding = PaddingValues(start = 24.dp, end = (Dims.SAFE_X + 24).dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                when (row) {
                    is HomeRow.Continue -> itemsIndexed(row.items, key = { _, it -> it.kind.name }) { _, it ->
                        ContinueCard(
                            it, onClick = { onContinue(app, it) }, onFocused = { app.ambient = it.cover.thumbhash },
                            modifier = Modifier.remembered(vm.focus, "cont:${it.kind}"),
                        )
                    }
                    is HomeRow.Photos -> itemsIndexed(row.items, key = { _, it -> it.id }) { i, a ->
                        PhotoTile(
                            a, favorite = a.id in favs,
                            onClick = { app.openViewer(row.items, i) },
                            onLongClick = { app.showMenu(MenuTarget.Photo(a, row.items, i)) },
                            onFocused = { app.ambient = a.thumbhash },
                            modifier = Modifier.size(336.dp, 224.dp).remembered(vm.focus, "${row.key}:${a.id}").onPlayPause { app.slideshowFrom(row.items, i) },
                        )
                    }
                    is HomeRow.Albums -> itemsIndexed(row.items, key = { _, it -> it.id }) { _, al ->
                        AlbumCard(
                            al,
                            onClick = { app.openAlbum(al.id) },
                            onLongClick = { app.showMenu(MenuTarget.AlbumT(al)) },
                            onFocused = { al.cover?.let { app.ambient = it.thumbhash } },
                            modifier = Modifier.width(360.dp),
                            tileModifier = Modifier.remembered(vm.focus, "${row.key}:${al.id}").onPlayPause { playAlbum(app, al) },
                            offline = offline,
                        )
                    }
                    is HomeRow.People -> itemsIndexed(row.items, key = { _, it -> it.id }) { _, p ->
                        PersonAvatar(
                            p, 176.dp, onClick = { app.openPerson(p.id) },
                            onLongClick = { app.showMenu(MenuTarget.PersonT(p)) },
                            width = 188.dp,
                            avatarModifier = Modifier.remembered(vm.focus, "people:${p.id}").onPlayPause { playPerson(app, p) },
                        )
                    }
                }
            }
        }
    }
}

private fun onContinue(app: AppController, it: ContinueItem) {
    when (it.kind) {
        ContinueKind.RESUME_SLIDESHOW -> app.scope.launch {
            val favs = app.repo.favorites()
            app.slideshow(SlideshowRequest(favs, SlideMode.NORMAL, start = 12.coerceAtMost(favs.lastIndex.coerceAtLeast(0)), keepOrder = true, sourceLabel = "Favorites"))
        }
        ContinueKind.ALBUM -> it.albumId?.let(app::openAlbum)
        ContinueKind.TIMELINE -> it.timelineMonth?.let { m -> app.goTop(TimelineRoute(m.year, m.month)) }
    }
}

private fun playAlbum(app: AppController, al: Album) = app.scope.launch {
    app.slideshow(SlideshowRequest(app.repo.albumAssets(al.id), SlideMode.NORMAL, sourceLabel = "Album: ${al.title}"))
}

private fun playPerson(app: AppController, p: Person) = app.scope.launch {
    app.slideshow(SlideshowRequest(app.repo.personAssets(p.id), SlideMode.NORMAL, sourceLabel = "Person: ${p.name}"))
}

@Composable
private fun HomeEmpty(vm: HomeViewModel) {
    val app = LocalApp.current
    Box(Modifier.fillMaxSize().padding(start = Dims.CONTENT_LEFT.dp, end = Dims.SAFE_X.dp, top = 54.dp, bottom = 54.dp), contentAlignment = Alignment.Center) {
        EmptyState(
            Ic.album, "Your library is empty",
            "Photos and videos you back up with the Immich app on your phone, or upload on the web, will show up here.",
        ) {
            PillButton("Switch user", { app.goTop(ProfilesRoute) }, Modifier.remembered(vm.focus, "switch"), primary = true)
            PillButton("Refresh", { vm.refresh(); app.toast("Library refreshed · still empty", Ic.refresh) }, icon = Ic.refresh)
        }
    }
    ScreenFocus(vm.focus, true, "switch")
}

/** St-Loading: the Home layout drawn as skeletons while the first response is in flight. */
@Composable
private fun HomeSkeleton() {
    Column(Modifier.offset(x = Dims.CONTENT_LEFT.dp, y = HERO_TOP.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            SkeletonBox(Modifier.size(1040.dp, 460.dp).clipRounded(20.dp))
            SkeletonBox(Modifier.size(260.dp, 460.dp).clipRounded(20.dp))
            SkeletonBox(Modifier.size(260.dp, 460.dp).clipRounded(20.dp))
        }
        Column(Modifier.padding(top = ROW_GAP.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SkeletonBox(Modifier.size(220.dp, 34.dp).clipRounded(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                repeat(4) { SkeletonBox(Modifier.size(440.dp, 248.dp).clipRounded(18.dp)) }
            }
        }
    }
}

private fun Modifier.clipRounded(r: Dp) = clip(RoundedCornerShape(r))
