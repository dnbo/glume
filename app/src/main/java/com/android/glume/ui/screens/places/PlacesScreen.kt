@file:OptIn(ExperimentalFoundationApi::class)

package com.android.glume.ui.screens.places

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.glume.data.ImmichRepository
import com.android.glume.data.mock.MockImmichRepository
import com.android.glume.domain.MapPin
import com.android.glume.domain.Place
import com.android.glume.ui.app.LocalApp
import com.android.glume.ui.app.ScreenFocus
import com.android.glume.ui.components.AssetImage
import com.android.glume.ui.components.Chip
import com.android.glume.ui.components.FocusSurface
import com.android.glume.ui.components.Ic
import com.android.glume.ui.components.PlaceTile
import com.android.glume.ui.components.SkeletonBox
import com.android.glume.ui.components.T
import com.android.glume.ui.components.UrlImage
import com.android.glume.ui.components.rememberPivot
import com.android.glume.ui.components.remembered
import com.android.glume.ui.nav.PlacesRoute
import com.android.glume.ui.screens.LoadViewModel
import com.android.glume.ui.screens.ScreenTitle
import com.android.glume.ui.screens.dataOrNull
import com.android.glume.ui.theme.Dims
import com.android.glume.ui.theme.FocusScale
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeType
import org.koin.androidx.compose.koinViewModel

data class PlacesData(val places: List<Place>, val pins: List<MapPin>)

class PlacesViewModel(private val repo: ImmichRepository) : LoadViewModel<PlacesData>() {
    init { refresh() }
    override suspend fun load() = PlacesData(repo.places(), repo.mapPins())
}

/** Places (design boards 07 / 07b): grid of places or a map with photo pins. */
@Composable
fun PlacesScreen(route: PlacesRoute, vm: PlacesViewModel = koinViewModel()) {
    val app = LocalApp.current
    val state by vm.state.collectAsStateWithLifecycle()
    val data = state.dataOrNull
    var map by rememberSaveable { mutableStateOf(route.map) }
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.offset(y = Dims.SAFE_Y.dp).fillMaxWidth().padding(end = Dims.SAFE_X.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ScreenTitle("Places", data?.let { "${it.places.size} places" }, Modifier.offset(y = (-Dims.SAFE_Y).dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Chip("Grid", { map = false }, Modifier.remembered(vm.focus, "tog:grid"), icon = Ic.grid, on = !map)
                Chip("Map", { map = true }, Modifier.remembered(vm.focus, "tog:map"), icon = Ic.map, on = map)
            }
        }
        if (data == null) {
            Row(Modifier.padding(start = Dims.CONTENT_LEFT.dp, top = 160.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                repeat(4) { SkeletonBox(Modifier.size(384.dp, 256.dp).clip(RoundedCornerShape(14.dp))) }
            }
        } else if (map) {
            PlacesMap(data.pins, vm, route.pin)
        } else {
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(304.dp)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.padding(top = 140.dp).fillMaxSize(),
                    contentPadding = PaddingValues(start = Dims.CONTENT_LEFT.dp, end = Dims.SAFE_X.dp, top = 20.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    items(data.places, key = { it.name }) { p ->
                        PlaceTile(
                            p, onClick = { app.openPlace(p.name, listOf(p.name), p.country) },
                            onFocused = { app.ambient = p.cover.thumbhash },
                            modifier = Modifier.fillMaxWidth().height(256.dp).remembered(vm.focus, "place:${p.name}"),
                        )
                    }
                }
            }
        }
    }
    ScreenFocus(vm.focus, data != null, if (map) "tog:map" else data?.places?.firstOrNull()?.let { "place:${it.name}" })
}

@Composable
private fun PlacesMap(pins: List<MapPin>, vm: PlacesViewModel, initialPin: String) {
    val app = LocalApp.current
    val c = Glume.colors
    var focused by remember { mutableStateOf(pins.firstOrNull { initialPin in it.placeNames } ?: pins.getOrNull(3)) }
    BoxWithConstraints(
        Modifier.offset(x = Dims.CONTENT_LEFT.dp, y = 150.dp).size(1608.dp, 830.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF10141E)),
    ) {
        UrlImage(MockImmichRepository.MAP_URL, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        pins.forEach { pin ->
            val size = if (pin.placeNames.size > 1) 104.dp else 84.dp
            val isF = focused == pin
            Column(
                Modifier
                    .offset(x = maxWidth * (pin.x / 100f) - size / 2, y = maxHeight * (pin.y / 100f) - size / 2)
                    .zIndex(if (isF) 5f else 1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    FocusSurface(
                        onClick = { app.openPlace(pin.label, pin.placeNames, pin.country) },
                        onFocused = { focused = pin; app.ambient = pin.cover.thumbhash },
                        modifier = Modifier.size(size).remembered(vm.focus, "pin:${pin.label}"),
                        shape = CircleShape, focusScale = FocusScale.KEY,
                        border = BorderStroke(4.dp, Color.White),
                    ) {
                        AssetImage(pin.cover.thumbUrl, pin.cover.thumbhash, Modifier.fillMaxSize())
                    }
                    T(
                        pin.count.toString(), GlumeType.badge.copy(fontWeight = FontWeight(800)),
                        Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-10).dp).clip(RoundedCornerShape(20.dp)).background(c.accentStrong).padding(horizontal = 12.dp, vertical = 8.dp),
                        color = Color.White, tabular = true,
                    )
                }
                if (isF) T(pin.label, GlumeType.badge, Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 8.dp), color = Color(0xFF0A0C11))
            }
        }
        focused?.let { pin ->
            Row(
                Modifier.align(Alignment.BottomStart).padding(32.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xEB10131A)).padding(start = 18.dp, top = 18.dp, bottom = 18.dp, end = 32.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Box(Modifier.size(168.dp, 112.dp).clip(RoundedCornerShape(12.dp))) { AssetImage(pin.cover.thumbUrl, pin.cover.thumbhash, Modifier.fillMaxSize()) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(pin.label, GlumeType.title)
                    T("${pin.count} photos" + if (pin.placeNames.size > 1) " · ${pin.placeNames.size} places" else "", GlumeType.sec, color = c.text2)
                    T("Press OK to open", GlumeType.sec, color = c.accent)
                }
            }
        }
    }
}
