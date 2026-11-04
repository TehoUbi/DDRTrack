package com.tehoubi.ddrtracker.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A text line's rotated rectangle: corners top-left, top-right, bottom-right, bottom-left. */
data class Quad(val xs: DoubleArray, val ys: DoubleArray) {
    val width: Double get() = hypot(xs[1] - xs[0], ys[1] - ys[0])
    val height: Double get() = hypot(xs[3] - xs[0], ys[3] - ys[0])

    /** Slope of the top edge in degrees, positive when it runs down to the right. */
    val angle: Float get() = Math.toDegrees(atan2(ys[1] - ys[0], xs[1] - xs[0])).toFloat()

    /** The point a fraction [u] along the text and [v] down it. */
    fun at(u: Double, v: Double): Pair<Double, Double> {
        val topX = xs[0] + (xs[1] - xs[0]) * u; val topY = ys[0] + (ys[1] - ys[0]) * u
        val botX = xs[3] + (xs[2] - xs[3]) * u; val botY = ys[3] + (ys[2] - ys[3]) * u
        return (topX + (botX - topX) * v) to (topY + (botY - topY) * v)
    }

    override fun equals(other: Any?) = other is Quad && xs.contentEquals(other.xs) && ys.contentEquals(other.ys)
    override fun hashCode() = 31 * xs.contentHashCode() + ys.contentHashCode()
}

/**
 * PP-OCR's DB text detector: the network marks pixels that belong to text,
 * and each connected region becomes a rotated rectangle, grown back out by
 * the margin the network was trained to shrink it by.
 *
 * Mirrors PaddleOCR's DBPostProcess (threshold 0.3, 2x2 dilation, box score
 * 0.5, unclip ratio 1.6), with connected regions standing in for OpenCV
 * contours so nothing native beyond ONNX Runtime is needed.
 */
