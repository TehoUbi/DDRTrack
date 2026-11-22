package com.tehoubi.ddrtracker.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One recognised line, and where each character sits along it (0..1 of its width). */
data class Recognition(val confidence: Float, val chars: List<Glyph>) {
    data class Glyph(val text: String, val start: Double, val end: Double)

    val text: String get() = chars.joinToString("") { it.text }
}

/**
 * PP-OCR's CTC recogniser. Each line is straightened out of the photo,
 * scaled to 48 px high, and read left to right; the network scores every
 * character in its list at each step, and repeats and blanks are collapsed.
 */
class TextRecognizer(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val batchSize: Int = 6,
) {
    /** Index 0 is the CTC blank; the model's list follows, then a space. */
    private val characters: List<String> = buildList {
        add("")
        val list = session.metadata.customMetadata["character"]
            ?: error("recognition model has no character list in its metadata")
        addAll(list.split('\n'))
        add(" ")
    }

    fun recognize(image: RgbImage, quads: List<Quad>): List<Recognition> {
        val crops = quads.map { straighten(image, it) }
        val order = crops.indices.sortedBy { crops[it].width / crops[it].height.toDouble() }
        val results = arrayOfNulls<Recognition>(crops.size)
        for (batch in order.chunked(batchSize)) {
            val maxRatio = max(IMAGE_W / IMAGE_H.toDouble(), batch.maxOf { crops[it].width / crops[it].height.toDouble() })
            val width = (IMAGE_H * maxRatio).toInt()
            val data = FloatArray(batch.size * 3 * IMAGE_H * width)
            val contentWidths = batch.mapIndexed { b, idx -> fill(crops[idx], data, b, width) }
            val shape = longArrayOf(batch.size.toLong(), 3, IMAGE_H.toLong(), width.toLong())
            OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape).use { tensor ->
                session.run(mapOf(session.inputNames.first() to tensor)).use { out ->
                    val result = out[0] as OnnxTensor
                    val dims = result.info.shape
                    val steps = dims[1].toInt(); val classes = dims[2].toInt()
                    // floatBuffer is already a copy; copying it again into an array would
                    // move batch x steps x 18,710 floats (tens of MB) a second time.
                    val probs = result.floatBuffer
                    batch.forEachIndexed { b, idx ->
                        results[idx] = decode(probs, b * steps * classes, steps, classes, width, contentWidths[b])
                    }
                }
            }
        }
        return results.map { it!! }
    }

    /** The quad cut out and turned level; tall crops are turned on their side, as PaddleOCR does. */
    private fun straighten(image: RgbImage, q: Quad): RgbImage {
        val w = max(1, q.width.roundToInt())
        val h = max(1, q.height.roundToInt())
        // Quad.at(u, v), unrolled: the top and bottom edge points depend only
        // on the column, so they are worked out once per column, not per pixel.
        val topX = DoubleArray(w); val topY = DoubleArray(w)
        val botX = DoubleArray(w); val botY = DoubleArray(w)
        for (x in 0 until w) {
            val u = (x + 0.5) / w
            topX[x] = q.xs[0] + (q.xs[1] - q.xs[0]) * u; topY[x] = q.ys[0] + (q.ys[1] - q.ys[0]) * u
            botX[x] = q.xs[3] + (q.xs[2] - q.xs[3]) * u; botY[x] = q.ys[3] + (q.ys[2] - q.ys[3]) * u
        }
        val px = IntArray(w * h)
        for (y in 0 until h) {
            val v = (y + 0.5) / h
            for (x in 0 until w) {
                val sx = topX[x] + (botX[x] - topX[x]) * v
                val sy = topY[x] + (botY[x] - topY[x]) * v
                px[y * w + x] = image.sample(sx - 0.5, sy - 0.5)
            }
        }
        val crop = RgbImage(w, h, px)
        return if (h >= w * 1.5) crop.rotated(-90) else crop
    }

    /** Writes [crop] scaled to the input height into slot [b], left-aligned; returns its width there. */
    private fun fill(crop: RgbImage, data: FloatArray, b: Int, width: Int): Int {
        val resizedW = min(width, ceil(IMAGE_H * crop.width / crop.height.toDouble()).toInt()).coerceAtLeast(1)
        val scaled = crop.resize(resizedW, IMAGE_H)
        val plane = IMAGE_H * width
        val base = b * 3 * plane
        val unit = UNIT_RANGE
        for (y in 0 until IMAGE_H) for (x in 0 until resizedW) {
            val p = scaled.argb[y * resizedW + x]
            val i = y * width + x
            data[base + i] = unit[p and 0xFF]
            data[base + plane + i] = unit[(p shr 8) and 0xFF]
            data[base + 2 * plane + i] = unit[(p shr 16) and 0xFF]
        }
        return resizedW
    }

    /**
     * Greedy CTC: the most likely character at each step, repeats and blanks
     * collapsed. The model ends in a softmax, so each step's scores sum to 1
     * (RecognizerOutputTest checks this); once one is past [SURE] no other can
     * beat it and the rest of the 18,710 are skipped. Most steps are blank and
     * stop at the first score.
     */
    private fun decode(probs: FloatBuffer, offset: Int, steps: Int, classes: Int, inputWidth: Int, contentWidth: Int): Recognition {
        val chars = mutableListOf<Recognition.Glyph>()
        var confSum = 0.0
        var prev = -1
        // Each output step covers an equal slice of the padded input.
        val stepWidth = inputWidth / steps.toDouble()
        for (t in 0 until steps) {
            val row = offset + t * classes
            var best = 0
            var bestP = probs.get(row)
            var c = 1
            while (bestP < SURE && c < classes) {
                val p = probs.get(row + c)
                if (p > bestP) { bestP = p; best = c }
                c++
            }
            val end = ((t + 1) * stepWidth / contentWidth).coerceIn(0.0, 1.0)
            if (best != 0 && best != prev) {
                chars += Recognition.Glyph(characters.getOrElse(best) { "" }, (t * stepWidth / contentWidth).coerceIn(0.0, 1.0), end)
                confSum += bestP
            } else if (best != 0 && chars.isNotEmpty()) {
                chars[chars.size - 1] = chars.last().copy(end = end)
            }
            prev = best
        }
        val conf = if (chars.isEmpty()) 0f else (confSum / chars.size).toFloat()
        return Recognition(conf, chars)
    }

    private companion object {
        const val IMAGE_H = 48
        const val IMAGE_W = 320
        /** Past half, with room for the softmax's rounding (its sums are within 1e-5 of 1). */
        const val SURE = 0.501f
    }
}
