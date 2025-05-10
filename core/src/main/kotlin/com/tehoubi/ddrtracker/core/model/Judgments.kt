package com.tehoubi.ddrtracker.core.model

/**
 * The judgment counts printed on a result screen.
 *
 * [ok] counts freeze/shock arrows held to the end. The result screen does not
 * print N.G. (dropped freezes) separately, so it is not stored.
 */
data class Judgments(
    val marvelous: Int = 0,
    val perfect: Int = 0,
    val great: Int = 0,
    val good: Int = 0,
    val ok: Int = 0,
    val miss: Int = 0,
) {
    init {
        require(listOf(marvelous, perfect, great, good, ok, miss).all { it >= 0 }) {
            "judgment counts cannot be negative: $this"
        }
    }

    /** Step judgments only (everything but O.K.). */
    val steps: Int get() = marvelous + perfect + great + good + miss

    val total: Int get() = steps + ok

    companion object {
        val EMPTY = Judgments()
    }
}
