package com.android.glume.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import com.android.glume.domain.Album
import com.android.glume.domain.AssetKind
import com.android.glume.ui.components.AssetImage
import com.android.glume.ui.components.GIcon
import com.android.glume.ui.components.Ic
import com.android.glume.ui.components.Label
import com.android.glume.ui.components.T
import com.android.glume.ui.components.UrlImage
import com.android.glume.ui.components.focusRing
import com.android.glume.ui.theme.FocusScale
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeMotion
import com.android.glume.ui.theme.GlumeType
import com.android.glume.util.Format
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider

/** A row drawn as focused without owning real focus (modals drive their own selection). */
@Composable
fun SelectableRow(selected: Boolean, modifier: Modifier = Modifier, minHeight: Dp = 84.dp, content: @Composable () -> Unit) {
    val c = Glume.colors
    val scale by animateFloatAsState(if (selected) FocusScale.ROW else 1f, tween(Glume.motion.focusMs, easing = GlumeMotion.Ease), label = "row")
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(minHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (selected) Modifier.focusRing(shape, c) else Modifier)
            .background(if (selected) c.focusFill else Color.Transparent, shape)
            .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (selected) c.focusInk else c.text) { content() }
    }
}

private fun KeyEvent.isDown() = type == KeyEventType.KeyDown
/** OK presses; auto-repeats of the hold that opened a modal are ignored. */
private fun KeyEvent.isOk() = (key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter) &&
    nativeKeyEvent.repeatCount == 0 && !nativeKeyEvent.isLongPress

/** Side panel opened by long-press OK (or ⓘ/M). Mirrors the design's St-ContextMenu board. */
class ContextMenu(private val app: AppController, private val target: MenuTarget) : Modal {

    private data class Opt(val id: String, val label: String, val icon: String, val album: Album? = null)

    private var level by mutableStateOf("main")
    private var index by mutableIntStateOf(0)
    private var albums by mutableStateOf<List<Album>>(emptyList())

    init {
        app.scope.launch { albums = app.repo.albums().filter { it.count > 0 }.take(6) }
    }

    private fun options(): List<Opt> {
        if (level == "album") return albums.map { Opt("add", it.title, Ic.album, it) } + Opt("new", "New album…", Ic.plus)
        return when (target) {
            is MenuTarget.Photo -> {
                val fav = target.asset.id in app.repo.favoriteIds.value
                listOf(
                    Opt("fav", if (fav) "Remove from Favorites" else "Add to Favorites", Ic.heart),
                    Opt("album", "Add to album…", Ic.album),
                    Opt("slide", "Play slideshow from here", Ic.play),
                    Opt("info", "Show details", Ic.info),
                )
            }
            is MenuTarget.AlbumT -> listOf(Opt("aplay", "Play slideshow", Ic.play), Opt("aopen", "Open album", Ic.album))
            is MenuTarget.PersonT -> listOf(Opt("pplay", "Play slideshow of ${target.person.name}", Ic.play), Opt("popen", "Show photos", Ic.people))
            is MenuTarget.MemoryT -> listOf(Opt("mplay", "Play memory", Ic.play), Opt("mhide", "Hide this memory", Ic.eyeOff))
        }
    }

    private fun close() { app.modal = null }

    override fun onKey(event: KeyEvent): Boolean {
        if (!event.isDown()) return true
        val n = options().size
        when {
            event.key == Key.DirectionUp -> index = (index - 1).coerceAtLeast(0)
            event.key == Key.DirectionDown -> index = (index + 1).coerceAtMost(n - 1)
            event.key == Key.DirectionLeft && level == "album" -> { level = "main"; index = 1 }
            event.isOk() -> select()
            event.key == Key.Escape -> onBack()
        }
        return true
    }

    override fun onBack() {
        if (level == "album") { level = "main"; index = 1 } else close()
    }

    private fun select() {
        val o = options().getOrNull(index) ?: return
        val repo = app.repo
        when (o.id) {
            "fav" -> {
                val t = target as MenuTarget.Photo
                val was = t.asset.id in repo.favoriteIds.value
                app.scope.launch { repo.setFavorite(t.asset.id, !was) }
                close(); app.toast(if (was) "Removed from Favorites" else "Added to Favorites", Ic.heart)
            }
            "album" -> { level = "album"; index = 0 }
            "add" -> {
                val t = target as MenuTarget.Photo
                app.scope.launch { repo.addToAlbum(t.asset.id, o.album!!.id) }
                close(); app.toast("Added to “${o.album!!.title}”")
            }
            "new" -> { close(); app.toast("New album created · name it in the Immich app", Ic.plus) }
            "slide" -> { val t = target as MenuTarget.Photo; close(); app.slideshowFrom(t.list, t.index) }
            "info" -> {
                val t = target as MenuTarget.Photo
                close()
                if (t.inViewer) app.viewerShowInfo = true else app.openViewer(t.list, t.index)
            }
            "aplay" -> {
                val a = (target as MenuTarget.AlbumT).album
                close()
                app.scope.launch { app.slideshow(com.android.glume.ui.nav.SlideshowRequest(repo.albumAssets(a.id), com.android.glume.ui.nav.SlideMode.NORMAL, sourceLabel = "Album: ${a.title}")) }
            }
            "aopen" -> { close(); app.openAlbum((target as MenuTarget.AlbumT).album.id) }
            "pplay" -> {
                val p = (target as MenuTarget.PersonT).person
                close()
                app.scope.launch { app.slideshow(com.android.glume.ui.nav.SlideshowRequest(repo.personAssets(p.id), com.android.glume.ui.nav.SlideMode.NORMAL, sourceLabel = "Person: ${p.name}")) }
            }
            "popen" -> { close(); app.openPerson((target as MenuTarget.PersonT).person.id) }
            "mplay" -> { close(); app.playMemory((target as MenuTarget.MemoryT).memory) }
            "mhide" -> { close(); app.toast("Memory hidden", Ic.eyeOff) }
        }
    }

