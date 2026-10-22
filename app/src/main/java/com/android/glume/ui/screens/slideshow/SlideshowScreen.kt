package com.android.glume.ui.screens.slideshow

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.android.glume.data.Prefs
import com.android.glume.data.SettingKey
import com.android.glume.domain.Asset
import com.android.glume.ui.app.InterceptBack
import com.android.glume.ui.app.LocalApp
import com.android.glume.ui.app.SelectableRow
import com.android.glume.ui.components.GIcon
import com.android.glume.ui.components.Ic
import com.android.glume.ui.components.KenBurnsImage
import com.android.glume.ui.components.Label
import com.android.glume.ui.components.T
import com.android.glume.ui.components.Toggle
import com.android.glume.ui.nav.MediaQueue
import com.android.glume.ui.nav.SlideMode
import com.android.glume.ui.nav.SlideshowRoute
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeType
import com.android.glume.ui.theme.Manrope
import com.android.glume.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PANEL_ROWS = listOf(
    SettingKey.SlideSource to "Show photos from",
    SettingKey.Shuffle to "Shuffle",
    SettingKey.Interval to "Interval",
    SettingKey.Transition to "Transition",
    SettingKey.KenBurns to "Ken Burns effect",
    SettingKey.Captions to "Captions",
    SettingKey.IncludeVideos to "Include videos",
)

private fun prepare(list: List<Asset>, mode: SlideMode, keepOrder: Boolean, p: Prefs): List<Asset> {
    var l = list
    if (!p.includeVideos && mode != SlideMode.MEMORY) l = l.filter { !it.isVideo }
    if (mode == SlideMode.PARTY || (p.shuffle && !keepOrder && mode != SlideMode.MEMORY)) l = l.shuffled()
    return l.take(40)
}

private val shadow = Shadow(Color(0x99000000), blurRadius = 16f)

/**
 * Slideshow, memories, party mode and the ambient screensaver (design boards 10, 10b, St-Party,
 * St-Screensaver). Ken Burns and crossfades are graphicsLayer-only; one slide ahead is prefetched.
 */
