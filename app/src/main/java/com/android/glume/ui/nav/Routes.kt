package com.android.glume.ui.nav

import kotlinx.serialization.Serializable

/** Type-safe navigation routes (Compose Navigation + kotlinx.serialization). */

@Serializable data object ConnectRoute
@Serializable data object ProfilesRoute

// Rail sections
@Serializable data object HomeRoute
@Serializable data class TimelineRoute(val year: Int = -1, val month: Int = -1)
@Serializable data object AlbumsRoute
@Serializable data object PeopleRoute
@Serializable data class PlacesRoute(val map: Boolean = false, val pin: String = "")
@Serializable data object FavoritesRoute
@Serializable data object SearchRoute
@Serializable data object SettingsRoute

// Details
@Serializable data class AlbumRoute(val id: String)
@Serializable data class PersonRoute(val id: String)
@Serializable data class PlaceRoute(val title: String, val names: List<String>, val country: String)

/** Full-screen media. [token] refers to a list parked in [MediaQueue]. */
@Serializable data class ViewerRoute(val token: Long, val index: Int)
@Serializable data class SlideshowRoute(val token: Long)

enum class RailSection(val label: String, val icon: String, val route: Any) {
    Home("Home", com.android.glume.ui.components.Ic.home, HomeRoute),
    Timeline("Timeline", com.android.glume.ui.components.Ic.grid, TimelineRoute()),
    Albums("Albums", com.android.glume.ui.components.Ic.album, AlbumsRoute),
    People("People", com.android.glume.ui.components.Ic.people, PeopleRoute),
    Places("Places", com.android.glume.ui.components.Ic.pin, PlacesRoute()),
    Favorites("Favorites", com.android.glume.ui.components.Ic.heart, FavoritesRoute),
    Search("Search", com.android.glume.ui.components.Ic.search, SearchRoute),
    Settings("Settings", com.android.glume.ui.components.Ic.gear, SettingsRoute),
}
