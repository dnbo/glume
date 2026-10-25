package com.android.glume.ui.app

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.android.glume.data.ImmichRepository
import com.android.glume.data.Prefs
import com.android.glume.data.SettingsStore
import com.android.glume.data.auth.AccountStore
import com.android.glume.ui.components.Ic
import com.android.glume.ui.components.AmbientBackdrop
import com.android.glume.ui.components.FocusMemory
import com.android.glume.ui.components.RestoreFocus
import com.android.glume.ui.components.ShimmerProvider
import com.android.glume.ui.nav.AlbumRoute
import com.android.glume.ui.nav.AlbumsRoute
import com.android.glume.ui.nav.ConnectRoute
import com.android.glume.ui.nav.FavoritesRoute
import com.android.glume.ui.nav.HomeRoute
import com.android.glume.ui.nav.PeopleRoute
import com.android.glume.ui.nav.PersonRoute
import com.android.glume.ui.nav.PlaceRoute
import com.android.glume.ui.nav.PlacesRoute
import com.android.glume.ui.nav.ProfilesRoute
import com.android.glume.ui.nav.RailSection
import com.android.glume.ui.nav.SearchRoute
import com.android.glume.ui.nav.SettingsRoute
import com.android.glume.ui.nav.SlideMode
import com.android.glume.ui.nav.SlideshowRequest
import com.android.glume.ui.nav.SlideshowRoute
import com.android.glume.ui.nav.TimelineRoute
import com.android.glume.ui.nav.ViewerRoute
import com.android.glume.ui.screens.Session
import com.android.glume.ui.screens.albums.AlbumsScreen
import com.android.glume.ui.screens.collection.CollectionScreen
import com.android.glume.ui.screens.collection.CollectionSource
import com.android.glume.ui.screens.connect.ConnectScreen
import com.android.glume.ui.screens.home.HomeScreen
import com.android.glume.ui.screens.people.PeopleScreen
import com.android.glume.ui.screens.places.PlacesScreen
import com.android.glume.ui.screens.profiles.ProfilesScreen
import com.android.glume.ui.screens.search.SearchScreen
import com.android.glume.ui.screens.settings.SettingsScreen
import com.android.glume.ui.screens.slideshow.SlideshowScreen
import com.android.glume.ui.screens.timeline.TimelineScreen
import com.android.glume.ui.screens.viewer.ViewerScreen
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeMotion
import com.android.glume.ui.theme.GlumeTheme
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/** App entry composable: waits for settings, applies the theme, then hosts the shell. */
@Composable
fun GlumeRoot(
    settings: SettingsStore = koinInject(),
    repo: ImmichRepository = koinInject(),
    session: Session = koinInject(),
    accountStore: AccountStore = koinInject(),
) {
    val prefs by settings.prefs.collectAsStateWithLifecycle()
    val accounts by accountStore.accounts.collectAsStateWithLifecycle()
    if (!prefs.loaded || accounts == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0A0C11)))
        return
    }
    GlumeTheme(highContrast = prefs.highContrast, largeText = prefs.largeText, reduceMotion = prefs.reduceMotion) {
        val nav = rememberNavController()
        val scope = rememberCoroutineScope()
        val app = remember { AppController(nav, repo, settings, scope) }
        val start: Any = remember {
            when {
                accounts.isNullOrEmpty() -> ConnectRoute
                accounts!!.none { it.id == prefs.userId } -> ProfilesRoute
                else -> HomeRoute
            }
        }
        CompositionLocalProvider(LocalApp provides app) {
            ShimmerProvider { AppShell(app, start, prefs, session) }
        }
    }
}

private fun NavDestination?.section(): RailSection? =
    this?.let { d -> RailSection.entries.firstOrNull { d.hasRoute(it.route::class) } }

private fun NavDestination?.isFullScreen(): Boolean =
    this != null && (hasRoute(ViewerRoute::class) || hasRoute(SlideshowRoute::class))

private fun NavDestination?.isSignedOut(): Boolean =
    this != null && (hasRoute(ConnectRoute::class) || hasRoute(ProfilesRoute::class))

private val KONAMI = listOf(Key.DirectionUp, Key.DirectionUp, Key.DirectionDown, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionLeft, Key.DirectionRight)

