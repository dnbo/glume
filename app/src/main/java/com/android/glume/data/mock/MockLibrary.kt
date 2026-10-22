package com.android.glume.data.mock

import com.android.glume.domain.AssetKind

/**
 * Deterministic demo library, ported from the design prototype so the app shows the same
 * photos, albums, people and memories as the handoff. Pure Kotlin (no Android) so it is unit-testable.
 * Image references are indexes into the bundled sample photos (assets/mock/photo_NN.jpg).
 */
object MockLibrary {

    const val PHOTO_COUNT = 24
    const val AVATAR_COUNT = 12

    data class Meta(val place: String, val country: String, val tags: List<String>, val camera: String)

    data class Item(
        val id: Int,
        val img: Int,
        val year: Int,
        /** 0-based month, like the prototype. */
        val month: Int,
        val day: Int,
        val kind: AssetKind,
        val durationSec: Int,
        val people: List<Int>,
        val by: String,
        val firstOfDay: Boolean,
    )

    data class AlbumSpec(
        val title: String,
        val count: Int,
        val cover: Int,
        val imgs: List<Int>,
        val shared: Boolean,
        val owner: String,
        val date: String,
        val description: String,
        val contributors: List<Int>,
    )

    data class MemorySpec(val year: Int, val ago: String, val place: String, val imgs: List<Int>, val count: Int, val story: List<String>)
    data class PinSpec(val label: String, val names: List<String>, val x: Float, val y: Float)
    data class UnnamedFace(val avatar: Int, val count: Int)
    data class UserSpec(val name: String, val avatar: Int?, val email: String, val role: String, val empty: Boolean = false)

    val META = listOf(
        Meta("Mallorca", "Spain", listOf("sunset", "sea", "beach", "coast", "golden"), "iPhone 15 Pro"),
        Meta("Dolomites", "Italy", listOf("mountains", "hiking", "haze"), "Sony A7 IV"),
        Meta("Dalarna", "Sweden", listOf("forest", "trees", "mist"), "Pixel 8"),
        Meta("Gotland", "Sweden", listOf("beach", "sea", "summer"), "iPhone 15 Pro"),
        Meta("Stockholm", "Sweden", listOf("city", "night", "lights"), "Sony A7 IV"),
        Meta("Lofoten", "Norway", listOf("lake", "mountains", "reflection"), "Sony A7 IV"),
        Meta("Merzouga", "Morocco", listOf("desert", "dunes", "sunset"), "iPhone 15 Pro"),
        Meta("Abisko", "Sweden", listOf("aurora", "northern", "lights", "night", "stars", "winter"), "Sony A7 IV"),
        Meta("Öland", "Sweden", listOf("meadow", "flowers", "summer"), "Pixel 8"),
        Meta("Stockholm archipelago", "Sweden", listOf("islands", "sea", "summer", "boat"), "iPhone 15 Pro"),
        Meta("Lofoten", "Norway", listOf("mountains", "sunset", "dusk"), "Sony A7 IV"),
        Meta("Lisbon", "Portugal", listOf("sunset", "sea", "beach", "coast"), "iPhone 15 Pro"),
        Meta("Dalarna", "Sweden", listOf("forest", "autumn", "golden"), "Pixel 8"),
        Meta("Mallorca", "Spain", listOf("beach", "sea", "summer"), "iPhone 15 Pro"),
        Meta("Kyoto", "Japan", listOf("city", "night", "lights"), "Sony A7 IV"),
        Meta("Abisko", "Sweden", listOf("mountains", "snow", "winter"), "Sony A7 IV"),
        Meta("Reykjavík", "Iceland", listOf("aurora", "northern", "lights", "night", "winter"), "Sony A7 IV"),
        Meta("Lofoten", "Norway", listOf("lake", "mountains", "reflection", "morning"), "Pixel 8"),
        Meta("Merzouga", "Morocco", listOf("desert", "dunes", "dusk"), "iPhone 15 Pro"),
        Meta("Öland", "Sweden", listOf("meadow", "flowers", "poppies"), "Pixel 8"),
        Meta("Stockholm archipelago", "Sweden", listOf("islands", "sunset", "summer"), "iPhone 15 Pro"),
        Meta("Gotland", "Sweden", listOf("sunset", "sea", "beach", "coast"), "iPhone 15 Pro"),
        Meta("Dalarna", "Sweden", listOf("forest", "mist", "morning"), "Pixel 8"),
        Meta("Dolomites", "Italy", listOf("mountains", "moon", "dusk"), "Sony A7 IV"),
    )

