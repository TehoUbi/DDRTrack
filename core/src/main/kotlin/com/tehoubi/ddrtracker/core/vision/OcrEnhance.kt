package com.tehoubi.ddrtracker.core.vision

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Prepares a photo for a second OCR pass when the first one misses fields.
 *
 * Cabinet screens photographed in an arcade are low-contrast (a dim LCD
 * behind a glossy panel) and colour-heavy (judgment labels in gradients).
 * Converting to luminance and stretching the 1st-99th percentile range to
 * full scale makes the glyph edges much crisper for the recogniser.
 */
object OcrEnhance {

    /** ARGB pixels in, opaque grayscale ARGB pixels out. */
    fun enhance(argb: IntArray, lowPercentile: Double = 0.01, highPercentile: Double = 0.99, gamma: Double = 0.9): IntArray {
        if (argb.isEmpty()) return argb
        val luma = IntArray(argb.size) { i ->
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Rec. 601 weights in fixed point.
            (299 * r + 587 * g + 114 * b) / 1000
        }
        val histogram = IntArray(256)
        luma.forEach { histogram[it]++ }
        val lo = percentile(histogram, argb.size, lowPercentile)
        val hi = percentile(histogram, argb.size, highPercentile).coerceAtLeast(lo + 1)

        val lut = IntArray(256) { v ->
            val t = ((v - lo).toDouble() / (hi - lo)).coerceIn(0.0, 1.0)
            (255 * t.pow(gamma)).roundToInt()
        }
        return IntArray(argb.size) { i ->
            val v = lut[luma[i]]
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    private fun percentile(histogram: IntArray, total: Int, p: Double): Int {
        val target = (total * p).toLong()
        var seen = 0L
        histogram.forEachIndexed { value, count ->
            seen += count
            if (seen > target) return value
        }
        return 255
    }

    /** Downscale factor that keeps the long side at or under [maxSide] pixels. */
    fun scaleFor(width: Int, height: Int, maxSide: Int = 2560): Float {
        val longest = maxOf(width, height)
        return if (longest <= maxSide) 1f else maxSide.toFloat() / longest
    }
}
