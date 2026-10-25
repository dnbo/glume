package com.android.glume.data

import com.android.glume.domain.Album
import com.android.glume.domain.Asset
import com.android.glume.domain.HomeData
import com.android.glume.domain.MapPin
import com.android.glume.domain.Memory
import com.android.glume.domain.Person
import com.android.glume.domain.Place
import com.android.glume.domain.SearchFilter
import com.android.glume.domain.ServerInfo
import com.android.glume.domain.TimeBucket
import com.android.glume.domain.User
import com.android.glume.domain.YearMonthKey
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the UI needs from an Immich server. The only implementation today is
 * [com.android.glume.data.mock.MockImmichRepository]; a Ktor-backed one will replace it once the
 * real server is reachable. Keep this interface free of HTTP/DTO types.
 *
 * Read-only client (see CLAUDE.md): the only writes allowed are [setFavorite] and [addToAlbum].
 * Never add delete, trash, archive, edit, album create/remove or upload methods here.
 */
interface ImmichRepository {

    /** True while a demo (mock) library is being served. */
    val isDemo: Boolean

    /** Asset ids the current user has favorited. Updated optimistically by [setFavorite]. */
    val favoriteIds: StateFlow<Set<String>>

    suspend fun serverInfo(): ServerInfo

    suspend fun home(user: User): HomeData

    suspend fun timelineBuckets(): List<TimeBucket>
    suspend fun timelineBucket(key: YearMonthKey): List<Asset>

    suspend fun albums(): List<Album>
    suspend fun album(id: String): Album?
    suspend fun albumAssets(id: String): List<Asset>

    /** Named people first, then unnamed faces (name == null). */
    suspend fun people(): List<Person>
    suspend fun person(id: String): Person?
    suspend fun personAssets(id: String): List<Asset>

    suspend fun places(): List<Place>
    suspend fun mapPins(): List<MapPin>
    suspend fun placeAssets(placeNames: List<String>): List<Asset>

    suspend fun favorites(): List<Asset>
    suspend fun setFavorite(assetId: String, favorite: Boolean)
    suspend fun addToAlbum(assetId: String, albumId: String)

    suspend fun memories(): List<Memory>

    fun searchFilters(): List<SearchFilter>

    /** Smart search. [filters] holds one selected option index per [searchFilters] entry (0 = any). */
    suspend fun search(query: String, filters: List<Int>): List<Asset>

    /** The asset's album, if any, for the viewer's info overlay. */
    suspend fun albumOf(asset: Asset): Album?
    suspend fun peopleIn(asset: Asset): List<Person>
}