    val CAMERAS = mapOf(
        "Sony A7 IV" to "Sony A7 IV · 35 mm · ƒ/2.8 · 1/250 s · ISO 200",
        "iPhone 15 Pro" to "iPhone 15 Pro · 24 mm · ƒ/1.8 · 1/120 s · ISO 64",
        "Pixel 8" to "Pixel 8 · 25 mm · ƒ/1.7 · 1/500 s · ISO 50",
    )

    val PEOPLE = listOf("Emma", "Sam", "Oskar", "Maja", "Alex", "Leo", "Nora", "Elias", "Ingrid", "Ellie", "Hugo", "Alva")

    val UNNAMED = listOf(
        UnnamedFace(2, 14), UnnamedFace(5, 9), UnnamedFace(7, 6),
        UnnamedFace(10, 5), UnnamedFace(11, 3), UnnamedFace(3, 2),
    )

    val USERS = listOf(
        UserSpec("Alex", 4, "alex@example.home", "Owner"),
        UserSpec("Sam", 1, "sam@example.home", "Partner"),
        UserSpec("Kids", 9, "kids@example.home", "Shared albums"),
        UserSpec("Guest", null, "guest@example.home", "Empty library", empty = true),
    )

    val ALBUMS = listOf(
        AlbumSpec("Lofoten 2023", 212, 5, listOf(5, 10, 17, 1, 23), true, "Alex", "Oct 2023", "Ten days of hiking, rorbu mornings and a lot of rain between the good bits.", listOf(0, 1, 2)),
        AlbumSpec("Summer house 2026", 348, 9, listOf(9, 20, 3, 8, 19), true, "Sam", "Jun – Aug 2026", "Swims, boat trips and long evenings on the jetty.", listOf(0, 4, 3, 9)),
        AlbumSpec(
            "Midsummer weekend at the summer house with everyone, plus the long drive home through Dalarna", 96, 8, listOf(8, 19, 2, 12, 22), false, "Alex", "Jun 2025",
            "A deliberately long album name and description to check truncation on the TV: titles get one line with an ellipsis in grids and rows, descriptions get two lines here, and nothing ever wraps or reflows while an item is focused, because focus should feel calm and predictable.",
            listOf(0, 3),
        ),
        AlbumSpec("Northern lights", 41, 7, listOf(7, 16, 15), false, "Alex", "Feb 2024", "Abisko and a cold week in Reykjavík.", emptyList()),
        AlbumSpec("Mallorca with the kids", 187, 13, listOf(13, 0, 3), true, "Sam", "Jul 2024", "Beach days and far too much ice cream.", listOf(0, 2, 9)),
        AlbumSpec("Kyoto & Tokyo", 403, 14, listOf(14, 4), false, "Alex", "Apr 2022", "Two weeks of trains, temples and neon.", listOf(1)),
        AlbumSpec("Morocco road trip", 256, 6, listOf(6, 18), true, "Sam", "Nov 2021", "From Marrakech over the Atlas to the dunes at Merzouga.", listOf(0, 4)),
        AlbumSpec("Autumn walks", 64, 12, listOf(12, 2, 22), false, "Alex", "Oct 2025", "Weekend walks in the forest.", emptyList()),
        AlbumSpec("Gotland", 88, 3, listOf(3, 21, 0), false, "Alex", "Jul 2023", "Visby, limestone beaches and sunsets on the west coast.", listOf(5)),
        AlbumSpec("Wildflowers", 52, 19, listOf(19, 8), true, "Sam", "May 2026", "", emptyList()),
        AlbumSpec("Ideas for the 2027 photo book", 0, -1, emptyList(), false, "Alex", "Created today", "Nothing here yet.", emptyList()),
    )

