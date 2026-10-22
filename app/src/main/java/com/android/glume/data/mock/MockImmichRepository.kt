package com.android.glume.data.mock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.android.glume.data.ImmichRepository
import com.android.glume.domain.Album
import com.android.glume.domain.Asset
import com.android.glume.domain.ContinueItem
import com.android.glume.domain.ContinueKind
import com.android.glume.domain.HomeData
import com.android.glume.domain.MapPin
import com.android.glume.domain.Memory
import com.android.glume.domain.Person
import com.android.glume.domain.Place
import com.android.glume.domain.SearchFilter
import com.android.glume.domain.ServerCheck
import com.android.glume.domain.ServerInfo
import com.android.glume.domain.TimeBucket
import com.android.glume.domain.User
import com.android.glume.domain.YearMonthKey
import com.android.glume.util.ThumbHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Serves [MockLibrary] as if it came from an Immich server, so the app runs without one.
 * Thumbnails, previews and originals all point at the bundled 960 px sample photos; videos stream a
 * public test clip. ThumbHashes are computed once from the bundled photos, mimicking the server.
 */
class MockImmichRepository(private val context: Context) : ImmichRepository {

    override val isDemo = true

    private val favs = MutableStateFlow(MockLibrary.initialFavorites.map(::assetId).toSet())
    override val favoriteIds: StateFlow<Set<String>> = favs

    private val hashLock = Mutex()
    private var thumbhashes: Array<String?>? = null
    private var assets: List<Asset> = emptyList()
    private val byId = HashMap<String, Asset>()

    private suspend fun ensureLoaded() {
        if (thumbhashes != null) return
        hashLock.withLock {
            if (thumbhashes != null) return
            val hashes = withContext(Dispatchers.Default) { Array(MockLibrary.PHOTO_COUNT) { computeHash(photoPath(it)) } }
            thumbhashes = hashes
            assets = MockLibrary.items.map { toAsset(it) }
            assets.forEach { byId[it.id] = it }
        }
    }

