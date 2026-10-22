package com.android.glume.ui.screens.viewer

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.android.glume.domain.Album
import com.android.glume.domain.Asset
import com.android.glume.domain.AssetKind
import com.android.glume.domain.Person
import com.android.glume.ui.app.InterceptBack
import com.android.glume.ui.app.LocalApp
import com.android.glume.ui.app.MenuTarget
import com.android.glume.ui.components.AssetImage
import com.android.glume.ui.components.Avatar
import com.android.glume.ui.components.GIcon
import com.android.glume.ui.components.Ic
import com.android.glume.ui.components.T
import com.android.glume.ui.components.rememberThumbHash
import com.android.glume.ui.nav.MediaQueue
import com.android.glume.ui.nav.ViewerRoute
import com.android.glume.ui.theme.GlumeMotion
import com.android.glume.ui.theme.GlumeType
import com.android.glume.util.Format
import kotlinx.coroutines.delay

private val ZOOMS = listOf(1f, 1.5f, 2f, 3f)

/**
 * Fullscreen viewer (design boards 09 / 09b). Owns the D-pad: ◀ ▶ browse, ▲ ▼ zoom (◀ ▶ pan while
 * zoomed), OK toggles the info overlay, hold OK for options, ⏯ plays videos or starts a slideshow.
 */
