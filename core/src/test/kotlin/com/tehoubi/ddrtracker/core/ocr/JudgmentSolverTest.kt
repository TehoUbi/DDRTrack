package com.tehoubi.ddrtracker.core.ocr

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.scoring.DdrScoring
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JudgmentSolverTest {

    private fun parsed(read: Map<StatLabel, Int>, j: Judgments, lamp: ClearLamp? = null, total: Boolean = true, ex: Boolean = true) = ParsedResult(
        score = Field(DdrScoring.moneyScore(j), 1f),
        stats = read.mapValues { Field(it.value, 0.9f) } + listOfNotNull(if (ex) StatLabel.EX_SCORE to Field(DdrScoring.exScore(j), 1f) else null),
        comboTotal = if (total) Field(j.steps, 1f) else null,
        lamp = lamp?.let { Field(it, 0.9f) },
    )

    private val j = Judgments(marvelous = 310, perfect = 95, great = 22, good = 4, ok = 26, miss = 6)

    @Test
    fun `fills unread rows when the totals pin them down`() {
        val s = JudgmentSolver.solve(parsed(mapOf(StatLabel.MARVELOUS to 310, StatLabel.PERFECT to 95), j))!!
        assertEquals(j, s.judgments)
        assertEquals(setOf(StatLabel.GREAT, StatLabel.GOOD, StatLabel.OK, StatLabel.MISS), s.filled)
    }

    @Test
    fun `fixes a single misread value`() {
        val read = mapOf(StatLabel.MARVELOUS to 310, StatLabel.PERFECT to 95, StatLabel.GREAT to 28, StatLabel.GOOD to 4, StatLabel.OK to 26, StatLabel.MISS to 6)
        val s = JudgmentSolver.solve(parsed(read, j))!!
        assertEquals(j, s.judgments)
        assertEquals(mapOf(StatLabel.GREAT to 28), s.replaced)
    }

    @Test
    fun `a consistent read needs nothing`() {
        val read = mapOf(StatLabel.MARVELOUS to 310, StatLabel.PERFECT to 95, StatLabel.GREAT to 22, StatLabel.GOOD to 4, StatLabel.OK to 26, StatLabel.MISS to 6)
        assertNull(JudgmentSolver.solve(parsed(read, j)))
    }

    @Test
    fun `the lamp rules rows out`() {
        val pfc = Judgments(marvelous = 400, perfect = 30, ok = 20)
        val s = JudgmentSolver.solve(parsed(mapOf(StatLabel.MARVELOUS to 400), pfc, lamp = ClearLamp.PERFECT_FULL_COMBO))!!
        assertEquals(pfc, s.judgments)
    }

    @Test
    fun `too little information fills nothing wrong`() {
        // Only the score: Great/Good/Miss/OK can't be separated.
        val s = JudgmentSolver.solve(parsed(mapOf(StatLabel.MARVELOUS to 310, StatLabel.PERFECT to 95), j, total = false, ex = false))
        assertNull(s)
    }
}
