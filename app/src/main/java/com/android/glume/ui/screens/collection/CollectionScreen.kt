@file:OptIn(ExperimentalFoundationApi::class)

package com.android.glume.ui.screens.collection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.glume.data.ImmichRepository
import com.android.glume.domain.Asset
import com.android.glume.domain.Person
import com.android.glume.ui.app.LocalApp
import com.android.glume.ui.app.MenuTarget
import com.android.glume.ui.app.ScreenFocus
import com.android.glume.ui.components.AssetImage
import com.android.glume.ui.components.Avatar
import com.android.glume.ui.components.GIcon
import com.android.glume.ui.components.Ic
import com.android.glume.ui.components.Label
import com.android.glume.ui.components.PhotoTile
import com.android.glume.ui.components.PillButton
import com.android.glume.ui.components.SkeletonBox
import com.android.glume.ui.components.T
import com.android.glume.ui.components.UrlImage
import com.android.glume.ui.components.onPlayPause
import com.android.glume.ui.components.rememberPivot
import com.android.glume.ui.components.remembered
import com.android.glume.ui.nav.PlacesRoute
import com.android.glume.ui.nav.SlideMode
import com.android.glume.ui.nav.SlideshowRequest
import com.android.glume.ui.screens.LoadViewModel
import com.android.glume.ui.screens.dataOrNull
import com.android.glume.ui.theme.Dims
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeType
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

sealed interface CollectionSource {
    data object Favorites : CollectionSource
    data class Album(val id: String) : CollectionSource
    data class Person(val id: String) : CollectionSource
    data class Place(val title: String, val names: List<String>, val country: String) : CollectionSource
}

data class Collection(
    val label: String,
    val title: String,
    val meta: String,
    val description: String,
    val cover: Asset?,
    val avatarUrl: String?,
    val contributors: List<Person>,
    val assets: List<Asset>,
    val isAlbum: Boolean,
    val sourceLabel: String,
)

class CollectionViewModel(val source: CollectionSource, private val repo: ImmichRepository) : LoadViewModel<Collection>() {
    init { refresh() }

    override suspend fun load(): Collection = when (source) {
        is CollectionSource.Album -> {
            val a = repo.album(source.id) ?: error("Album not found")
            val items = repo.albumAssets(source.id)
            Collection(
                "Album", a.title,
                listOf("${a.count} photos", a.dateLabel, if (a.shared) "Shared by ${a.ownerName}" else "Only you").joinToString(" · "),
                a.description, a.cover, null, a.contributors, items, isAlbum = true, sourceLabel = "Album: ${a.title}",
            )
        }
        is CollectionSource.Person -> {
            val p = repo.person(source.id) ?: error("Person not found")
            val items = repo.personAssets(source.id)
            val years = if (items.isNotEmpty()) " · ${items.last().date.year}–${items.first().date.year}" else ""
            Collection("Person", p.name ?: "Unnamed", "${items.size} photos$years", "", null, p.avatarUrl, emptyList(), items.take(60), false, "Person: ${p.name}")
        }
        is CollectionSource.Place -> {
            val items = repo.placeAssets(source.names)
            Collection("Place", source.title, (if (source.country.isNotEmpty()) "${source.country} · " else "") + "${items.size} photos", "", items.firstOrNull(), null, emptyList(), items, false, source.title)
        }
        CollectionSource.Favorites -> {
            val items = repo.favorites()
            Collection("", "Favorites", "${items.size} photos & videos · hold OK on any photo to change", "", null, null, emptyList(), items, false, "Favorites")
        }
    }
}

/** Album / person / place detail and the Favorites section (design boards 05b, 06b, St-EmptyAlbum, St-LongNames). */
@Composable
fun CollectionScreen(source: CollectionSource) {
    val vm: CollectionViewModel = koinViewModel(key = source.toString()) { parametersOf(source) }
    val app = LocalApp.current
    val state by vm.state.collectAsStateWithLifecycle()
    val favs by app.repo.favoriteIds.collectAsStateWithLifecycle()
    val data = state.dataOrNull
    val isFav = source == CollectionSource.Favorites
    val left = if (isFav) Dims.CONTENT_LEFT else 120
    val width = 1920 - left - Dims.SAFE_X
    val tileW = (width - 5 * 24) / 6f
    val tileH = tileW * 2f / 3f
    val gridState = rememberLazyGridState()

    // Favorites list follows favorite changes live.
    val assets = if (isFav && data != null) data.assets.filter { it.id in favs } else data?.assets.orEmpty()

    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(380.dp, band = 140.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = (left).dp, end = Dims.SAFE_X.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                if (data == null) HeaderSkeleton() else Header(vm, data, assets, isFav)
            }
            if (data != null && data.isAlbum && data.assets.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyAlbum() }
            }
            if (data == null) {
                items(12) { SkeletonBox(Modifier.fillMaxWidth().height(tileH.dp).clip(RoundedCornerShape(14.dp))) }
            }
            itemsIndexed(assets, key = { _, a -> a.id }) { i, a ->
                PhotoTile(
                    a, favorite = a.id in favs,
                    onClick = { app.openViewer(assets, i) },
                    onLongClick = { app.showMenu(MenuTarget.Photo(a, assets, i)) },
                    onFocused = { app.ambient = a.thumbhash },
                    inset = 10.dp,
                    modifier = Modifier.fillMaxWidth().height(tileH.dp).remembered(vm.focus, a.id).onPlayPause { app.slideshowFrom(assets, i) },
                )
            }
        }
    }
    ScreenFocus(vm.focus, data != null, "act:0")
}