@OptIn(UnstableApi::class)
@Composable
fun ViewerScreen(route: ViewerRoute) {
    val app = LocalApp.current
    val list = remember(route.token) { MediaQueue.get(route.token) }
    if (list.isEmpty()) {
        LaunchedEffect(Unit) { app.nav.popBackStack() }
        return
    }
    val ctx = LocalContext.current
    val prefs by app.settings.prefs.collectAsStateWithLifecycle()
    val favs by app.repo.favoriteIds.collectAsStateWithLifecycle()
    var idx by remember { mutableIntStateOf(route.index.coerceIn(0, list.lastIndex)) }
    var overlay by remember { mutableStateOf(true) }
    var shownAt by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var zoomI by remember { mutableIntStateOf(0) }
    var pan by remember { mutableFloatStateOf(0f) }
    val asset = list[idx]
    val isVideo = asset.kind == AssetKind.VIDEO
    val focus = remember { FocusRequester() }
    var okDown by remember { mutableStateOf(false) }
    var longPressed by remember { mutableStateOf(false) }

    // Video player: one instance for the viewer's lifetime, hardware decode by default.
    val player = remember { ExoPlayer.Builder(ctx).build().apply { repeatMode = Player.REPEAT_MODE_OFF } }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(asset.id) {
        player.stop(); player.clearMediaItems()
        positionMs = 0; durationMs = asset.durationSec * 1000L
        asset.videoUrl?.let { player.setMediaItem(MediaItem.fromUri(it)); player.prepare() }
    }
    LaunchedEffect(playing) {
        while (playing) {
            positionMs = player.currentPosition
            if (player.duration > 0) durationMs = player.duration
            delay(250)
        }
    }

    // Overlay auto-hides after 4 s (stays while a video is paused).
    LaunchedEffect(overlay, shownAt, playing) {
        if (!overlay || (isVideo && !playing)) return@LaunchedEffect
        delay(GlumeMotion.OVERLAY_HIDE_MS)
        overlay = false
    }
    LaunchedEffect(app.viewerShowInfo) {
        if (app.viewerShowInfo) { overlay = true; shownAt = SystemClock.uptimeMillis(); app.viewerShowInfo = false }
    }

    // Prefetch previews for the next and previous two assets.
    LaunchedEffect(idx) {
        val loader = SingletonImageLoader.get(ctx)
        for (d in listOf(1, -1, 2, -2)) list.getOrNull(idx + d)?.let { loader.enqueue(ImageRequest.Builder(ctx).data(it.previewUrl).build()) }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun go(newIdx: Int) {
        if (newIdx !in list.indices || newIdx == idx) return
        idx = newIdx; zoomI = 0; pan = 0f; shownAt = SystemClock.uptimeMillis()
    }

    fun ok() { overlay = !overlay; shownAt = SystemClock.uptimeMillis() }

    fun playPause() {
        if (isVideo) {
            if (player.isPlaying) player.pause() else {
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                player.play()
            }
            overlay = true; shownAt = SystemClock.uptimeMillis()
        } else app.slideshowFrom(list, idx)
    }

    fun onKey(e: KeyEvent): Boolean {
        val down = e.type == KeyEventType.KeyDown
        val zoom = ZOOMS[zoomI]
        when (e.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                // Hold = the remote's auto-repeat (or long-press flag) opens the options menu.
                if (down) {
                    val n = e.nativeKeyEvent
                    if (n.repeatCount == 0 && !n.isLongPress) { okDown = true; longPressed = false }
                    else if (!longPressed) { longPressed = true; app.showMenu(MenuTarget.Photo(asset, list, idx, inViewer = true)) }
                } else {
                    if (okDown && !longPressed) ok()
                    okDown = false
                }
                return true
            }
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.Spacebar -> { if (down) playPause(); return true }
            Key.Menu, Key.M, Key.Info -> { if (down) app.showMenu(MenuTarget.Photo(asset, list, idx, inViewer = true)); return true }
        }
        if (!down) return false
        when (e.key) {
            Key.DirectionLeft, Key.DirectionRight -> {
                val right = e.key == Key.DirectionRight
                when {
                    zoom > 1f -> { val lim = (zoom - 1) / zoom * 50f; pan = (pan + if (right) -8f else 8f).coerceIn(-lim, lim) }
                    isVideo && overlay -> {
                        player.seekTo((player.currentPosition + if (right) 10_000 else -10_000).coerceIn(0, durationMs.coerceAtLeast(0)))
                        positionMs = player.currentPosition; shownAt = SystemClock.uptimeMillis()
                    }
                    else -> go(idx + if (right) 1 else -1)
                }
            }
            Key.DirectionUp -> if (!isVideo) { zoomI = (zoomI + 1).coerceAtMost(ZOOMS.lastIndex); overlay = false }
            Key.DirectionDown -> if (zoom > 1f) { zoomI--; if (zoomI == 0) pan = 0f } else { overlay = true; shownAt = SystemClock.uptimeMillis() }
            else -> return false
        }
        return true
    }

    // Back resets zoom first, then leaves the viewer (handled by the app's Back).
    InterceptBack(zoomI > 0) { zoomI = 0; pan = 0f }

    val zoom by animateFloatAsState(ZOOMS[zoomI], tween(320, easing = GlumeMotion.Ease), label = "zoom")
    val panAnim by animateFloatAsState(pan, tween(320, easing = GlumeMotion.Ease), label = "pan")
    val imageUrl = if (prefs.photoQuality == "Original") asset.originalUrl else asset.previewUrl

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus).onKeyEvent(::onKey).focusable(),
    ) {
        // Cheap blurred backdrop: the ThumbHash stretched and darkened.
        val bmp = rememberThumbHash(asset.thumbhash)
        if (bmp != null) {
            Box(Modifier.fillMaxSize().drawWithContent {
                drawImage(bmp, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()), alpha = 0.45f)
            })
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            scaleX = zoom; scaleY = zoom
            translationX = size.width * panAnim / 100f
        }) {
            if (isVideo) {
                ContentFrame(player, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                if (!playing && positionMs == 0L) AssetImage(asset.previewUrl, asset.thumbhash, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                AssetImage(imageUrl, asset.thumbhash, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        if (isVideo && !playing) {
            Box(Modifier.align(Alignment.Center).size(150.dp).clip(CircleShape).background(Color(0x990A0C11)), contentAlignment = Alignment.Center) {
                GIcon(Ic.play, 64.dp, filled = true, tint = Color.White)
            }
        }
        if (zoomI > 0) {
            Pill("${ZOOMS[zoomI].let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }}× · ◀ ▶ pan · Back resets", Modifier.align(Alignment.TopCenter).padding(top = 54.dp))
        }
        AnimatedVisibility(overlay, enter = fadeIn(tween(260)), exit = fadeOut(tween(260))) {
            ViewerOverlay(asset, list, idx, asset.id in favs, isVideo, playing, positionMs, durationMs)
        }
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier) {
    T(text, GlumeType.badge, modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xBF0A0C11)).padding(horizontal = 24.dp, vertical = 12.dp), color = Color.White)
}

@Composable
private fun ViewerOverlay(asset: Asset, list: List<Asset>, idx: Int, fav: Boolean, isVideo: Boolean, playing: Boolean, posMs: Long, durMs: Long) {
    val app = LocalApp.current
    val album by produceState<Album?>(null, asset.id) { value = app.repo.albumOf(asset) }
    val people by produceState(emptyList<Person>(), asset.id) { value = app.repo.peopleIn(asset) }
    val soft = Color(0xFFD5DAE4)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(260.dp).background(Brush.verticalGradient(listOf(Color(0xBF000000), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(480.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000)))))
        Column(Modifier.offset(x = 96.dp, y = 54.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T(Format.dateLong(asset.date), GlumeType.h2, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GIcon(Ic.pin, 26.dp, strokeWidth = 2.2f, tint = soft)
                T("${asset.place}, ${asset.country}", GlumeType.body, color = soft)
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(top = 54.dp, end = 96.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val badge = when (asset.kind) { AssetKind.LIVE -> "LIVE"; AssetKind.RAW -> "RAW"; AssetKind.VIDEO -> "VIDEO"; else -> null }
            badge?.let {
                T(it, GlumeType.badge.copy(fontWeight = FontWeight(800)), Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White)
            }
            if (fav) GIcon(Ic.heart, 30.dp, filled = true, tint = Color.White)
            T("${idx + 1} / ${list.size}", GlumeType.body, color = soft, tabular = true)
        }
        if (isVideo) {
            val pct = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
            Column(Modifier.align(Alignment.BottomStart).padding(start = 96.dp, end = 96.dp, bottom = 330.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    T(Format.duration((posMs / 1000).toInt()), GlumeType.body, Modifier.width(90.dp), color = Color.White, tabular = true)
                    Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.CenterStart) {
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.25f)))
                        Box(Modifier.fillMaxWidth(pct).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF7C8CFF)))
                        Box(Modifier.fillMaxWidth(pct.coerceAtLeast(0.001f)), contentAlignment = Alignment.CenterEnd) {
                            Box(Modifier.offset(x = 14.dp).size(28.dp).clip(CircleShape).background(Color.White))
                        }
                    }
                    T(Format.duration((durMs / 1000).toInt()), GlumeType.body, Modifier.width(90.dp), color = soft, tabular = true)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { GIcon(Ic.back10, 40.dp, tint = soft); T("10 s", GlumeType.badge, color = soft) }
                    Box(Modifier.size(88.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        GIcon(if (playing) Ic.pause else Ic.play, 40.dp, filled = true, tint = Color(0xFF0A0C11))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { T("10 s", GlumeType.badge, color = soft); GIcon(Ic.fwd10, 40.dp, tint = soft) }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(start = 96.dp, end = 96.dp, bottom = 214.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            InfoChip(Ic.camera, asset.cameraLine)
            album?.let { InfoChip(Ic.album, it.title) }
            people.forEach { p ->
                Row(
                    Modifier.height(52.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.12f)).padding(start = 6.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Avatar(p.avatarUrl, 40.dp)
                    T(p.name ?: "", GlumeType.badge.copy(fontWeight = FontWeight(600)), color = Color.White)
                }
            }
        }
        // Filmstrip: up to 5 either side of the current item.
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (j in (idx - 5)..(idx + 5)) {
                val a = list.getOrNull(j) ?: continue
                val cur = j == idx
                Box(
                    Modifier.size(132.dp, 88.dp).graphicsLayer { scaleX = if (cur) 1.12f else 1f; scaleY = scaleX; alpha = if (cur) 1f else 0.55f }
                        .then(if (cur) Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(4.dp) else Modifier)
                        .clip(RoundedCornerShape(10.dp)),
                ) { AssetImage(a.thumbUrl, a.thumbhash, Modifier.fillMaxSize()) }
            }
        }
        val hint = when {
            isVideo -> "◀ ▶ skip 10 s · ⏯ play/pause · OK hide controls · ▼ info"
            asset.kind == AssetKind.LIVE -> "◀ ▶ browse · ▲ zoom · OK hide info · hold OK for options"
            else -> "◀ ▶ browse · ▲ ▼ zoom · OK hide info · ⏯ slideshow from here · hold OK for options"
        }
        T(hint, GlumeType.sec, Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp), color = Color(0xFFAEB5C4))
    }
}

@Composable
private fun InfoChip(icon: String, text: String) {
    Row(
        Modifier.height(52.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GIcon(icon, 22.dp, strokeWidth = 2.2f, tint = Color.White)
        T(text, GlumeType.badge.copy(fontWeight = FontWeight(600)), color = Color.White)
    }
}