    private fun computeHash(assetPath: String): String? = runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = 16 }
        val bmp = context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val scaled = if (bmp.width > 100 || bmp.height > 100) {
            val s = 100f / maxOf(bmp.width, bmp.height)
            Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
        } else bmp
        val px = IntArray(scaled.width * scaled.height)
        scaled.getPixels(px, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        val rgba = ByteArray(px.size * 4)
        px.forEachIndexed { i, c ->
            rgba[i * 4] = (c shr 16 and 0xFF).toByte()
            rgba[i * 4 + 1] = (c shr 8 and 0xFF).toByte()
            rgba[i * 4 + 2] = (c and 0xFF).toByte()
            rgba[i * 4 + 3] = (c ushr 24).toByte()
        }
        Base64.encodeToString(ThumbHash.encode(scaled.width, scaled.height, rgba), Base64.NO_WRAP)
    }.getOrNull()

    private fun toAsset(it: MockLibrary.Item, story: String? = null, idOverride: String? = null): Asset {
        val meta = MockLibrary.META[it.img]
        val url = ASSET_URI + photoPath(it.img)
        return Asset(
            id = idOverride ?: assetId(it.id),
            kind = it.kind,
            date = LocalDate.of(it.year, it.month + 1, it.day),
            place = meta.place,
            country = meta.country,
            cameraLine = MockLibrary.CAMERAS.getValue(meta.camera) + if (it.kind == com.android.glume.domain.AssetKind.RAW) " · RAW" else "",
            durationSec = it.durationSec,
            peopleIds = it.people.map(::personId),
            owner = it.by,
            thumbUrl = url,
            previewUrl = url,
            originalUrl = url,
            videoUrl = if (it.kind == com.android.glume.domain.AssetKind.VIDEO) SAMPLE_VIDEO else null,
            thumbhash = thumbhashes?.get(it.img),
            story = story,
        )
    }

    private suspend fun latency(ms: Long = 280) = delay(ms)

    override suspend fun connect(serverUrl: String, email: String, password: String): ServerCheck {
        latency(1400)
        val host = serverUrl.trim().lowercase().removeSuffix("/").removePrefix("https://").removePrefix("http://")
        return if (host == DEMO_HOST) ServerCheck.Ok
        else ServerCheck.Unreachable(host, "Connection timed out after 10 seconds")
    }

    override suspend fun serverInfo() = ServerInfo("https://$DEMO_HOST", 18, 48_210, 2_306, 1_420_000_000_000L, 4_000_000_000_000L)

    override suspend fun users(): List<User> = MockLibrary.USERS.mapIndexed { i, u ->
        User("u$i", u.name, u.email, u.avatar?.let(::avatarUrl), u.role, u.empty)
    }

    override suspend fun user(id: String): User? = users().firstOrNull { it.id == id }

    override suspend fun home(user: User): HomeData {
        ensureLoaded(); latency()
        if (user.libraryEmpty) return HomeData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), "", emptyList(), emptyList())
        val partner = if (user.name == "Sam") "Alex" else "Sam"
        val albums = albumsNow()
        return HomeData(
            memories = memoriesNow(),
            continueItems = listOf(
                ContinueItem(ContinueKind.RESUME_SLIDESHOW, "Resume slideshow", "Favorites · 34 of 120", coverFor(21), 0.28f),
                ContinueItem(ContinueKind.ALBUM, "Summer house 2026", "Last album · photo 12 of 348", coverFor(9), 0.04f, albumId = albumId(1)),
                ContinueItem(ContinueKind.TIMELINE, "Back where you were", "Timeline · August 2024", coverFor(13), 0.62f, timelineMonth = YearMonthKey(2024, 8)),
            ),
            recent = assets.take(16),
            favorites = favoritesNow().take(16),
            albums = albums.take(8),
            people = peopleNow().filter { it.name != null },
            partnerName = partner,
            partnerPhotos = assets.filter { it.owner == partner }.take(16),
            sharedWithMe = albums.filter { it.shared && it.ownerName != user.name },
        )
    }

    override suspend fun timelineBuckets(): List<TimeBucket> {
        ensureLoaded(); latency(150)
        return MockLibrary.items.groupBy { YearMonthKey(it.year, it.month + 1) }
            .map { (k, v) -> TimeBucket(k, v.size) }
    }

    override suspend fun timelineBucket(key: YearMonthKey): List<Asset> {
        ensureLoaded(); latency(120)
        return assets.filter { it.date.year == key.year && it.date.monthValue == key.month }
    }

    override suspend fun albums(): List<Album> { ensureLoaded(); latency(); return albumsNow() }
    override suspend fun album(id: String): Album? { ensureLoaded(); return albumsNow().firstOrNull { it.id == id } }
    override suspend fun albumAssets(id: String): List<Asset> {
        ensureLoaded(); latency()
        val i = id.removePrefix("al").toIntOrNull() ?: return emptyList()
        return MockLibrary.albumItems(i).map { byId.getValue(assetId(it.id)) }
    }

    override suspend fun people(): List<Person> { ensureLoaded(); latency(); return peopleNow() }
    override suspend fun person(id: String): Person? { ensureLoaded(); return peopleNow().firstOrNull { it.id == id } }
    override suspend fun personAssets(id: String): List<Asset> {
        ensureLoaded(); latency()
        val i = id.removePrefix("p").toIntOrNull() ?: return emptyList()
        return MockLibrary.personItems(i).map { byId.getValue(assetId(it.id)) }
    }

    override suspend fun places(): List<Place> {
        ensureLoaded(); latency()
        return MockLibrary.places.map { Place(it.name, it.country, it.count, coverFor(it.img)) }
    }

    override suspend fun mapPins(): List<MapPin> {
        ensureLoaded()
        val places = MockLibrary.places
        return MockLibrary.PINS.map { p ->
            val pl = places.filter { it.name in p.names }
            MapPin(p.label, p.names, p.x, p.y, pl.sumOf { it.count }, pl.firstOrNull()?.country.orEmpty(), coverFor(pl.firstOrNull()?.img ?: 0))
        }
    }

    override suspend fun placeAssets(placeNames: List<String>): List<Asset> {
        ensureLoaded(); latency()
        return assets.filter { it.place in placeNames }.take(60)
    }

    override suspend fun favorites(): List<Asset> { ensureLoaded(); latency(); return favoritesNow() }

    override suspend fun setFavorite(assetId: String, favorite: Boolean) {
        favs.update { if (favorite) it + assetId else it - assetId }
    }

    override suspend fun addToAlbum(assetId: String, albumId: String) = latency(120)

    override suspend fun memories(): List<Memory> { ensureLoaded(); return memoriesNow() }

    override fun searchFilters(): List<SearchFilter> = MockLibrary.FILTERS.map { SearchFilter(it.name, it.icon, it.options) }

    override suspend fun search(query: String, filters: List<Int>): List<Asset> {
        ensureLoaded(); latency(200)
        return MockLibrary.search(query, filters).map { byId.getValue(assetId(it.id)) }
    }

    override suspend fun albumOf(asset: Asset): Album? {
        val img = imgOf(asset) ?: return null
        return albumsNow().firstOrNull { a -> MockLibrary.ALBUMS[a.id.removePrefix("al").toInt()].let { it.count > 0 && img in it.imgs } }
    }

    override suspend fun peopleIn(asset: Asset): List<Person> {
        val all = peopleNow()
        return asset.peopleIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
    }

    // ---- mapping helpers ----

    private fun imgOf(asset: Asset): Int? = asset.thumbUrl.substringAfterLast("photo_").substringBefore(".jpg").toIntOrNull()

    private fun coverFor(img: Int): Asset {
        val item = MockLibrary.items.firstOrNull { it.img == img } ?: MockLibrary.items.first()
        return byId[assetId(item.id)] ?: toAsset(item)
    }

    private fun favoritesNow(): List<Asset> {
        val f = favs.value
        return assets.filter { it.id in f }
    }

    private fun memoriesNow(): List<Memory> = MockLibrary.MEMORIES.mapIndexed { mi, m ->
        val list = m.imgs.mapIndexed { j, img ->
            val base = MockLibrary.items.first { it.img == img }
            toAsset(base.copy(year = m.year, month = 9, day = 21, kind = com.android.glume.domain.AssetKind.PHOTO), story = m.story[j % m.story.size], idOverride = "m$mi-$j")
        }
        Memory("m$mi", m.year, m.ago, m.place, m.count, list)
    }

    private fun peopleNow(): List<Person> {
        val named = MockLibrary.PEOPLE.mapIndexed { i, n -> Person(personId(i), n, avatarUrl(i), MockLibrary.personItems(i).size) }
        val unnamed = MockLibrary.UNNAMED.mapIndexed { k, u -> Person("f$k", null, avatarUrl(u.avatar), u.count) }
        return named + unnamed
    }

    private fun albumsNow(): List<Album> {
        val people = MockLibrary.PEOPLE.mapIndexed { i, n -> Person(personId(i), n, avatarUrl(i), 0) }
        return MockLibrary.ALBUMS.mapIndexed { i, a ->
            Album(
                id = albumId(i), title = a.title, count = a.count,
                cover = if (a.cover >= 0) coverFor(a.cover) else null,
                shared = a.shared, ownerName = a.owner, ownerAvatarUrl = avatarUrl(if (a.owner == "Sam") 1 else 4),
                dateLabel = a.date, description = a.description, contributors = a.contributors.map { people[it] },
            )
        }
    }

    companion object {
        const val DEMO_HOST = "photos.example.home"
        const val DEMO_URL = "https://$DEMO_HOST"
        const val DEMO_EMAIL = "alex@example.home"
        const val DEMO_PASSWORD = "correct-horse"
        private const val ASSET_URI = "file:///android_asset/"
        private const val SAMPLE_VIDEO = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"

        fun photoPath(i: Int) = "mock/photo_%02d.jpg".format(i)
        fun avatarUrl(i: Int) = ASSET_URI + "mock/avatar_%02d.jpg".format(i)
        const val MAP_URL = ASSET_URI + "mock/map.png"
        const val QR_URL = ASSET_URI + "mock/qr.png"
        fun assetId(i: Int) = "a$i"
        fun personId(i: Int) = "p$i"
        fun albumId(i: Int) = "al$i"
    }
}
