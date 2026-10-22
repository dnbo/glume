package com.android.glume.ui.nav

import com.android.glume.domain.Asset
import com.android.glume.domain.Memory

enum class SlideMode { NORMAL, MEMORY, PARTY, AMBIENT }

data class SlideshowRequest(
    val assets: List<Asset>,
    val mode: SlideMode,
    val start: Int = 0,
    val memory: Memory? = null,
    val title: String = "",
    /** Keep the given order even if Shuffle is on (slideshow "from here", memories). */
    val keepOrder: Boolean = false,
    /** Settings source label for the slideshow panel ("Favorites", "Album: …"). */
    val sourceLabel: String? = null,
)

/**
 * Hands asset lists to the viewer and slideshow without serializing them into routes. Entries live
 * as long as the process; the app is single-activity and lists are small, so no eviction is needed
 * beyond a size cap.
 */
object MediaQueue {
    private var next = 1L
    private val lists = LinkedHashMap<Long, List<Asset>>()
    private val slides = LinkedHashMap<Long, SlideshowRequest>()

    @Synchronized
    fun put(list: List<Asset>): Long {
        val t = next++
        lists[t] = list
        if (lists.size > 32) lists.remove(lists.keys.first())
        return t
    }

    @Synchronized
    fun get(token: Long): List<Asset> = lists[token].orEmpty()

    @Synchronized
    fun putSlideshow(req: SlideshowRequest): Long {
        val t = next++
        slides[t] = req
        if (slides.size > 16) slides.remove(slides.keys.first())
        return t
    }

    @Synchronized
    fun slideshow(token: Long): SlideshowRequest? = slides[token]
}