@Composable
fun SlideshowScreen(route: SlideshowRoute) {
    val app = LocalApp.current
    val req = remember(route.token) { MediaQueue.slideshow(route.token) }
    if (req == null) { LaunchedEffect(Unit) { app.nav.popBackStack() }; return }
    val prefs by app.settings.prefs.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val motion = Glume.motion
    var list by remember { mutableStateOf(prepare(req.assets, req.mode, req.keepOrder, prefs)) }
    var idx by remember { mutableIntStateOf(req.start.coerceIn(0, (list.size - 1).coerceAtLeast(0))) }
    var prev by remember { mutableIntStateOf(-1) }
    var paused by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(false) }
    var pr by remember { mutableIntStateOf(0) }
    var slideAt by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val startedAt = remember { SystemClock.uptimeMillis() }
    val focus = remember { FocusRequester() }
    val ambient = req.mode == SlideMode.AMBIENT
    if (list.isEmpty()) { LaunchedEffect(Unit) { app.toast("Nothing to show here yet", Ic.info); app.nav.popBackStack() }; return }

    fun step(d: Int) {
        if (list.size < 2) return
        prev = idx; idx = (idx + d + list.size) % list.size; slideAt = SystemClock.uptimeMillis()
    }

    LaunchedEffect(idx, paused, panel, prefs.intervalSec) {
        if (paused || panel) return@LaunchedEffect
        delay(prefs.intervalSec * 1000L)
        step(1)
    }
    LaunchedEffect(idx) {
        list.getOrNull((idx + 1) % list.size)?.let { SingletonImageLoader.get(ctx).enqueue(ImageRequest.Builder(ctx).data(it.previewUrl).build()) }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun change(key: SettingKey, dir: Int) {
        val v = key.next(prefs, dir)
        app.scope.launch {
            app.settings.set(key, v)
            if (key == SettingKey.SlideSource) {
                val src = app.assetsForSource(v as String)
                val p = app.settings.prefs.value
                list = prepare(src, req.mode, false, p).ifEmpty { list }
                idx = 0; prev = -1
            }
        }
    }

    fun onKey(e: KeyEvent): Boolean {
        if (ambient) {
            if (e.type == KeyEventType.KeyDown) app.nav.popBackStack()
            return true
        }
        if (e.type != KeyEventType.KeyDown) return e.key in listOf(Key.DirectionCenter, Key.Enter)
        if (panel) {
            when (e.key) {
                Key.DirectionUp -> pr = (pr - 1).coerceAtLeast(0)
                Key.DirectionDown -> pr = (pr + 1).coerceAtMost(PANEL_ROWS.lastIndex)
                Key.DirectionLeft -> change(PANEL_ROWS[pr].first, -1)
                Key.DirectionRight, Key.DirectionCenter, Key.Enter -> change(PANEL_ROWS[pr].first, 1)
                else -> return false
            }
            return true
        }
        when (e.key) {
            Key.DirectionLeft -> step(-1)
            Key.DirectionRight -> step(1)
            Key.DirectionCenter, Key.Enter -> { panel = true; pr = 0 }
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.Spacebar -> { paused = !paused; slideAt = SystemClock.uptimeMillis() }
            else -> return false
        }
        return true
    }

    InterceptBack(panel) { panel = false; slideAt = SystemClock.uptimeMillis() }

    val xf = when (prefs.transition) { "None" -> 0; "Fade to black" -> 700; else -> motion.crossfadeMs }
    val kenBurns = (prefs.kenBurns || ambient) && motion.kenBurns
    val asset = list[idx.coerceIn(0, list.lastIndex)]
    val white = Color.White
    val soft = Color(0xFFE2E6EE)

    Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).onKeyEvent(::onKey).focusable()) {
        list.forEachIndexed { j, a ->
            if (j == idx || j == prev) {
                // "Fade to black" hides the outgoing slide immediately so the incoming one rises from black.
                val visible = j == idx
                KenBurnsImage(
                    a, visible = visible, variant = j % 3, durationMs = (prefs.intervalSec + 3) * 1000,
                    crossfadeMs = if (prefs.transition == "Fade to black" && !visible) 0 else xf, enabled = kenBurns,
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x59000000), 0.22f to Color.Transparent, 0.65f to Color.Transparent, 1f to Color(0x99000000))))

        val clock by produceState(Format.clock()) { while (true) { delay(5_000); value = Format.clock() } }
        when (req.mode) {
            SlideMode.NORMAL -> {
                if (prefs.captions != "Off") {
                    Column(Modifier.align(Alignment.BottomStart).padding(start = 96.dp, bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        T(asset.place, GlumeType.h2.copy(shadow = shadow), color = white)
                        T(Format.dateLong(asset.date), GlumeType.body.copy(shadow = shadow), color = soft)
                    }
                }
                if (prefs.captions == "Clock & place") {
                    Column(Modifier.align(Alignment.TopEnd).padding(top = 54.dp, end = 96.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T(clock, GlumeType.h2.copy(shadow = shadow), color = white, tabular = true)
                        T(Format.today(), GlumeType.sec.copy(shadow = shadow), color = soft)
                    }
                }
            }
            SlideMode.MEMORY -> {
                val m = req.memory
                Row(Modifier.align(Alignment.TopStart).padding(start = 96.dp, end = 96.dp, top = 54.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    list.indices.forEach { j -> Segment(j, idx, paused || panel, slideAt, prefs.intervalSec, Modifier.weight(1f)) }
                }
                Column(Modifier.align(Alignment.TopStart).padding(start = 96.dp, top = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label("On this day", Color(0xFFD4D9FF))
                    T(m?.agoLabel.orEmpty(), GlumeType.h1.copy(shadow = shadow), color = white)
                    T(m?.let { "${it.place} · ${it.count} photos" }.orEmpty(), GlumeType.body.copy(shadow = shadow), color = soft)
                }
                T(asset.story.orEmpty(), GlumeType.h2.copy(shadow = shadow), Modifier.align(Alignment.BottomStart).padding(start = 96.dp, bottom = 64.dp).widthIn(max = 1100.dp), color = white, maxLines = 2)
            }
            SlideMode.PARTY -> {
                Row(
                    Modifier.align(Alignment.TopStart).padding(start = 96.dp, top = 54.dp).clip(RoundedCornerShape(999.dp)).background(Color(0x990A0C11)).padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GIcon(Ic.shuffle, 22.dp, strokeWidth = 2.2f, tint = white)
                    T("Party mode · shuffled favorites", GlumeType.badge, color = white)
                }
                Column(Modifier.align(Alignment.BottomCenter).padding(start = 96.dp, end = 96.dp, bottom = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(asset.place, TextStyle(fontFamily = Manrope, fontSize = 96.sp, fontWeight = FontWeight(800), letterSpacing = (-0.02).em, shadow = Shadow(Color(0xB3000000), blurRadius = 24f)), color = white, align = TextAlign.Center)
                    T(asset.date.year.toString(), TextStyle(fontFamily = Manrope, fontSize = 44.sp, fontWeight = FontWeight(600), shadow = shadow), Modifier.padding(top = 12.dp), color = Color(0xFFE8EBF2))
                }
            }
            SlideMode.AMBIENT -> {
                Column(Modifier.align(Alignment.BottomStart).padding(start = 96.dp, bottom = 70.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(clock, TextStyle(fontFamily = Manrope, fontSize = 168.sp, fontWeight = FontWeight(300), letterSpacing = (-0.03).em, shadow = shadow), color = white, tabular = true)
                    T(Format.today(), GlumeType.h2.copy(fontWeight = FontWeight(500), shadow = shadow), color = white)
                }
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(end = 96.dp, bottom = 76.dp).clip(RoundedCornerShape(999.dp)).background(Color(0x800A0C11)).padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GIcon(Ic.pin, 22.dp, strokeWidth = 2.2f, tint = white)
                    T("${asset.place} · ${asset.date.year}", GlumeType.badge.copy(fontWeight = FontWeight(600)), color = white)
                }
            }
        }

        if (paused && !panel) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(140.dp).clip(CircleShape).background(Color(0x990A0C11)), contentAlignment = Alignment.Center) {
                    GIcon(Ic.pause, 60.dp, filled = true, tint = white)
                }
                T("Paused", GlumeType.body.copy(fontWeight = FontWeight.Bold), color = white)
            }
        }
        val showHint by produceState(!ambient) { delay(4500); value = false }
        AnimatedVisibility(showHint && !panel, Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp), enter = fadeIn(), exit = fadeOut()) {
            T("OK settings · ⏯ pause · ◀ ▶ skip · Back exit", GlumeType.badge.copy(fontWeight = FontWeight(600)),
                Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0x990A0C11)).padding(horizontal = 24.dp, vertical = 12.dp), color = Color(0xFFD5DAE4))
        }
        AnimatedVisibility(
            panel, Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(motion.panelMs)) { it / 18 } + fadeIn(tween(motion.panelMs)), exit = fadeOut(tween(160)),
        ) {
            Panel(list.size, req.mode, prefs, pr)
        }
    }
}