    @Composable
    override fun Content() {
        val c = Glume.colors
        Box(Modifier.fillMaxSize().background(Color(0x8C050609))) {
            AnimatedVisibility(
                visible = true,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(tween(Glume.motion.panelMs)) { it / 12 } + fadeIn(tween(Glume.motion.panelMs)),
            ) {
                Column(
                    Modifier.width(680.dp).fillMaxHeight().background(Color(0xFF151923))
                        .padding(start = 52.dp, end = 96.dp, top = 54.dp, bottom = 54.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    val (kicker, title, sub) = header()
                    if (level != "album") {
                        Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(16.dp)).background(c.surface2)) {
                            when (target) {
                                is MenuTarget.Photo -> AssetImage(target.asset.previewUrl, target.asset.thumbhash, Modifier.fillMaxSize())
                                is MenuTarget.AlbumT -> target.album.cover?.let { AssetImage(it.thumbUrl, it.thumbhash, Modifier.fillMaxSize()) }
                                is MenuTarget.PersonT -> UrlImage(target.person.avatarUrl, Modifier.fillMaxSize())
                                is MenuTarget.MemoryT -> target.memory.assets.firstOrNull()?.let { AssetImage(it.thumbUrl, it.thumbhash, Modifier.fillMaxSize()) }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Label(if (level == "album") "Add to album" else kicker)
                        T(title, GlumeType.title)
                        T(sub, GlumeType.sec, color = c.text2)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options().forEachIndexed { i, o ->
                            SelectableRow(selected = i == index) {
                                GIcon(o.icon, 30.dp, strokeWidth = 2.2f)
                                T(o.label, GlumeType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(650)))
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    T(
                        if (level == "album") "▲ ▼ choose · OK add · Back to options" else "▲ ▼ choose · OK select · Back to close",
                        GlumeType.sec, color = c.text2,
                    )
                }
            }
        }
    }

    private fun header(): Triple<String, String, String> = when (target) {
        is MenuTarget.Photo -> Triple(if (target.asset.kind == AssetKind.VIDEO) "Video" else "Photo", Format.dateLong(target.asset.date), "${target.asset.place}, ${target.asset.country}")
        is MenuTarget.AlbumT -> Triple("Album", target.album.title, "${target.album.count} photos")
        is MenuTarget.PersonT -> Triple("Person", target.person.name ?: "Unnamed", "${target.person.count} photos")
        is MenuTarget.MemoryT -> Triple("Memory", target.memory.agoLabel, target.memory.place)
    }
}

/** The 7-column on-screen keyboard layout shared by the connect dialog and Search. */
object Kb {
    val ROWS = listOf(
        listOf("a", "b", "c", "d", "e", "f", "g"),
        listOf("h", "i", "j", "k", "l", "m", "n"),
        listOf("o", "p", "q", "r", "s", "t", "u"),
        listOf("v", "w", "x", "y", "z", ".", "-"),
        listOf("1", "2", "3", "4", "5", "6", "7"),
        listOf("8", "9", "0", "@", ":", "/", "_"),
    )

    /** Key at row/col; row 6 is Space (cols 0-2), Delete (3-4), Done (5-6). */
    fun keyAt(r: Int, c: Int): String = if (r < 6) ROWS[r][c] else if (c <= 2) "space" else if (c <= 4) "del" else "done"

    /** Moves within the grid; returns null at an edge. */
    fun step(r: Int, c: Int, key: Key): Pair<Int, Int>? = when (key) {
        Key.DirectionLeft -> if (r == 6) (if (c >= 5) 6 to 3 else if (c >= 3) 6 to 0 else null) else if (c == 0) null else r to c - 1
        Key.DirectionRight -> if (r == 6) (if (c <= 2) 6 to 3 else if (c <= 4) 6 to 5 else null) else if (c == 6) null else r to c + 1
        Key.DirectionUp -> if (r == 0) null else r - 1 to c
        Key.DirectionDown -> if (r == 6) null else r + 1 to c
        else -> null
    }

    fun apply(value: String, key: String): String = when (key) {
        "del" -> value.dropLast(1)
        "space" -> "$value "
        "done" -> value
        else -> value + key
    }
}

/** Modal keyboard for the connect form (server URL, email, password). */
class KeyboardDialog(
    private val app: AppController,
    private val label: String,
    initial: String,
    private val secret: Boolean,
    private val onDone: (String) -> Unit,
) : Modal {
    private var value by mutableStateOf(initial)
    private var r by mutableIntStateOf(0)
    private var c by mutableIntStateOf(0)

    override fun onKey(event: KeyEvent): Boolean {
        if (!event.isDown()) return true
        when {
            event.key == Key.Escape -> onBack()
            event.isOk() -> press(Kb.keyAt(r, c))
            event.key == Key.Backspace -> value = value.dropLast(1)
            Kb.step(r, c, event.key) != null -> Kb.step(r, c, event.key)!!.let { (nr, nc) -> r = nr; c = nc }
            event.utf16CodePoint in 32..126 -> value += event.utf16CodePoint.toChar().lowercaseChar()
        }
        return true
    }

    override fun onBack() { app.modal = null }

    private fun press(key: String) {
        if (key == "done") { app.modal = null; onDone(value); return }
        value = Kb.apply(value, key)
    }

    @Composable
    override fun Content() {
        val col = Glume.colors
        Box(Modifier.fillMaxSize().background(Color(0xCC050609)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(704.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF151923)).padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                Label(label, col.text3)
                Box(
                    Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(18.dp)).background(col.surface2)
                        .border(2.dp, col.accent, RoundedCornerShape(18.dp)).padding(horizontal = 28.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(if (secret) "•".repeat(value.length) else value, GlumeType.body)
                        Caret()
                    }
                }
                KeyGrid(r, c, doneLabel = "Done")
                T("Done confirms · Back cancels · a USB keyboard works too", GlumeType.sec, color = col.text2)
            }
        }
    }
}

/** Keyboard drawn from a selection index (for modals). */
@Composable
fun KeyGrid(selR: Int, selC: Int, doneLabel: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Kb.ROWS.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { c, k -> FakeKey(k, 76.dp, r == selR && c == selC) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val cur = if (selR == 6) Kb.keyAt(6, selC) else ""
            FakeKey("Space", 248.dp, cur == "space")
            FakeKey("⌫", 162.dp, cur == "del")
            FakeKey(doneLabel, 162.dp, cur == "done")
        }
    }
}