    val MEMORIES = listOf(
        MemorySpec(2023, "3 years ago", "Lofoten", listOf(5, 10, 17, 1), 48, listOf("Rain cleared just in time for the lake", "First morning above Reine", "The view everyone kept talking about", "Last light on the way down")),
        MemorySpec(2025, "1 year ago", "Stockholm archipelago", listOf(9, 20, 3), 31, listOf("Ferry out to the islands", "Swim stop on the rocks", "Evening on the jetty")),
        MemorySpec(2021, "5 years ago", "Abisko", listOf(7, 16, 15), 22, listOf("Minus 24 and worth it", "The lights came out at 23:40", "Snowshoes the next morning")),
    )

    val PINS = listOf(
        PinSpec("Reykjavík", listOf("Reykjavík"), 16f, 14f),
        PinSpec("Lofoten", listOf("Lofoten"), 46f, 13f),
        PinSpec("Abisko", listOf("Abisko"), 57f, 18f),
        PinSpec("Stockholm area", listOf("Stockholm", "Stockholm archipelago", "Dalarna"), 58f, 36f),
        PinSpec("Gotland & Öland", listOf("Gotland", "Öland"), 62f, 47f),
        PinSpec("Dolomites", listOf("Dolomites"), 50f, 63f),
        PinSpec("Lisbon", listOf("Lisbon"), 22f, 74f),
        PinSpec("Mallorca", listOf("Mallorca"), 40f, 77f),
        PinSpec("Merzouga", listOf("Merzouga"), 31f, 90f),
        PinSpec("Kyoto", listOf("Kyoto"), 91f, 52f),
    )

    val SUGGESTIONS = listOf("sunset at the beach 2023", "northern lights", "videos from summer 2025", "Emma", "mountains in Lofoten")

    val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    /** The prototype's seeded PRNG (mulberry32), so the library is identical to the design. */
    class Rng(private var seed: Int) {
        fun next(): Double {
            seed += 0x6D2B79F5
            var t = (seed xor (seed ushr 15)) * (1 or seed)
            t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
        }
    }

    /** Every item, newest first. */
    val items: List<Item> by lazy { generate() }

    private fun generate(): List<Item> {
        val r = Rng(11)
        val out = ArrayList<Item>()
        var id = 0
        for (y in 2026 downTo 2019) {
            for (m in 11 downTo 0) {
                if (y == 2026 && m > 9) continue
                val summer = m in 5..7
                var n = (r.next() * (if (summer) 14 else 8)).toInt() + (if (summer) 6 else 1)
                if (!summer && r.next() < 0.12) n = 0
                data class Raw(val img: Int, val d: Int, val kind: AssetKind, val dur: Int, val ppl: List<Int>, val by: String)
                val month = ArrayList<Raw>()
                repeat(n) {
                    val img = (r.next() * 24).toInt()
                    val kk = r.next()
                    val kind = when {
                        kk < 0.1 -> AssetKind.VIDEO
                        kk < 0.16 -> AssetKind.LIVE
                        kk < 0.2 -> AssetKind.RAW
                        else -> AssetKind.PHOTO
                    }
                    val ppl = ArrayList<Int>()
                    if (r.next() < 0.5) ppl.add((r.next() * 12).toInt())
                    if (r.next() < 0.2) {
                        val q = (r.next() * 12).toInt()
                        if (q !in ppl) ppl.add(q)
                    }
                    val maxD = if (y == 2026 && m == 9) 20 else 28
                    val d = 1 + (r.next() * maxD).toInt()
                    val dur = 45 + (r.next() * 150).toInt()
                    val by = if (r.next() < 0.28) "Sam" else "Alex"
                    month.add(Raw(img, d, kind, dur, ppl, by))
                }
                if (y == 2023 && m == 6) {
                    listOf(0 to 24, 11 to 18, 21 to 9).forEach { (img, d) ->
                        month.add(Raw(img, d, AssetKind.PHOTO, 60, listOf(0), "Alex"))
                    }
                }
                // JS Array.sort is stable, as is sortedByDescending.
                val sorted = month.sortedByDescending { it.d }
                var prevDay = -1
                sorted.forEachIndexed { i, it ->
                    out.add(Item(id++, it.img, y, m, it.d, it.kind, it.dur, it.ppl, it.by, firstOfDay = i == 0 || prevDay != it.d))
                    prevDay = it.d
                }
            }
        }
        return out
    }