@Composable
private fun Segment(j: Int, idx: Int, paused: Boolean, slideAt: Long, intervalSec: Int, modifier: Modifier) {
    val p = remember(j, idx, slideAt) { Animatable(if (j < idx) 1f else 0f) }
    LaunchedEffect(j, idx, slideAt, paused) {
        if (j == idx && !paused) p.animateTo(1f, tween(((1f - p.value) * intervalSec * 1000).toInt(), easing = LinearEasing))
    }
    Box(modifier.height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.3f))) {
        Box(Modifier.fillMaxWidth(p.value).fillMaxHeight().background(Color.White))
    }
}

@Composable
private fun Panel(count: Int, mode: SlideMode, prefs: Prefs, pr: Int) {
    val c = Glume.colors
    Column(
        Modifier.width(760.dp).fillMaxHeight().background(Color(0xF710131A)).padding(start = 52.dp, end = 96.dp, top = 54.dp, bottom = 54.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        T("Slideshow", GlumeType.h2, Modifier.padding(bottom = 6.dp))
        val src = when (mode) { SlideMode.MEMORY -> "Memory"; SlideMode.PARTY -> "Party mode"; SlideMode.AMBIENT -> "Screensaver"; else -> prefs.slideSource }
        T("Playing $count items · $src", GlumeType.sec, Modifier.padding(bottom = 18.dp), color = c.text2)
        PANEL_ROWS.forEachIndexed { i, (key, label) ->
            val sel = i == pr
            SelectableRow(sel, minHeight = 80.dp) {
                T(label, GlumeType.body.copy(fontWeight = FontWeight(650)), Modifier.weight(1f))
                if (key.isToggle) Toggle(key.read(prefs) as Boolean, sel)
                else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val a = if (sel) 1f else 0.35f
                    T("‹", GlumeType.body, color = androidx.tv.material3.LocalContentColor.current.copy(alpha = a))
                    T(key.display(prefs), GlumeType.body, Modifier.widthIn(max = 330.dp))
                    T("›", GlumeType.body, color = androidx.tv.material3.LocalContentColor.current.copy(alpha = a))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        T("◀ ▶ change · OK next value · Back close", GlumeType.sec, color = c.text2)
    }
}
