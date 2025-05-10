package com.tehoubi.ddrtracker.core.scoring

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments

/**
 * DDR's scoring rules (SuperNOVA 2 scoring, used unchanged through WORLD).
 *
 * Every object on a chart (steps, freezes and shocks) is worth
 * 1,000,000 / objects. Marvelous and O.K. earn the full value, Perfect the
 * full value minus 10, Great 60% minus 10, Good 20% minus 10, and Miss / N.G.
 * nothing. The total is rounded down to a multiple of 10.
 */
object DdrScoring {

    const val MAX_SCORE = 1_000_000

    /** EX score: Marvelous 3, Perfect 2, Great 1, O.K. 3. */
    fun exScore(j: Judgments): Int = 3 * j.marvelous + 2 * j.perfect + j.great + 3 * j.ok

    /** The best EX score possible on a chart with this many objects. */
    fun maxExScore(objects: Int): Int = 3 * objects

    fun grade(score: Int, failed: Boolean = false): Grade {
        if (failed) return Grade.E
        require(score in 0..MAX_SCORE) { "score out of range: $score" }
        return Grade.entries.first { it != Grade.E && score >= it.minScore }
    }

    /**
     * The clear lamp the judgments earn. A full combo needs no Miss; which
     * kind depends on the worst step judgment present. Dropped freezes (N.G.)
     * also break a full combo in game, but the result screen doesn't show
     * them, so a scan can over-report an FC on charts with freezes.
     */
    fun lamp(j: Judgments, failed: Boolean = false): ClearLamp = when {
        failed -> ClearLamp.FAILED
        j.miss > 0 -> ClearLamp.CLEAR
        j.good > 0 -> ClearLamp.FULL_COMBO
        j.great > 0 -> ClearLamp.GREAT_FULL_COMBO
        j.perfect > 0 -> ClearLamp.PERFECT_FULL_COMBO
        j.steps > 0 -> ClearLamp.MARVELOUS_FULL_COMBO
        else -> ClearLamp.CLEAR
    }

    /**
     * The money score these judgments produce if [ng] freezes were dropped.
     * Done in integer arithmetic scaled by the object count so it matches the
     * game's truncation instead of drifting with floating point.
     */
    fun moneyScore(j: Judgments, ng: Int = 0): Int {
        val objects = j.total + ng
        if (objects == 0) return 0
        // Points * objects, with the per-object value 1,000,000 / objects factored out.
        val scaled = 1_000_000L * (j.marvelous + j.ok + j.perfect) +
            600_000L * j.great +
            200_000L * j.good
        val raw = scaled / objects - 10L * (j.perfect + j.great + j.good)
        return ((raw.coerceAtLeast(0) / 10) * 10).toInt()
    }

    /**
     * Searches for the dropped-freeze count that makes [j] produce [score].
     * Returns null if no N.G. count up to [maxNg] reproduces it within
     * [tolerance] points (the game truncates per step, so the closed form can
     * land 10 points off on long charts).
     */
    fun inferNg(j: Judgments, score: Int, maxNg: Int = 200, tolerance: Int = 10): Int? =
        (0..maxNg).firstOrNull { ng -> kotlin.math.abs(moneyScore(j, ng) - score) <= tolerance }

    /** Score lost to each judgment kind relative to an all-Marvelous play. */
    fun scoreLoss(j: Judgments, ng: Int = 0): Map<String, Int> {
        val objects = j.total + ng
        if (objects == 0) return emptyMap()
        val value = MAX_SCORE.toDouble() / objects
        return linkedMapOf(
            "Perfect" to (j.perfect * 10.0).toInt(),
            "Great" to (j.great * (value * 0.4 + 10)).toInt(),
            "Good" to (j.good * (value * 0.8 + 10)).toInt(),
            "Miss" to (j.miss * value).toInt(),
            "N.G." to (ng * value).toInt(),
        )
    }
}