    /** Favorites seeded like the prototype. */
    val initialFavorites: Set<Int> by lazy { items.filter { it.id % 7 == 0 && it.id < 300 }.map { it.id }.toSet() }

    fun albumItems(index: Int): List<Item> {
        val a = ALBUMS[index]
        if (a.count == 0) return emptyList()
        return items.filter { it.img in a.imgs }.take(minOf(48, a.count))
    }

    fun personItems(index: Int): List<Item> = items.filter { index in it.people }

    data class PlaceSpec(val name: String, val country: String, val count: Int, val img: Int)

    val places: List<PlaceSpec> by lazy {
        val map = LinkedHashMap<String, PlaceSpec>()
        items.forEach {
            val m = META[it.img]
            val cur = map[m.place]
            map[m.place] = cur?.copy(count = cur.count + 1) ?: PlaceSpec(m.place, m.country, 1, it.img)
        }
        map.values.sortedByDescending { it.count }
    }

    private val STOP = setOf("at", "the", "in", "of", "from", "with", "and", "a", "an", "on", "my", "to", "by", "me", "show", "all")

    data class Filter(val name: String, val icon: String, val options: List<String>)

    val FILTERS = listOf(
        Filter("People", "people", listOf("People") + PEOPLE.take(6)),
        Filter("Places", "pin", listOf("Places", "Lofoten", "Stockholm", "Gotland", "Mallorca", "Abisko", "Kyoto")),
        Filter("Date", "calendar", listOf("Date", "2026", "2025", "2024", "2023", "2022")),
        Filter("Media type", "film", listOf("Media type", "Photos", "Videos")),
        Filter("Camera", "camera", listOf("Camera", "Sony A7 IV", "iPhone 15 Pro", "Pixel 8")),
    )

    /** The prototype's keyword "smart search" over places, tags, people, years, months and media type. */
    fun search(q: String, f: List<Int>): List<Item> {
        val toks = q.lowercase().split(Regex("[^a-z0-9åäöéí]+")).filter { it.isNotEmpty() && it !in STOP }
        val people = PEOPLE.map { it.lowercase() }
        return items.filter { it ->
            val m = META[it.img]
            if (f[0] > 0 && (f[0] - 1) !in it.people) return@filter false
            if (f[1] > 0 && !m.place.startsWith(FILTERS[1].options[f[1]])) return@filter false
            if (f[2] > 0 && it.year.toString() != FILTERS[2].options[f[2]]) return@filter false
            if (f[3] == 1 && it.kind == AssetKind.VIDEO) return@filter false
            if (f[3] == 2 && it.kind != AssetKind.VIDEO) return@filter false
            if (f[4] > 0 && m.camera != FILTERS[4].options[f[4]]) return@filter false
            toks.all { t ->
                when {
                    Regex("^\\d{4}$").matches(t) -> it.year.toString() == t
                    t == "video" || t == "videos" -> it.kind == AssetKind.VIDEO
                    t == "photo" || t == "photos" || t == "pictures" -> it.kind != AssetKind.VIDEO
                    t == "summer" -> it.month in 5..7 || "summer" in m.tags
                    t == "winter" -> it.month == 11 || it.month <= 1 || "winter" in m.tags
                    MONTHS.any { mo -> mo.lowercase() == t } -> it.month == MONTHS.indexOfFirst { mo -> mo.lowercase() == t }
                    t in people -> people.indexOf(t) in it.people
                    m.place.lowercase().contains(t) || m.country.lowercase() == t -> true
                    else -> {
                        val st = t.removeSuffix("s")
                        m.tags.any { tg -> tg == t || tg == st || tg.startsWith(st) }
                    }
                }
            }
        }.take(60)
    }
}
