package com.android.glume

import com.android.glume.util.ThumbHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ThumbHashTest {

    private fun solid(w: Int, h: Int, r: Int, g: Int, b: Int) = ByteArray(w * h * 4) { i ->
        when (i % 4) { 0 -> r; 1 -> g; 2 -> b; else -> 255 }.toByte()
    }

    @Test
    fun `solid color round-trips to roughly the same color`() {
        val hash = ThumbHash.encode(40, 30, solid(40, 30, 200, 120, 40))
        val img = ThumbHash.toRgba(hash)
        val mid = (img.height / 2 * img.width + img.width / 2) * 4
        val r = img.rgba[mid].toInt() and 0xFF
        val g = img.rgba[mid + 1].toInt() and 0xFF
        val b = img.rgba[mid + 2].toInt() and 0xFF
        assertTrue("r=$r", abs(r - 200) < 20)
        assertTrue("g=$g", abs(g - 120) < 20)
        assertTrue("b=$b", abs(b - 40) < 20)
    }

    @Test
    fun `landscape aspect ratio is preserved approximately`() {
        val hash = ThumbHash.encode(90, 60, solid(90, 60, 10, 20, 30))
        assertEquals(1.5f, ThumbHash.aspectRatio(hash), 0.3f)
        val img = ThumbHash.toRgba(hash)
        assertEquals(32, img.width)
        assertTrue(img.height in 18..24)
    }

    @Test
    fun `opaque images produce a 25 byte hash`() {
        // 5 header bytes + (7x? luminance + 2 x 5 chroma AC terms) nibbles for a landscape image.
        val hash = ThumbHash.encode(100, 75, solid(100, 75, 0, 0, 0))
        assertTrue(hash.size in 20..28)
    }
}