class TextDetector(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val thresh: Float = 0.3f,
    private val boxThresh: Float = 0.5f,
    private val unclipRatio: Double = 1.6,
    private val maxCandidates: Int = 1000,
) {
    /**
     * Text lines in [image]. The network sees the image scaled so its short
     * side is at least [minSide] and its long side at most [maxSide], in
     * multiples of 32.
     */
    fun detect(image: RgbImage, minSide: Int = 736, maxSide: Int = 2560): List<Quad> {
        var ratio = 1.0
        val short = min(image.width, image.height)
        if (short < minSide) ratio = minSide / short.toDouble()
        val long = max(image.width, image.height)
        if (long * ratio > maxSide) ratio = maxSide / long.toDouble()
        val w = max(32, (image.width * ratio / 32).roundToInt() * 32)
        val h = max(32, (image.height * ratio / 32).roundToInt() * 32)
        val input = image.resize(w, h)

        val chw = FloatBuffer.allocate(3 * w * h)
        val plane = w * h
        val data = chw.array()
        for (i in 0 until plane) {
            val p = input.argb[i]
            // BGR order, scaled to [-1, 1], as PaddleOCR feeds OpenCV images.
            data[i] = (p and 0xFF) / 127.5f - 1f
            data[plane + i] = ((p shr 8) and 0xFF) / 127.5f - 1f
            data[2 * plane + i] = ((p shr 16) and 0xFF) / 127.5f - 1f
        }
        val prob = OnnxTensor.createTensor(env, chw, longArrayOf(1, 3, h.toLong(), w.toLong())).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { out ->
                val result = out[0] as OnnxTensor
                val buffer = result.floatBuffer
                FloatArray(buffer.remaining()).also { buffer.get(it) }
            }
        }
        return boxes(prob, w, h, image.width / w.toDouble(), image.height / h.toDouble())
    }

    private fun boxes(prob: FloatArray, w: Int, h: Int, sx: Double, sy: Double): List<Quad> {
        val raw = BooleanArray(w * h) { prob[it] > thresh }
        // 2x2 dilation (OpenCV's anchor sits at the kernel's bottom-right).
        val mask = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            mask[i] = raw[i] || (x > 0 && raw[i - 1]) || (y > 0 && raw[i - w]) || (x > 0 && y > 0 && raw[i - w - 1])
        }

        val quads = mutableListOf<Quad>()
        for (region in regions(mask, w, h).take(maxCandidates)) {
            val rect = minAreaRect(region) ?: continue
            if (min(rect.w, rect.h) < MIN_SIZE) continue
            if (meanInside(prob, w, h, rect.corners()) < boxThresh) continue
            // Unclip: grow every side by area * ratio / perimeter.
            val d = rect.w * rect.h * unclipRatio / (2 * (rect.w + rect.h))
            val grown = rect.copy(w = rect.w + 2 * d, h = rect.h + 2 * d)
            if (min(grown.w, grown.h) < MIN_SIZE + 2) continue
            val c = order(grown.corners())
            val xs = DoubleArray(4) { (c[it].first * sx).coerceIn(0.0, w * sx - 1) }
            val ys = DoubleArray(4) { (c[it].second * sy).coerceIn(0.0, h * sy - 1) }
            val q = Quad(xs, ys)
            if (q.width <= 3 || q.height <= 3) continue
            quads += q
        }
        return quads
    }

    /**
     * Outline points of each 8-connected region: for every row it covers,
     * the left- and right-most pixel centres, which is all the convex hull
     * (and so the rotated rectangle) depends on.
     */
    private fun regions(mask: BooleanArray, w: Int, h: Int): List<List<Pair<Double, Double>>> {
        val label = IntArray(w * h)
        val out = mutableListOf<List<Pair<Double, Double>>>()
        val stack = IntArray(w * h)
        var next = 0
        for (start in mask.indices) {
            if (!mask[start] || label[start] != 0) continue
            next++
            val rowMin = HashMap<Int, Int>()
            val rowMax = HashMap<Int, Int>()
            var top = 0
            stack[top++] = start
            label[start] = next
            while (top > 0) {
                val i = stack[--top]
                val x = i % w; val y = i / w
                rowMin[y] = min(rowMin[y] ?: x, x)
                rowMax[y] = max(rowMax[y] ?: x, x)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (mask[j] && label[j] == 0) { label[j] = next; stack[top++] = j }
                }
            }
            out += rowMin.keys.sorted().flatMap { y ->
                listOf(rowMin.getValue(y).toDouble() to y.toDouble(), rowMax.getValue(y).toDouble() to y.toDouble())
            }
        }
        return out
    }

    internal data class Rect(val cx: Double, val cy: Double, val w: Double, val h: Double, val theta: Double) {
        fun corners(): List<Pair<Double, Double>> {
            val c = cos(theta); val s = sin(theta)
            return listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).map { (a, b) ->
                val dx = a * w / 2; val dy = b * h / 2
                (cx + dx * c - dy * s) to (cy + dx * s + dy * c)
            }
        }
    }

    /** Smallest rectangle around [points], by rotating calipers over the convex hull. */
    internal fun minAreaRect(points: List<Pair<Double, Double>>): Rect? {
        val hull = convexHull(points)
        if (hull.size < 3) return null
        var best: Rect? = null
        var bestArea = Double.MAX_VALUE
        for (i in hull.indices) {
            val (x0, y0) = hull[i]
            val (x1, y1) = hull[(i + 1) % hull.size]
            val theta = atan2(y1 - y0, x1 - x0)
            val c = cos(theta); val s = sin(theta)
            var minU = Double.MAX_VALUE; var maxU = -Double.MAX_VALUE
            var minV = Double.MAX_VALUE; var maxV = -Double.MAX_VALUE
            for ((px, py) in hull) {
                val u = px * c + py * s
                val v = -px * s + py * c
                minU = min(minU, u); maxU = max(maxU, u)
                minV = min(minV, v); maxV = max(maxV, v)
            }
            val area = (maxU - minU) * (maxV - minV)
            if (area < bestArea) {
                bestArea = area
                val mu = (minU + maxU) / 2; val mv = (minV + maxV) / 2
                best = Rect(mu * c - mv * s, mu * s + mv * c, maxU - minU, maxV - minV, theta)
            }
        }
        return best
    }

    private fun convexHull(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val sorted = points.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (sorted.size < 3) return sorted
        fun cross(o: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = mutableListOf<Pair<Double, Double>>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.size - 1)
            lower += p
        }
        val upper = mutableListOf<Pair<Double, Double>>()
        for (p in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.size - 1)
            upper += p
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    /** Mean probability inside the quad: how sure the network is this is text. */
    private fun meanInside(prob: FloatArray, w: Int, h: Int, corners: List<Pair<Double, Double>>): Double {
        val x0 = corners.minOf { it.first }.toInt().coerceIn(0, w - 1)
        val x1 = kotlin.math.ceil(corners.maxOf { it.first }).toInt().coerceIn(0, w - 1)
        val y0 = corners.minOf { it.second }.toInt().coerceIn(0, h - 1)
        val y1 = kotlin.math.ceil(corners.maxOf { it.second }).toInt().coerceIn(0, h - 1)
        var sum = 0.0; var n = 0
        for (y in y0..y1) for (x in x0..x1) {
            if (inside(corners, x.toDouble(), y.toDouble())) { sum += prob[y * w + x]; n++ }
        }
        return if (n == 0) 0.0 else sum / n
    }

    private fun inside(c: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
        var sign = 0
        for (i in c.indices) {
            val (ax, ay) = c[i]; val (bx, by) = c[(i + 1) % c.size]
            val cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
            if (abs(cross) < 1e-9) continue
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    /** Corners as top-left, top-right, bottom-right, bottom-left. */
    private fun order(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val byX = points.sortedBy { it.first }
        val (tl, bl) = byX.take(2).sortedBy { it.second }
        val (tr, br) = byX.drop(2).sortedBy { it.second }
        return listOf(tl, tr, br, bl)
    }

    private companion object {
        const val MIN_SIZE = 3.0
    }
}
