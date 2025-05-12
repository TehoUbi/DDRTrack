package com.tehoubi.ddrtracker.core.ocr

/** A number read from one or more adjacent OCR elements. */
data class NumberToken(
    val value: Int,
    val digits: String,
    val box: Box,
    val lineIndex: Int,
    /** Characters that had to be mapped from a look-alike letter. */
    val substitutions: Int,
    val confidence: Float,
)

internal object Numbers {

    private val confusables = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0', 'U' to '0',
        'I' to '1', 'l' to '1', '|' to '1', 'i' to '1', '!' to '1', ']' to '1', '[' to '1',
        'Z' to '2', 'z' to '2',
        'S' to '5', 's' to '5',
        'B' to '8',
        'G' to '6', 'b' to '6',
        'T' to '7',
        'g' to '9', 'q' to '9',
    )

    data class Reading(val digits: String, val substitutions: Int, val realDigits: Int)

    /**
     * Reads [raw] as digits, allowing thousands separators and look-alike
     * letters. Returns null if anything else is in the way.
     *
     * In [lenient] mode a token made entirely of look-alikes ("O", "lO") is
     * accepted: right next to a judgment label that is almost always a
     * misread number. Elsewhere at least half the characters must be real
     * digits, otherwise words like "GOOD" would read as 6000.
     */
    fun read(raw: String, lenient: Boolean = false): Reading? {
        val trimmed = raw.trim().trimEnd('.', ':', ';').trimStart(':', '.', '#', 'x', 'X')
        if (trimmed.isEmpty()) return null
        val out = StringBuilder()
        var subs = 0
        var real = 0
        for (c in trimmed) {
            when {
                c.isDigit() -> { out.append(c); real++ }
                c == ',' || c == '.' || c == '\'' -> Unit
                c in confusables -> { out.append(confusables.getValue(c)); subs++ }
                else -> return null
            }
        }
        if (out.isEmpty() || out.length > 7) return null
        if (!lenient && real * 2 < out.length) return null
        return Reading(out.toString(), subs, real)
    }

    /**
     * Groups numeric elements of a line into numbers, joining neighbours that
     * the recogniser split at a thousands separator ("998," "520").
     */
    fun numbersInLine(line: OcrLine, lineIndex: Int, lenient: Boolean = false): List<NumberToken> {
        val tokens = mutableListOf<NumberToken>()
        var pending: Pair<OcrElement, Reading>? = null

        fun flush() {
            pending?.let { (el, r) ->
                tokens += NumberToken(
                    value = r.digits.toInt(),
                    digits = r.digits,
                    box = el.box,
                    lineIndex = lineIndex,
                    substitutions = r.substitutions,
                    confidence = el.confidence,
                )
            }
            pending = null
        }

        for (el in line.elements) {
            val reading = read(el.text, lenient)
            if (reading == null) { flush(); continue }
            val prev = pending
            if (prev != null && joins(prev.first, prev.second, el, reading)) {
                val merged = OcrElement(prev.first.text + el.text, prev.first.box.union(el.box), minOf(prev.first.confidence, el.confidence))
                pending = merged to Reading(
                    prev.second.digits + reading.digits,
                    prev.second.substitutions + reading.substitutions,
                    prev.second.realDigits + reading.realDigits,
                )
            } else {
                flush()
                pending = el to reading
            }
        }
        flush()
        return tokens
    }

    private fun joins(a: OcrElement, ar: Reading, b: OcrElement, br: Reading): Boolean {
        val gap = b.box.left - a.box.right
        val separated = a.text.endsWith(',') || a.text.endsWith('.')
        return br.digits.length == 3 && ar.digits.length <= 3 &&
            gap < a.box.height && (separated || gap < a.box.height / 3)
    }
}
