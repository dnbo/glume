package com.android.glume.domain

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/*
 * Small immutable UI-facing models. The repository maps API DTOs (or mock data) into these, so
 * screens never see HTTP types. Image URLs are already resolved to the right size by the repository.
 */

enum class AssetKind { PHOTO, VIDEO, LIVE, RAW }

@Immutable
data class Asset(
    val id: String,
    val kind: AssetKind,
    val date: LocalDate,
    val place: String,
    val country: String,
    /** e.g. "Sony A7 IV · 35 mm · ƒ/2.8 · 1/250 s · ISO 200" */
    val cameraLine: String,
    val durationSec: Int,
    val peopleIds: List<String>,
    val owner: String,
    /** Server-generated small thumbnail, for grids and rows. */
    val thumbUrl: String,
    /** Server-generated preview-size image, for the viewer and slideshow. */
    val previewUrl: String,
    /** Original file; only used when the user picks "Original" quality. */
    val originalUrl: String,
    val videoUrl: String?,
    /** Base64 ThumbHash from the server, drawn before the thumbnail arrives. */
    val thumbhash: String?,
    /** Caption used by memory playback. */
    val story: String? = null,
) {
    val isVideo get() = kind == AssetKind.VIDEO
}

@Immutable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String?,
    /** "Owner", "Partner", … */
    val role: String,
    val libraryEmpty: Boolean = false,
)

@Immutable
data class Person(
    val id: String,
    /** Null for faces nobody has named yet. */
    val name: String?,
    val avatarUrl: String,
    val count: Int,
)

@Immutable
data class Album(
    val id: String,
    val title: String,
    val count: Int,
    val cover: Asset?,
    val shared: Boolean,
    val ownerName: String,
    val ownerAvatarUrl: String?,
    val dateLabel: String,
    val description: String,
    val contributors: List<Person>,
)

@Immutable
data class Place(
    val name: String,
    val country: String,
    val count: Int,
    val cover: Asset,
)

/** A pin on the Places map. [x] and [y] are percentages of the map image. */
@Immutable
data class MapPin(
    val label: String,
    val placeNames: List<String>,
    val x: Float,
    val y: Float,
    val count: Int,
    val country: String,
    val cover: Asset,
)

@Immutable
data class Memory(
    val id: String,
    val year: Int,
    /** "3 years ago" */
    val agoLabel: String,
    val place: String,
    val count: Int,
    val assets: List<Asset>,
)

enum class ContinueKind { RESUME_SLIDESHOW, ALBUM, TIMELINE }

@Immutable
data class ContinueItem(
    val kind: ContinueKind,
    val title: String,
    val subtitle: String,
    val cover: Asset,
    val progress: Float,
    val albumId: String? = null,
    val timelineMonth: YearMonthKey? = null,
)

@Immutable
data class YearMonthKey(val year: Int, val month: Int) : Comparable<YearMonthKey> {
    override fun compareTo(other: YearMonthKey) = compareValuesBy(this, other, { it.year }, { it.month })
}

/** One month in the timeline, as returned by Immich's time-bucket endpoints. */
@Immutable
data class TimeBucket(val key: YearMonthKey, val count: Int)

@Immutable
data class HomeData(
    val memories: List<Memory>,
    val continueItems: List<ContinueItem>,
    val recent: List<Asset>,
    val favorites: List<Asset>,
    val albums: List<Album>,
    val people: List<Person>,
    val partnerName: String,
    val partnerPhotos: List<Asset>,
    val sharedWithMe: List<Album>,
) {
    val isEmpty get() = memories.isEmpty() && recent.isEmpty() && albums.isEmpty()
}

@Immutable
data class ServerInfo(
    val url: String,
    val latencyMs: Int,
    val photos: Int,
    val videos: Int,
    val usedBytes: Long,
    val totalBytes: Long,
)

/** Search filter chip: tapping cycles through [options]; index 0 means "any". */
@Immutable
data class SearchFilter(val name: String, val icon: String, val options: List<String>)

sealed interface ServerCheck {
    data object Ok : ServerCheck
    data class Unreachable(val host: String, val reason: String) : ServerCheck
    data object InvalidCredentials : ServerCheck
}
