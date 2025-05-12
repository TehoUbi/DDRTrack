package com.tehoubi.ddrtracker.core.ocr

import kotlin.math.max
import kotlin.math.min

internal object TextMatching {

    /** Classic two-row Levenshtein distance. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = curr; curr = t
        }
        return prev[b.length]
    }

    /** 1 - distance / longer length, so 1.0 is identical. */
    fun similarity(a: String, b: String): Float {
        val longest = max(a.length, b.length)
        if (longest == 0) return 1f
        return 1f - levenshtein(a, b) / longest.toFloat()
    }

    /**
     * Uppercase A-Z0-9 only, with digits that OCR commonly confuses for
     * letters mapped back, so "MARVEL0US" and "0.K." compare as words.
     */
    fun labelKey(raw: String): String = buildString {
        for (c in raw.uppercase()) {
            when (c) {
                in 'A'..'Z' -> append(c)
                '0' -> append('O')
                '1' -> append('I')
                '5' -> append('S')
                '8' -> append('B')
                '6' -> append('G')
                '|' -> append('I')
            }
        }
    }

    /** Edit distance allowed when matching a label of this length. */
    fun tolerance(length: Int): Int = when {
        length <= 3 -> 0
        length <= 5 -> 1
        else -> 2
    }
}
