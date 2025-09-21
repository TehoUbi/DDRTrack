package com.tehoubi.ddrtracker.core.vision

import com.tehoubi.ddrtracker.core.ocr.Field
import com.tehoubi.ddrtracker.core.ocr.ParsedResult
import com.tehoubi.ddrtracker.core.ocr.StatLabel

/**
 * Combines parses of consecutive camera frames.
 *
 * A single frame misreads a digit now and then; the same misread on several
 * frames in a row is much rarer. Each field's value is the confidence-weighted
 * mode over the last [window] frames, and the scan locks once the score and
 * every judgment have at least [minVotes] frames agreeing with a clear
 * majority, which is the cue to take the high-resolution still.
 */
class FieldConsensus(
    private val window: Int = 8,
    private val minVotes: Int = 3,
    private val minShare: Float = 0.6f,
) {
    private data class Frame(val result: ParsedResult, val weight: Float)

    private val frames = ArrayDeque<Frame>()

    val frameCount: Int get() = frames.size

    fun add(result: ParsedResult, weight: Float = 1f) {
        if (!result.looksLikeResultScreen) return
        frames.addLast(Frame(result, weight))
        while (frames.size > window) frames.removeFirst()
    }

    fun reset() = frames.clear()

    private data class Vote<T>(val value: T, val share: Float, val agreeing: Int, val confidence: Float)

    private fun <T> vote(select: (ParsedResult) -> Field<T>?): Vote<T>? {
        val weights = linkedMapOf<T, Float>()
        val counts = mutableMapOf<T, Int>()
        val confidences = mutableMapOf<T, Float>()
        var total = 0f
        for (frame in frames) {
            val field = select(frame.result) ?: continue
            val w = frame.weight * field.confidence.coerceAtLeast(0.05f)
            weights[field.value] = (weights[field.value] ?: 0f) + w
            counts[field.value] = (counts[field.value] ?: 0) + 1
            confidences[field.value] = maxOf(confidences[field.value] ?: 0f, field.confidence)
            total += w
        }
        val (value, weight) = weights.maxByOrNull { it.value } ?: return null
        return Vote(value, weight / total, counts.getValue(value), confidences.getValue(value))
    }

    private fun <T> field(select: (ParsedResult) -> Field<T>?): Field<T>? =
        vote(select)?.let { Field(it.value, (it.confidence * (0.5f + 0.5f * it.share)).coerceIn(0f, 1f)) }

    /** The best current reading of every field. */
    fun current(): ParsedResult {
        val latest = frames.lastOrNull()?.result ?: return ParsedResult()
        val stats = StatLabel.entries.mapNotNull { label -> field { it.stats[label] }?.let { label to it } }.toMap()
        return ParsedResult(
            title = field { it.title },
            artist = field { it.artist },
            style = field { it.style },
            difficulty = field { it.difficulty },
            level = field { it.level },
            score = field { it.score },
            stats = stats,
            grade = field { it.grade },
            lamp = field { it.lamp },
            failed = frames.count { it.result.failed } * 2 > frames.size || latest.failed && frames.size == 1,
            comboTotal = field { it.comboTotal },
            labelsSeen = frames.flatMap { it.result.labelsSeen }.toSet() + stats.keys,
        )
    }

    private val score: (ParsedResult) -> Field<Int>? = { it.score }
    private val judgments: List<(ParsedResult) -> Field<Int>?> = StatLabel.JUDGMENTS.map { label -> { r: ParsedResult -> r.stats[label] } }
    private val totals: List<(ParsedResult) -> Field<Int>?> = listOf({ it.exScore }, { it.comboTotal })

    private fun isSettled(select: (ParsedResult) -> Field<Int>?): Boolean =
        vote(select)?.let { it.agreeing >= minVotes && it.share >= minShare } ?: false

    /** Share of the fields needed to lock that have settled, for a progress indicator. */
    val progress: Float
        get() {
            val viaJudgments = (listOf(score) + judgments).count { isSettled(it) } / 7f
            val viaTotals = (listOf(score) + totals).count { isSettled(it) } / 3f
            return maxOf(viaJudgments, viaTotals)
        }

    val isLocked: Boolean
        get() = isSettled(score) && (judgments.all { isSettled(it) } || totals.all { isSettled(it) })
}