@Composable
private fun Header(vm: CollectionViewModel, d: Collection, assets: List<Asset>, isFav: Boolean) {
    val app = LocalApp.current
    val c = Glume.colors
    val headH = when { isFav -> 300; d.avatarUrl != null -> 420; else -> 520 }
    val padT = if (isFav) 54 else 96
    Row(
        Modifier.fillMaxWidth().height(headH.dp).padding(top = padT.dp, bottom = 24.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(56.dp),
    ) {
        when {
            d.avatarUrl != null -> UrlImage(d.avatarUrl, Modifier.size(280.dp).clip(CircleShape))
            d.cover != null -> Box(Modifier.size(600.dp, 400.dp).clip(RoundedCornerShape(20.dp)).background(c.surface2)) {
                AssetImage(d.cover.previewUrl, d.cover.thumbhash, Modifier.fillMaxSize())
            }
            d.isAlbum -> Box(
                Modifier.size(600.dp, 400.dp).clip(RoundedCornerShape(20.dp)).background(c.surface2).border(2.dp, c.line, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) { GIcon(Ic.album, 96.dp, strokeWidth = 1.4f, tint = c.text3) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (d.label.isNotEmpty()) Label(d.label)
            T(d.title, GlumeType.h1, maxLines = 2, modifier = Modifier.widthIn(max = 1050.dp))
            T(d.meta, GlumeType.sec, color = c.text2)
            if (d.description.isNotEmpty()) T(d.description, GlumeType.body, color = c.text2, maxLines = 2, modifier = Modifier.widthIn(max = 1050.dp))
            if (d.contributors.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    d.contributors.forEachIndexed { i, p ->
                        Avatar(p.avatarUrl, 52.dp, Modifier.offset(x = (-14 * i).dp).border(3.dp, c.bg, CircleShape))
                    }
                    val names = d.contributors.mapNotNull { it.name }
                    val text = if (names.size > 1) names.dropLast(1).joinToString(", ") + " and ${names.last()} contributed" else "${names.first()} contributed"
                    T(text, GlumeType.sec, color = c.text2, modifier = Modifier.offset(x = (22 - 14 * (d.contributors.size - 1)).dp))
                }
            }
            val empty = assets.isEmpty()
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                val play = { if (empty) app.toast("This album is empty", Ic.info) else app.slideshow(SlideshowRequest(assets, SlideMode.NORMAL, title = d.title, sourceLabel = d.sourceLabel)) }
                PillButton(
                    if (d.avatarUrl != null) "Play slideshow of ${d.title}" else "Play slideshow", play,
                    Modifier.remembered(vm.focus, "act:0"), icon = Ic.play, primary = true, disabled = empty,
                )
                when {
                    isFav -> PillButton("Party mode", { app.slideshow(SlideshowRequest(assets, SlideMode.PARTY)) }, Modifier.remembered(vm.focus, "act:1"), icon = Ic.sparkle)
                    vm.source is CollectionSource.Place -> PillButton("Show on map", {
                        app.goTop(PlacesRoute(map = true, pin = (vm.source as CollectionSource.Place).names.first()))
                    }, Modifier.remembered(vm.focus, "act:1"), icon = Ic.map)
                    else -> PillButton("Shuffle", {
                        if (empty) app.toast("This album is empty", Ic.info) else app.slideshow(SlideshowRequest(assets.shuffled(), SlideMode.NORMAL, keepOrder = true, sourceLabel = d.sourceLabel))
                    }, Modifier.remembered(vm.focus, "act:1"), icon = Ic.shuffle, disabled = empty)
                }
                if (isFav) PillButton("Shuffle", { app.slideshow(SlideshowRequest(assets.shuffled(), SlideMode.NORMAL, keepOrder = true)) }, Modifier.remembered(vm.focus, "act:2"), icon = Ic.shuffle)
                if (d.isAlbum) PillButton("Favorite", { app.toast("Album added to Favorites", Ic.heart) }, Modifier.remembered(vm.focus, "act:2"), icon = Ic.heart)
            }
        }
    }
}

@Composable
private fun EmptyAlbum() {
    val c = Glume.colors
    Row(
        Modifier.padding(top = 32.dp).fillMaxWidth().border(2.dp, c.line, RoundedCornerShape(24.dp)).padding(64.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(44.dp),
    ) {
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(32.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
            GIcon(Ic.plus, 60.dp, strokeWidth = 1.6f, tint = c.text3)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("This album is empty", GlumeType.h2)
            T("Add photos from the Immich app on your phone or on the web. They'll appear here automatically.", GlumeType.body, color = c.text2, maxLines = 2)
        }
    }
}

@Composable
private fun HeaderSkeleton() {
    Row(Modifier.fillMaxWidth().height(520.dp).padding(top = 96.dp, bottom = 24.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
        SkeletonBox(Modifier.size(600.dp, 400.dp).clip(RoundedCornerShape(20.dp)))
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SkeletonBox(Modifier.size(500.dp, 60.dp).clip(RoundedCornerShape(10.dp)))
            SkeletonBox(Modifier.size(360.dp, 30.dp).clip(RoundedCornerShape(8.dp)))
        }
    }
}
