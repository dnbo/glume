package com.android.glume

import com.android.glume.data.mock.MockLibrary
import com.android.glume.domain.AssetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockLibraryTest {

    @Test
    fun `library is deterministic and newest first`() {
        val items = MockLibrary.items
        assertTrue(items.size > 300)
        val keys = items.map { it.year * 10000 + it.month * 100 + it.day }
        assertEquals(keys.sortedDescending(), keys)
        assertEquals(items.indices.toList(), items.map { it.id })
    }

    @Test
    fun `no items after October 2026`() {
        assertTrue(MockLibrary.items.none { it.year == 2026 && it.month > 9 })
    }

    @Test
    fun `firstOfDay marks the first item of each day within a month`() {
        MockLibrary.items.groupBy { it.year * 12 + it.month }.values.forEach { month ->
            month.forEachIndexed { i, it ->
                assertEquals(i == 0 || month[i - 1].day != it.day, it.firstOfDay)
            }
        }
    }

    @Test
    fun `search by year only returns that year`() {
        val res = MockLibrary.search("2023", List(5) { 0 })
        assertTrue(res.isNotEmpty())
        assertTrue(res.all { it.year == 2023 })
    }

    @Test
    fun `search for videos honours the media type`() {
        val res = MockLibrary.search("videos", List(5) { 0 })
        assertTrue(res.isNotEmpty())
        assertTrue(res.all { it.kind == AssetKind.VIDEO })
    }

    @Test
    fun `nonsense search is empty`() {
        assertTrue(MockLibrary.search("penguins in antarctica", List(5) { 0 }).isEmpty())
    }

    @Test
    fun `empty album has no items`() {
        val i = MockLibrary.ALBUMS.indexOfFirst { it.count == 0 }
        assertTrue(MockLibrary.albumItems(i).isEmpty())
    }
}
