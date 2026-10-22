package com.android.glume.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ThumbHash (https://evanw.github.io/thumbhash/, MIT, Evan Wallace) ported to Kotlin.
 *
 * Immich returns a base64 ThumbHash per asset. [toRgba] decodes it to a ~32 px image that we draw
 * stretched as the placeholder (bilinear upscaling gives the blur for free). [encode] exists only so
 * the mock repository can produce realistic hashes for bundled sample photos.
 */
object ThumbHash {

    class Image(val width: Int, val height: Int, val rgba: ByteArray)

    fun toRgba(hash: ByteArray): Image {
        val header24 = u(hash[0]) or (u(hash[1]) shl 8) or (u(hash[2]) shl 16)
        val header16 = u(hash[3]) or (u(hash[4]) shl 8)
        val lDc = (header24 and 63) / 63f
        val pDc = ((header24 shr 6) and 63) / 31.5f - 1f
        val qDc = ((header24 shr 12) and 63) / 31.5f - 1f
        val lScale = ((header24 shr 18) and 31) / 31f
        val hasAlpha = (header24 shr 23) != 0
        val pScale = ((header16 shr 3) and 63) / 63f
        val qScale = ((header16 shr 9) and 63) / 63f
        val isLandscape = (header16 shr 15) != 0
        val lx = max(3, if (isLandscape) (if (hasAlpha) 5 else 7) else header16 and 7)
        val ly = max(3, if (isLandscape) header16 and 7 else (if (hasAlpha) 5 else 7))
        val aDc = if (hasAlpha) (u(hash[5]) and 15) / 15f else 1f
        val aScale = if (hasAlpha) (u(hash[5]) shr 4) / 15f else 0f

        val acStart = if (hasAlpha) 6 else 5
        var acIndex = 0
        fun decodeChannel(nx: Int, ny: Int, scale: Float): FloatArray {
            val ac = ArrayList<Float>()
            for (cy in 0 until ny) {
                var cx = if (cy > 0) 0 else 1
                while (cx * ny < nx * (ny - cy)) {
                    val nibble = (u(hash[acStart + (acIndex shr 1)]) shr ((acIndex and 1) shl 2)) and 15
                    ac.add((nibble / 7.5f - 1f) * scale)
                    acIndex++
                    cx++
                }
            }
            return ac.toFloatArray()
        }
        val lAc = decodeChannel(lx, ly, lScale)
        val pAc = decodeChannel(3, 3, pScale * 1.25f)
        val qAc = decodeChannel(3, 3, qScale * 1.25f)
        val aAc = if (hasAlpha) decodeChannel(5, 5, aScale) else FloatArray(0)

        val ratio = aspectRatio(hash)
        val w = (if (ratio > 1f) 32f else 32f * ratio).roundToInt()
        val h = (if (ratio > 1f) 32f / ratio else 32f).roundToInt()
        val rgba = ByteArray(w * h * 4)
        val fx = FloatArray(max(lx, if (hasAlpha) 5 else 3))
        val fy = FloatArray(max(ly, if (hasAlpha) 5 else 3))
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                var l = lDc
                var p = pDc
                var q = qDc
                var a = aDc
                for (cx in fx.indices) fx[cx] = cos(PI / w * (x + 0.5) * cx).toFloat()
                for (cy in fy.indices) fy[cy] = cos(PI / h * (y + 0.5) * cy).toFloat()

                var j = 0
                for (cy in 0 until ly) {
                    var cx = if (cy > 0) 0 else 1
                    val fy2 = fy[cy] * 2f
                    while (cx * ly < lx * (ly - cy)) {
                        l += lAc[j] * fx[cx] * fy2
                        cx++; j++
                    }
                }
                j = 0
                for (cy in 0 until 3) {
                    var cx = if (cy > 0) 0 else 1
                    val fy2 = fy[cy] * 2f
                    while (cx < 3 - cy) {
                        val f = fx[cx] * fy2
                        p += pAc[j] * f
                        q += qAc[j] * f
                        cx++; j++
                    }
                }
                if (hasAlpha) {
                    j = 0
                    for (cy in 0 until 5) {
                        var cx = if (cy > 0) 0 else 1
                        val fy2 = fy[cy] * 2f
                        while (cx < 5 - cy) {
                            a += aAc[j] * fx[cx] * fy2
                            cx++; j++
                        }
                    }
                }
                val b = l - 2f / 3f * p
                val r = (3f * l - b + q) / 2f
                val g = r - q
                rgba[i] = channel(r)
                rgba[i + 1] = channel(g)
                rgba[i + 2] = channel(b)
                rgba[i + 3] = channel(a)
                i += 4
            }
        }
        return Image(w, h, rgba)
    }

    fun aspectRatio(hash: ByteArray): Float {
        val header = u(hash[3])
        val hasAlpha = (u(hash[2]) and 0x80) != 0
        val isLandscape = (u(hash[4]) and 0x80) != 0
        val lx = if (isLandscape) (if (hasAlpha) 5 else 7) else header and 7
        val ly = if (isLandscape) header and 7 else (if (hasAlpha) 5 else 7)
        return lx.toFloat() / ly.toFloat()
    }

    /** Encodes an RGBA image of at most 100 x 100 px. */
    fun encode(w: Int, h: Int, rgba: ByteArray): ByteArray {
        require(w <= 100 && h <= 100) { "${w}x$h doesn't fit in 100x100" }
        var avgR = 0.0
        var avgG = 0.0
        var avgB = 0.0
        var avgA = 0.0
        for (i in 0 until w * h) {
            val j = i * 4
            val alpha = u(rgba[j + 3]) / 255.0
            avgR += alpha / 255.0 * u(rgba[j])
            avgG += alpha / 255.0 * u(rgba[j + 1])
            avgB += alpha / 255.0 * u(rgba[j + 2])
            avgA += alpha
        }
        if (avgA > 0) {
            avgR /= avgA; avgG /= avgA; avgB /= avgA
        }
        val hasAlpha = avgA < w * h
        val lLimit = if (hasAlpha) 5 else 7
        val lx = max(1, (lLimit.toDouble() * w / max(w, h)).roundToInt())
        val ly = max(1, (lLimit.toDouble() * h / max(w, h)).roundToInt())
        val l = DoubleArray(w * h)
        val p = DoubleArray(w * h)
        val q = DoubleArray(w * h)
        val a = DoubleArray(w * h)
        for (i in 0 until w * h) {
            val j = i * 4
            val alpha = u(rgba[j + 3]) / 255.0
            val r = avgR * (1 - alpha) + alpha / 255.0 * u(rgba[j])
            val g = avgG * (1 - alpha) + alpha / 255.0 * u(rgba[j + 1])
            val b = avgB * (1 - alpha) + alpha / 255.0 * u(rgba[j + 2])
            l[i] = (r + g + b) / 3
            p[i] = (r + g) / 2 - b
            q[i] = r - g
            a[i] = alpha
        }

        class Channel(val dc: Double, val ac: DoubleArray, val scale: Double)

        fun encodeChannel(channel: DoubleArray, nx: Int, ny: Int): Channel {
            var dc = 0.0
            val ac = ArrayList<Double>()
            var scale = 0.0
            val fx = DoubleArray(w)
            for (cy in 0 until ny) {
                var cx = 0
                while (cx * ny < nx * (ny - cy)) {
                    var f = 0.0
                    for (x in 0 until w) fx[x] = cos(PI / w * cx * (x + 0.5))
                    for (y in 0 until h) {
                        val fy = cos(PI / h * cy * (y + 0.5))
                        for (x in 0 until w) f += channel[x + y * w] * fx[x] * fy
                    }
                    f /= (w * h)
                    if (cx > 0 || cy > 0) {
                        ac.add(f)
                        scale = max(scale, abs(f))
                    } else {
                        dc = f
                    }
                    cx++
                }
            }
            val out = ac.toDoubleArray()
            if (scale > 0) for (i in out.indices) out[i] = 0.5 + 0.5 / scale * out[i]
            return Channel(dc, out, scale)
        }

        val lc = encodeChannel(l, max(3, lx), max(3, ly))
        val pc = encodeChannel(p, 3, 3)
        val qc = encodeChannel(q, 3, 3)
        val ac = if (hasAlpha) encodeChannel(a, 5, 5) else null

        val isLandscape = w > h
        val header24 = (63 * lc.dc).roundToInt() or
            ((31.5 + 31.5 * pc.dc).roundToInt() shl 6) or
            ((31.5 + 31.5 * qc.dc).roundToInt() shl 12) or
            ((31 * lc.scale).roundToInt() shl 18) or
            ((if (hasAlpha) 1 else 0) shl 23)
        val header16 = (if (isLandscape) ly else lx) or
            ((63 * pc.scale).roundToInt() shl 3) or
            ((63 * qc.scale).roundToInt() shl 9) or
            ((if (isLandscape) 1 else 0) shl 15)
        val channels = if (ac != null) listOf(lc.ac, pc.ac, qc.ac, ac.ac) else listOf(lc.ac, pc.ac, qc.ac)
        val acCount = channels.sumOf { it.size }
        val acStart = if (hasAlpha) 6 else 5
        val hash = ByteArray(acStart + (acCount + 1) / 2)
        hash[0] = (header24 and 255).toByte()
        hash[1] = ((header24 shr 8) and 255).toByte()
        hash[2] = (header24 shr 16).toByte()
        hash[3] = (header16 and 255).toByte()
        hash[4] = (header16 shr 8).toByte()
        if (ac != null) hash[5] = ((15 * ac.dc).roundToInt() or ((15 * ac.scale).roundToInt() shl 4)).toByte()
        var acIndex = 0
        for (ch in channels) for (f in ch) {
            val k = acStart + (acIndex shr 1)
            hash[k] = (u(hash[k]) or ((15 * f).roundToInt() shl ((acIndex and 1) shl 2))).toByte()
            acIndex++
        }
        return hash
    }

    private fun u(b: Byte) = b.toInt() and 0xFF
    private fun channel(v: Float): Byte = (max(0f, 255f * min(1f, v))).toInt().toByte()
}
