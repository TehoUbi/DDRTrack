package com.tehoubi.ddrtracker.ocr

import com.tehoubi.ddrtracker.core.ocr.Box
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * An image as packed ARGB pixels, row by row. Android's Bitmap and the JVM's
 * BufferedImage both convert to and from this, so everything in this module
 * runs the same on a phone and in tests.
 */
class RgbImage(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(width > 0 && height > 0 && argb.size == width * height) { "bad image ${width}x$height" }
    }

    operator fun get(x: Int, y: Int): Int = argb[y * width + x]

    fun crop(box: Box): RgbImage {
        val left = box.left.coerceIn(0, width - 1)
        val top = box.top.coerceIn(0, height - 1)
        val right = box.right.coerceIn(left + 1, width)
        val bottom = box.bottom.coerceIn(top + 1, height)
        val w = right - left
        val out = IntArray(w * (bottom - top))
        for (y in top until bottom) System.arraycopy(argb, y * width + left, out, (y - top) * w, w)
        return RgbImage(w, bottom - top, out)
    }

    /**
     * Resized to [w] x [h]. Shrinking averages each output pixel's whole
     * footprint (a photo scaled down 2-3x by point sampling loses the thin
     * strokes of small digits); enlarging is bilinear.
     */
    fun resize(w: Int, h: Int): RgbImage {
        if (w == width && h == height) return this
        return if (w <= width && h <= height) areaAverage(w, h) else bilinear(w, h)
    }

    fun scaled(scale: Float): RgbImage =
        if (scale == 1f) this
        else resize(max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()))

    /** The image turned [degrees] clockwise (a multiple of 90). */
    fun rotated(degrees: Int): RgbImage {
        val turns = ((degrees / 90) % 4 + 4) % 4
        if (turns == 0) return this
        val w = if (turns % 2 == 0) width else height
        val h = if (turns % 2 == 0) height else width
        val out = IntArray(w * h)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val target = when (turns) {
                    1 -> x * w + (height - 1 - y)
                    2 -> (height - 1 - y) * w + (width - 1 - x)
                    else -> (width - 1 - x) * w + y
                }
                out[target] = argb[row + x]
            }
        }
        return RgbImage(w, h, out)
    }

    private fun areaAverage(w: Int, h: Int): RgbImage {
        val cols = Footprint(width, w)
        val rows = Footprint(height, h)
        val out = IntArray(w * h)
        for (oy in 0 until h) {
            val y0 = rows.start[oy]
            val yWeights = rows.weights[oy]
            for (ox in 0 until w) {
                val x0 = cols.start[ox]
                val xWeights = cols.weights[ox]
                var r = 0.0; var g = 0.0; var b = 0.0; var total = 0.0
                for (j in yWeights.indices) {
                    val wy = yWeights[j]
                    val row = (y0 + j) * width + x0
                    for (i in xWeights.indices) {
                        val wgt = wy * xWeights[i]
                        val p = argb[row + i]
                        r += wgt * ((p shr 16) and 0xFF)
                        g += wgt * ((p shr 8) and 0xFF)
                        b += wgt * (p and 0xFF)
                        total += wgt
                    }
                }
                out[oy * w + ox] = pack(r / total, g / total, b / total)
            }
        }
        return RgbImage(w, h, out)
    }

    /**
     * Which source pixels each of [outSize] output pixels covers along one
     * axis, and by how much. Worked out once per axis rather than per pixel.
     */
    private class Footprint(inSize: Int, outSize: Int) {
        val start = IntArray(outSize)
        val weights = Array(outSize) { DoubleArray(0) }

        init {
            val scale = inSize / outSize.toDouble()
            for (o in 0 until outSize) {
                val a = o * scale
                val b = a + scale
                val first = floor(a).toInt()
                val end = min(ceil(b).toInt(), inSize)
                start[o] = first
                weights[o] = DoubleArray(max(0, end - first)) { k ->
                    val p = first + k
                    min(b, p + 1.0) - max(a, p.toDouble())
                }
            }
        }
    }

    private fun bilinear(w: Int, h: Int): RgbImage {
        val out = IntArray(w * h)
        for (oy in 0 until h) {
            val fy = ((oy + 0.5) * height / h - 0.5).coerceIn(0.0, height - 1.0)
            for (ox in 0 until w) {
                val fx = ((ox + 0.5) * width / w - 0.5).coerceIn(0.0, width - 1.0)
                out[oy * w + ox] = sample(fx, fy)
            }
        }
        return RgbImage(w, h, out)
    }

    /** Bilinear sample at (x, y), clamped to the edge. */
    fun sample(x: Double, y: Double): Int {
        val cx = x.coerceIn(0.0, width - 1.0)
        val cy = y.coerceIn(0.0, height - 1.0)
        val x0 = cx.toInt(); val y0 = cy.toInt()
        val x1 = min(x0 + 1, width - 1); val y1 = min(y0 + 1, height - 1)
        val ax = cx - x0; val ay = cy - y0
        val p00 = argb[y0 * width + x0]; val p10 = argb[y0 * width + x1]
        val p01 = argb[y1 * width + x0]; val p11 = argb[y1 * width + x1]
        return pack(lerp(p00, p10, p01, p11, 16, ax, ay), lerp(p00, p10, p01, p11, 8, ax, ay), lerp(p00, p10, p01, p11, 0, ax, ay))
    }

    // A plain function rather than a closure: this runs once per pixel of every text line.
    private fun lerp(p00: Int, p10: Int, p01: Int, p11: Int, shift: Int, ax: Double, ay: Double): Double {
        val top = ((p00 shr shift) and 0xFF) * (1 - ax) + ((p10 shr shift) and 0xFF) * ax
        val bottom = ((p01 shr shift) and 0xFF) * (1 - ax) + ((p11 shr shift) and 0xFF) * ax
        return top * (1 - ay) + bottom * ay
    }

    private fun pack(r: Double, g: Double, b: Double): Int =
        (0xFF shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
}
