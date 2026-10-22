package com.android.glume.ui.app

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.navigation.NavHostController
import com.android.glume.data.ImmichRepository
import com.android.glume.data.SettingsStore
import com.android.glume.domain.Album
import com.android.glume.domain.Asset
import com.android.glume.domain.Memory
import com.android.glume.domain.Person
import com.android.glume.ui.components.Ic
import com.android.glume.ui.nav.AlbumRoute
import com.android.glume.ui.nav.MediaQueue
import com.android.glume.ui.nav.PersonRoute
import com.android.glume.ui.nav.PlaceRoute
import com.android.glume.ui.nav.SlideMode
import com.android.glume.ui.nav.SlideshowRequest
import com.android.glume.ui.nav.SlideshowRoute
import com.android.glume.ui.nav.ViewerRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A full-screen overlay that owns the D-pad while open (context menu, connect keyboard). */
interface Modal {
    /** Return true if the key was handled. Called for every key while the modal is open. */
    fun onKey(event: KeyEvent): Boolean

    /** System Back (delivered by the back dispatcher, not as a key event on targetSdk 36+). */
    fun onBack()

    @Composable
    fun Content()
}

data class ToastMsg(val text: String, val icon: String, val id: Long)

/** What a long-press OK was on. */
sealed interface MenuTarget {
    data class Photo(val asset: Asset, val list: List<Asset>, val index: Int, val inViewer: Boolean = false) : MenuTarget
    data class AlbumT(val album: Album) : MenuTarget
    data class PersonT(val person: Person) : MenuTarget
    data class MemoryT(val memory: Memory) : MenuTarget
}

/**
 * App-wide actions and overlay state shared by all screens: navigation helpers, toasts, the context
 * menu, the ambient backdrop and the rail focus target. Provided through [LocalApp].
 */
@Stable
class AppController(
    val nav: NavHostController,
    val repo: ImmichRepository,
    val settings: SettingsStore,
    val scope: CoroutineScope,
) {
    var toast by mutableStateOf<ToastMsg?>(null)
        private set
    var modal by mutableStateOf<Modal?>(null)
    /** ThumbHash of the focused item; drawn as the blurred backdrop. */
    var ambient by mutableStateOf<String?>(null)
    var debugOverlay by mutableStateOf(false)
    /** Set when the viewer overlay should open on its info panel (context menu "Show details"). */
    var viewerShowInfo by mutableStateOf(false)

    /** Focus target for the rail item of the current section. */
    val railRequester = FocusRequester()
    var railFocused by mutableStateOf(false)

    /** The screen content container; focusing it restores the content's last focused item. */
    val contentRequester = FocusRequester()

    /** Set before navigating when focus should land on the rail instead of the new screen. */
    var holdRailFocus = false

    var lastInputAt = SystemClock.uptimeMillis()

    /** Screen-level Back interceptors, newest last; each returns true if it handled Back. */
    internal val backInterceptors = mutableListOf<() -> Boolean>()

    /** What Back does when nothing intercepts it; set by the shell (rail logic, pop, exit). */
    internal var rootBack: () -> Unit = {}

    /** Back goes up exactly one level: modal, then screen state (zoom, panel…), then navigation. */
    fun back() {
        modal?.let { it.onBack(); return }
        for (i in backInterceptors.indices.reversed()) if (backInterceptors[i]()) return
        rootBack()
    }

    private var toastSeq = 0L

    fun toast(text: String, icon: String = Ic.check) {
        val id = ++toastSeq
        toast = ToastMsg(text, icon, id)
        scope.launch {
            delay(2600)
            if (toast?.id == id) toast = null
        }
    }

    fun openViewer(list: List<Asset>, index: Int) {
        if (list.isEmpty()) return
        nav.navigate(ViewerRoute(MediaQueue.put(list), index.coerceIn(0, list.lastIndex)))
    }

    fun slideshow(req: SlideshowRequest) {
        if (req.assets.isEmpty()) {
            toast("Nothing to show here yet", Ic.info); return
        }
        nav.navigate(SlideshowRoute(MediaQueue.putSlideshow(req)))
    }

    /** Slideshow starting at [index] and wrapping around, in list order. */
    fun slideshowFrom(list: List<Asset>, index: Int) {
        val i = index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        slideshow(SlideshowRequest(list.drop(i) + list.take(i), SlideMode.NORMAL, keepOrder = true))
    }

    fun playMemory(m: Memory) = slideshow(SlideshowRequest(m.assets, SlideMode.MEMORY, memory = m, keepOrder = true))

    fun openAlbum(id: String) = nav.navigate(AlbumRoute(id))
    fun openPerson(id: String) = nav.navigate(PersonRoute(id))
    fun openPlace(title: String, names: List<String>, country: String) = nav.navigate(PlaceRoute(title, names, country))

    /** Switches rail section: replaces the whole back stack, so Back from content goes to the rail. */
    fun goTop(route: Any) {
        nav.navigate(route) {
            popUpTo(nav.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    /** Assets for a slideshow/screensaver source label from Settings. */
    suspend fun assetsForSource(source: String): List<Asset> = when {
        source == "All photos" -> repo.timelineBuckets().take(6).flatMap { repo.timelineBucket(it.key) }.take(60)
        source == "Memories" -> repo.memories().flatMap { it.assets }
        source.startsWith("Album: ") -> repo.albums().firstOrNull { it.title == source.removePrefix("Album: ") }?.let { repo.albumAssets(it.id) }.orEmpty()
        source.startsWith("Person: ") -> repo.people().firstOrNull { it.name == source.removePrefix("Person: ") }?.let { repo.personAssets(it.id) }.orEmpty()
        else -> repo.favorites()
    }

    fun showMenu(target: MenuTarget) {
        modal = ContextMenu(this, target)
    }

    fun focusRail() {
        runCatching { railRequester.requestFocus() }
    }

    /** Focus memory of the visible screen, registered by [ScreenFocus]. */
    var screenMemory: com.android.glume.ui.components.FocusMemory? = null

    fun focusContent() {
        if (screenMemory?.restore() == true) return
        runCatching { contentRequester.requestFocus() }
    }
}

val LocalApp = staticCompositionLocalOf<AppController> { error("AppController not provided") }