@Composable
private fun FakeKey(label: String, width: Dp, selected: Boolean) {
    val c = Glume.colors
    val scale by animateFloatAsState(if (selected) FocusScale.KEY else 1f, tween(Glume.motion.focusMs, easing = GlumeMotion.Ease), label = "key")
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(width, 68.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (selected) Modifier.focusRing(shape, c) else Modifier)
            .background(if (selected) c.focusFill else Color.White.copy(alpha = 0.07f), shape),
        contentAlignment = Alignment.Center,
    ) {
        T(label, GlumeType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(650)), color = if (selected) c.focusInk else c.text)
    }
}

/** Blinking text caret. */
@Composable
fun Caret() {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(500); on = !on } }
    Box(Modifier.padding(start = 2.dp).width(3.dp).height(34.dp).background(if (on) Glume.colors.accent else Color.Transparent))
}

@Composable
fun BoxScope.ToastHost(toast: ToastMsg?) {
    AnimatedVisibility(
        visible = toast != null,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp),
        enter = fadeIn(tween(260)) + slideInVertically(tween(260, easing = GlumeMotion.Ease)) { it / 3 },
        exit = fadeOut(tween(200)),
    ) {
        var last by remember { mutableStateOf(toast) }
        if (toast != null) last = toast
        val t = last ?: return@AnimatedVisibility
        Row(
            Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFF3F5F9)).padding(horizontal = 34.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GIcon(t.icon, 28.dp, strokeWidth = 2.4f, tint = Color(0xFF0A0C11))
            T(t.text, GlumeType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Color(0xFF0A0C11))
        }
    }
}

@Composable
fun BoxScope.OfflineBanner() {
    Row(
        Modifier.align(Alignment.TopCenter).padding(top = 54.dp)
            .clip(RoundedCornerShape(999.dp)).background(Color(0xFF3A1F1C))
            .border(2.dp, Color(0x80FF8A7A), RoundedCornerShape(999.dp))
            .padding(horizontal = 26.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GIcon(Ic.cloudOff, 26.dp, strokeWidth = 2.2f, tint = Color(0xFFFFD9D3))
        T("Server unreachable · showing cached photos · retrying", GlumeType.badge, color = Color(0xFFFFD9D3))
    }
}

/** A circle with a dot that pulses (connect "Waiting for your phone…"). */
@Composable
fun PulseDot(color: Color, pulse: Boolean) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(1f, 0.35f, androidx.compose.animation.core.infiniteRepeatable(tween(800), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    Box(Modifier.size(12.dp).graphicsLayer { alpha = if (pulse) a else 1f; scaleX = if (pulse) 0.8f + 0.2f * a else 1f; scaleY = scaleX }.clip(CircleShape).background(color))
}
