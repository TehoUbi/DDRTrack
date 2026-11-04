package com.tehoubi.ddrtracker.ocr

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.tehoubi.ddrtracker.core.ocr.Box
import com.tehoubi.ddrtracker.core.ocr.OcrElement
import com.tehoubi.ddrtracker.core.ocr.OcrLine
import com.tehoubi.ddrtracker.core.ocr.OcrPage
import java.io.Closeable
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Reads text with PaddleOCR's PP-OCRv6 models on ONNX Runtime: detection
 * finds the lines, recognition reads each one. One model covers Japanese
 * (kana and kanji) and Latin, so song titles in either come out as written.
 *
 * Sessions are expensive to create (the models are about 30 MB); make one
 * instance and reuse it. Not safe for concurrent use.
 */
class PaddleOcr(detModel: ByteArray, recModel: ByteArray, threads: Int = defaultThreads()) : Closeable {

    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    private val detSession = env.createSession(detModel, options)
    private val recSession = env.createSession(recModel, options)
    private val detector = TextDetector(env, detSession)
    private val recognizer = TextRecognizer(env, recSession)

    /**
     * Every line of text in [image], in page coordinates. The detector sees
     * the image scaled up to at least [minSide] on its short side (small
     * images have small text) and down to at most [maxSide] on its long side.
     */
    fun read(image: RgbImage, minSide: Int = 736, maxSide: Int = 2560): OcrPage {
        val quads = detector.detect(image, minSide = minSide, maxSide = maxSide)
        val texts = recognizer.recognize(image, quads)
        val lines = quads.zip(texts)
            .filter { (_, r) -> r.confidence >= MIN_CONFIDENCE && r.text.isNotBlank() }
            .map { (q, r) -> toLine(q, r) }
            .sortedWith(compareBy({ it.box.top / LINE_BAND }, { it.box.left }))
        return OcrPage(image.width, image.height, lines)
    }

    /** A line with one element per space-separated word, boxed from where its characters were read. */
    private fun toLine(q: Quad, r: Recognition): OcrLine {
        val words = mutableListOf<List<Recognition.Glyph>>()
        var current = mutableListOf<Recognition.Glyph>()
        for (g in r.chars) {
            if (g.text.isBlank()) {
                if (current.isNotEmpty()) words += current
                current = mutableListOf()
            } else current += g
        }
        if (current.isNotEmpty()) words += current

        val elements = words.map { glyphs ->
            val text = glyphs.joinToString("") { it.text }
            OcrElement(text, bounds(q, glyphs.first().start, glyphs.last().end), r.confidence)
        }
        val text = words.joinToString(" ") { w -> w.joinToString("") { it.text } }
        return OcrLine(text, bounds(q, 0.0, 1.0), elements, r.confidence, q.angle)
    }

    /** Axis-aligned box around the part of [q] from [u0] to [u1] along the text. */
    private fun bounds(q: Quad, u0: Double, u1: Double): Box {
        val pts = listOf(q.at(u0, 0.0), q.at(u1, 0.0), q.at(u1, 1.0), q.at(u0, 1.0))
        return Box(
            floor(pts.minOf { it.first }).toInt(), floor(pts.minOf { it.second }).toInt(),
            ceil(pts.maxOf { it.first }).toInt(), ceil(pts.maxOf { it.second }).toInt(),
        )
    }

    override fun close() {
        detSession.close()
        recSession.close()
        options.close()
    }

    companion object {
        const val DET_MODEL = "PP-OCRv6_det_small.onnx"
        const val REC_MODEL = "PP-OCRv6_rec_small.onnx"

        /** Lines read with less confidence than this are noise (PaddleOCR's default cut-off). */
        private const val MIN_CONFIDENCE = 0.5f
        /** Lines whose tops are this close sort left to right. */
        private const val LINE_BAND = 10

        fun fromDirectory(dir: File, threads: Int = defaultThreads()) =
            PaddleOcr(File(dir, DET_MODEL).readBytes(), File(dir, REC_MODEL).readBytes(), threads)

        private fun defaultThreads() = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }
}