@Composable
private fun AppShell(app: AppController, start: Any, prefs: Prefs, session: Session) {
    val nav = app.nav
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val section = dest.section()
    val user by session.user.collectAsStateWithLifecycle()
    val c = Glume.colors
    val keyHistory = remember { ArrayDeque<Key>() }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                if (c.highContrast) drawRect(Color.Black)
                else drawRect(Brush.radialGradient(listOf(Color(0xFF151A28), c.bg), center = Offset(size.width * 0.3f, 0f), radius = size.width * 0.75f))
            }
            .onPreviewKeyEvent { e ->
                app.lastInputAt = SystemClock.uptimeMillis()
                if (e.type == KeyEventType.KeyDown) {
                    keyHistory.addLast(e.key)
                    if (keyHistory.size > KONAMI.size) keyHistory.removeFirst()
                    if (keyHistory.toList() == KONAMI) { app.debugOverlay = !app.debugOverlay; keyHistory.clear() }
                }
                app.modal?.onKey(e) ?: false
            }
            .onKeyEvent { e ->
                // Escape on a USB keyboard behaves like the remote's Back.
                if (e.key != Key.Escape) return@onKeyEvent false
                if (e.type == KeyEventType.KeyDown) app.back()
                true
            },
    ) {
        if (!c.highContrast && !dest.isFullScreen() && !dest.isSignedOut()) AmbientBackdrop(app.ambient)
        Box(
            Modifier.fillMaxSize().focusRequester(app.contentRequester).focusGroup(),
        ) {
            NavHost(
                navController = nav,
                startDestination = start,
                enterTransition = { screenEnter() },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { screenEnter() },
                popExitTransition = { fadeOut(tween(160)) },
            ) {
                composable<ConnectRoute> { ConnectScreen() }
                composable<ProfilesRoute> { ProfilesScreen() }
                composable<HomeRoute> { HomeScreen() }
                composable<TimelineRoute> { TimelineScreen(it.toRoute()) }
                composable<AlbumsRoute> { AlbumsScreen() }
                composable<PeopleRoute> { PeopleScreen() }
                composable<PlacesRoute> { PlacesScreen(it.toRoute()) }
                composable<FavoritesRoute> { CollectionScreen(CollectionSource.Favorites) }
                composable<SearchRoute> { SearchScreen() }
                composable<SettingsRoute> { SettingsScreen() }
                composable<AlbumRoute> { CollectionScreen(CollectionSource.Album(it.toRoute<AlbumRoute>().id)) }
                composable<PersonRoute> { CollectionScreen(CollectionSource.Person(it.toRoute<PersonRoute>().id)) }
                composable<PlaceRoute> { val r = it.toRoute<PlaceRoute>(); CollectionScreen(CollectionSource.Place(r.title, r.names, r.country)) }
                composable<ViewerRoute>(enterTransition = { fadeIn(tween(260)) }, popEnterTransition = { fadeIn(tween(200)) }) { ViewerScreen(it.toRoute()) }
                composable<SlideshowRoute>(enterTransition = { fadeIn(tween(400)) }) { SlideshowScreen(it.toRoute()) }
            }
        }
        if (section != null) {
            Rail(section, user, user?.role.orEmpty(), app)
        }
        if (prefs.simulateOffline && !dest.isFullScreen() && !dest.isSignedOut()) OfflineBanner()
        app.modal?.Content()
        ToastHost(app.toast)
        if (app.debugOverlay) DebugOverlay()
    }

    // Back is delivered by the back dispatcher (predictive back on targetSdk 36+), never as a key
    // event, so it is handled here in one place. NavHost's own handler is disabled so this one decides.
    val activity = LocalActivity.current
    LaunchedEffect(Unit) { nav.enableOnBackPressed(false) }
    app.rootBack = {
        when {
            section != null && !app.railFocused -> app.focusRail()
            section != null && section != RailSection.Home -> { app.holdRailFocus = true; app.goTop(HomeRoute) }
            section == RailSection.Home -> app.toast("Press Home on the remote to leave Immich", Ic.info)
            nav.previousBackStackEntry != null -> nav.popBackStack()
            dest?.hasRoute(ProfilesRoute::class) == true && prefs.userId != null -> app.goTop(HomeRoute)
            else -> activity?.moveTaskToBack(true)
        }
    }
    BackHandler { app.back() }

    // Back from a section to Home lands on the rail, not the content.
    LaunchedEffect(entry) {
        if (app.holdRailFocus) {
            repeat(3) { withFrameNanos { } }
            app.focusRail()
            app.holdRailFocus = false
        }
    }

    // In-app idle trigger for the ambient screensaver.
    LaunchedEffect(prefs.saver, prefs.saverAfter, prefs.saverSource) {
        if (!prefs.saver) return@LaunchedEffect
        while (true) {
            delay(5_000)
            val d = nav.currentDestination
            val idle = SystemClock.uptimeMillis() - app.lastInputAt
            if (d.isFullScreen() || d.isSignedOut() || app.modal != null) continue
            if (idle > prefs.saverAfterMs) {
                app.lastInputAt = SystemClock.uptimeMillis()
                app.slideshow(SlideshowRequest(app.assetsForSource(prefs.saverSource), SlideMode.AMBIENT))
            }
        }
    }
}

private fun screenEnter(): EnterTransition =
    fadeIn(tween(280, easing = GlumeMotion.Ease)) + slideInVertically(tween(280, easing = GlumeMotion.Ease)) { 14 }

/** Lets a screen take Back while [enabled] (zoomed viewer, open panel, settings rows…). */
@Composable
fun InterceptBack(enabled: Boolean, onBack: () -> Unit) {
    val app = LocalApp.current
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(app) {
        val f: () -> Boolean = { if (currentEnabled) { currentOnBack(); true } else false }
        app.backInterceptors.add(f)
        onDispose { app.backInterceptors.remove(f) }
    }
}

/**
 * Restores a screen's focus when it appears, unless Back is parking focus on the rail. Decided
 * once per visit so it never steals focus later.
 */
@Composable
fun ScreenFocus(memory: FocusMemory, ready: Boolean, fallbackKey: Any?) {
    val app = LocalApp.current
    DisposableEffect(memory) {
        app.screenMemory = memory
        onDispose { if (app.screenMemory === memory) app.screenMemory = null }
    }
    val allowed = remember { !app.holdRailFocus }
    if (allowed) RestoreFocus(memory, ready, fallbackKey)
}
