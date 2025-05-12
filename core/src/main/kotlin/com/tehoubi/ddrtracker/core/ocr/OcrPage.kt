package com.tehoubi.ddrtracker.core.ocr

import kotlin.math.max
import kotlin.math.min

/** Axis-aligned bounding box in image pixels. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /** Vertical overlap as a fraction of the shorter box's height. */
    fun verticalOverlap(other: Box): Float {
        val overlap = min(bottom, other.bottom) - max(top, other.top)
        val shorter = min(height, other.height).coerceAtLeast(1)
        return overlap.coerceAtLeast(0) / shorter.toFloat()
    }

    fun union(other: Box) = Box(
        min(left, other.left), min(top, other.top),
        max(right, other.right), max(bottom, other.bottom),
    )
}

/** One recognised word. [confidence] is ML Kit's, or 1 when unknown. */
data class OcrElement(val text: String, val box: Box, val confidence: Float = 1f)

/** One recognised line of words, in reading order. */
data class OcrLine(
    val text: String,
    val box: Box,
    val elements: List<OcrElement>,
    val confidence: Float = 1f,
) {
    companion object {
        /**
         * Builds a line whose words share [box] in proportion to their length.
         * Handy for fixtures and for engines that only report line boxes.
         */
        fun of(text: String, box: Box, confidence: Float = 1f): OcrLine {
            val words = text.split(' ').filter { it.isNotEmpty() }
            val totalChars = words.sumOf { it.length } + (words.size - 1).coerceAtLeast(0)
            val charWidth = box.width / totalChars.coerceAtLeast(1).toFloat()
            var x = box.left.toFloat()
            val elements = words.map { word ->
                val right = x + word.length * charWidth
                OcrElement(word, Box(x.toInt(), box.top, right.toInt(), box.bottom), confidence).also {
                    x = right + charWidth
                }
            }
            return OcrLine(text, box, elements, confidence)
        }
    }
}

/** Everything the recogniser found in one image. */
data class OcrPage(val width: Int, val height: Int, val lines: List<OcrLine>) {
    val text: String get() = lines.joinToString("\n") { it.text }
}
