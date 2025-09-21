package com.tehoubi.ddrtracker.core.ocr

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.scoring.DdrScoring

/**
 * Works out judgment counts OCR couldn't read (or misread) from everything
 * else on the screen.
 *
 * The small, stylised digits of the judgment table are the least readable
 * part of a cabinet photo, but the screen states the same facts several
 * times over in large print:
 *
 * - the step total after MAX COMBO ("140 / 417") equals Marvelous + Perfect
 *   + Great + Good + Miss;
 * - EX score = 3·Marvelous + 2·Perfect + Great + 3·O.K.;
 * - the money score follows exactly from the judgments (SN2 formula);
 * - a lamp ("Perfect Fullcombo!!") rules out whole judgment kinds, and a
 *   max combo equal to the step total rules out Misses.
 *
 * The unknown counts are enumerated under those constraints. Values are
 * only filled in when exactly one assignment fits; when several fit, only
 * the counts they all agree on are filled. If the values that *were* read
 * admit no solution, each one in turn is treated as possibly misread.
 */
object JudgmentSolver {

    data class Solution(
        val judgments: Judgments,
        /** Rows that were unread and are now filled in. */
        val filled: Set<StatLabel>,
        /** Rows whose read value was replaced: label -> value OCR gave. */
        val replaced: Map<StatLabel, Int>,
        val ng: Int,
    )

    private const val BUDGET = 3_000_000
    private const val MAX_NG = 3
    private const val MAX_SOLUTIONS = 50
    private val STEPS = listOf(StatLabel.MARVELOUS, StatLabel.PERFECT, StatLabel.GREAT, StatLabel.GOOD, StatLabel.MISS)

    private class Facts(
        val total: Int?,
        val ex: Int?,
        val score: Int?,
        val failed: Boolean,
        /** Rows known to be zero from the lamp or a full combo. */
        val zero: Set<StatLabel>,
    )

    fun solve(r: ParsedResult): Solution? {
        val facts = Facts(
            total = r.comboTotal?.value,
            ex = r.exScore?.value,
            score = r.score?.value?.takeIf { it <= DdrScoring.MAX_SCORE },
            failed = r.failed,
            zero = zeroRows(r),
        )
        // Without at least two independent totals the counts aren't pinned down.
        if (listOfNotNull(facts.total, facts.ex, facts.score.takeIf { !r.failed }).size < 2) return null

        val read = StatLabel.JUDGMENTS.mapNotNull { l -> r.stats[l]?.let { l to it } }.toMap()
        val unread = StatLabel.JUDGMENTS.filter { it !in read }.toSet()

        attempt(read.mapValues { it.value.value }, unread, facts)?.let { (j, ng, agreed) ->
            val filled = if (j != null) unread else agreed.keys
            if (filled.isEmpty() && j == null) return null
            if (j == null) return Solution(partial(read, agreed), filled, emptyMap(), ng)
            if (unread.isEmpty()) return null // already consistent
            return Solution(j, filled, emptyMap(), ng)
        }

        // Nothing fits the values as read: one of them is probably wrong. Try
        // freeing each read value (least confident first) and keep the fix
        // only if exactly one such fix explains the screen.
        val fixes = read.entries.sortedBy { it.value.confidence }.mapNotNull { (label, field) ->
            val others = read.filterKeys { it != label }.mapValues { it.value.value }
            attempt(others, unread + label, facts)?.let { (j, ng, _) -> j?.let { Triple(label, field.value, it to ng) } }
        }.filter { it.third.first.count(it.first) != it.second }
        if (fixes.size != 1) return null
        val (label, old, sol) = fixes.single()
        return Solution(sol.first, unread, mapOf(label to old), sol.second)
    }

    private fun Judgments.count(label: StatLabel): Int = when (label) {
        StatLabel.MARVELOUS -> marvelous
        StatLabel.PERFECT -> perfect
        StatLabel.GREAT -> great
        StatLabel.GOOD -> good
        StatLabel.OK -> ok
        StatLabel.MISS -> miss
        else -> error("not a judgment: $label")
    }

    private fun partial(read: Map<StatLabel, Field<Int>>, agreed: Map<StatLabel, Int>): Judgments {
        val v = read.mapValues { it.value.value } + agreed
        return Judgments(
            v[StatLabel.MARVELOUS] ?: 0, v[StatLabel.PERFECT] ?: 0, v[StatLabel.GREAT] ?: 0,
            v[StatLabel.GOOD] ?: 0, v[StatLabel.OK] ?: 0, v[StatLabel.MISS] ?: 0,
        )
    }

    private fun zeroRows(r: ParsedResult): Set<StatLabel> {
        val zero = mutableSetOf<StatLabel>()
        when (r.lamp?.value) {
            ClearLamp.MARVELOUS_FULL_COMBO -> zero += listOf(StatLabel.PERFECT, StatLabel.GREAT, StatLabel.GOOD, StatLabel.MISS)
            ClearLamp.PERFECT_FULL_COMBO -> zero += listOf(StatLabel.GREAT, StatLabel.GOOD, StatLabel.MISS)
            ClearLamp.GREAT_FULL_COMBO -> zero += listOf(StatLabel.GOOD, StatLabel.MISS)
            ClearLamp.FULL_COMBO -> zero += StatLabel.MISS
            else -> Unit
        }
        val combo = r.maxCombo?.value
        if (combo != null && combo == r.comboTotal?.value) zero += StatLabel.MISS
        return zero
    }

    /**
     * Enumerates assignments of [unknown] given [fixed]. Returns the unique
     * solution at the smallest N.G. count (first), or null plus the values
     * every solution shares (third) when several fit. Null overall when
     * nothing fits or the search is too large.
     */
    private fun attempt(
        fixed: Map<StatLabel, Int>,
        unknown: Set<StatLabel>,
        facts: Facts,
    ): Triple<Judgments?, Int, Map<StatLabel, Int>>? {
        // Rows the lamp zeroes are known, unless they were read as non-zero.
        if (facts.zero.any { (fixed[it] ?: 0) != 0 }) return null
        val known = fixed + facts.zero.filter { it in unknown }.associateWith { 0 }
        val free = unknown.filter { it !in known }
        val freeSteps = STEPS.filter { it in free }
        val okFree = StatLabel.OK in free

        val knownSteps = STEPS.sumOf { known[it] ?: 0 }
        val remaining = facts.total?.let { it - knownSteps }
        if (remaining != null && remaining < 0) return null
        if (remaining == null && freeSteps.size > 1) return null
        val range = remaining ?: 1_000

        val solutions = mutableListOf<Pair<Judgments, Int>>()
        var work = 0
        val values = IntArray(6) { i -> known[StatLabel.JUDGMENTS[i]] ?: 0 }
        fun idx(l: StatLabel) = StatLabel.JUDGMENTS.indexOf(l)

        fun evaluate(): Boolean {
            if (++work > BUDGET) return false
            val okIdx = idx(StatLabel.OK)
            if (okFree) {
                val ex = facts.ex ?: return true.also { /* without EX, O.K. is unconstrained */ }
                val rest = ex - (3 * values[0] + 2 * values[1] + values[2])
                if (rest < 0 || rest % 3 != 0) return true
                values[okIdx] = rest / 3
            }
            val j = Judgments(values[0], values[1], values[2], values[3], values[4], values[5])
            if (facts.total != null && j.steps != facts.total) return true
            if (facts.ex != null && DdrScoring.exScore(j) != facts.ex) return true
            if (facts.score != null && !facts.failed) {
                val ng = (0..MAX_NG).firstOrNull { DdrScoring.moneyScore(j, it) == facts.score } ?: return true
                solutions += j to ng
            } else solutions += j to 0
            return solutions.size <= MAX_SOLUTIONS
        }

        fun search(i: Int, left: Int): Boolean {
            if (i == freeSteps.size - 1) {
                values[idx(freeSteps[i])] = if (remaining != null) left else return (0..range).all { v ->
                    values[idx(freeSteps[i])] = v; evaluate()
                }
                return evaluate()
            }
            for (v in 0..left) {
                values[idx(freeSteps[i])] = v
                if (!search(i + 1, left - v)) return false
            }
            return true
        }

        val finished = if (freeSteps.isEmpty()) evaluate() else search(0, range)
        // Out of budget, or so many fits that the ones seen say nothing: give up.
        if (!finished) return null
        if (solutions.isEmpty()) return null
        val bestNg = solutions.minOf { it.second }
        val best = solutions.filter { it.second == bestNg }.map { it.first }.distinct()
        if (best.size == 1) return Triple(best.single(), bestNg, emptyMap())
        val agreed = free.mapNotNull { l -> best.map { it.count(l) }.distinct().singleOrNull()?.let { l to it } }.toMap()
        return Triple(null, bestNg, agreed)
    }
}
