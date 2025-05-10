package com.tehoubi.ddrtracker.core.scoring

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DdrScoringTest {

    @Test
    fun `all marvelous is a million`() {
        val j = Judgments(marvelous = 400, ok = 20)
        assertEquals(1_000_000, DdrScoring.moneyScore(j))
        assertEquals(1_260, DdrScoring.exScore(j))
        assertEquals(ClearLamp.MARVELOUS_FULL_COMBO, DdrScoring.lamp(j))
    }

    @Test
    fun `each perfect costs exactly ten points`() {
        val j = Judgments(marvelous = 390, perfect = 10, ok = 20)
        assertEquals(999_900, DdrScoring.moneyScore(j))
        assertEquals(ClearLamp.PERFECT_FULL_COMBO, DdrScoring.lamp(j))
    }

    @Test
    fun `greats and goods take a share of the step value`() {
        // 500 objects -> 2000 points each. Great = 1200 - 10, Good = 400 - 10.
        val j = Judgments(marvelous = 497, great = 2, good = 1)
        val expected = 497 * 2000 + 2 * 1190 + 390
        assertEquals(expected / 10 * 10, DdrScoring.moneyScore(j))
        assertEquals(ClearLamp.FULL_COMBO, DdrScoring.lamp(j))
    }

    @Test
    fun `score rounds down to a multiple of ten`() {
        // 3 objects -> 333,333.33 each; one miss leaves 666,666.67.
        val j = Judgments(marvelous = 2, miss = 1)
        assertEquals(666_660, DdrScoring.moneyScore(j))
        assertEquals(ClearLamp.CLEAR, DdrScoring.lamp(j))
    }

    @Test
    fun `dropped freezes lower the score and can be inferred`() {
        val j = Judgments(marvelous = 300, perfect = 40, great = 5, ok = 18)
        val withTwoNg = DdrScoring.moneyScore(j, ng = 2)
        assertEquals(2, DdrScoring.inferNg(j, withTwoNg))
        assertNull(DdrScoring.inferNg(j, 500_000))
    }

    @Test
    fun `grade boundaries`() {
        assertEquals(Grade.AAA, DdrScoring.grade(990_000))
        assertEquals(Grade.AA_PLUS, DdrScoring.grade(989_990))
        assertEquals(Grade.AA_MINUS, DdrScoring.grade(899_990))
        assertEquals(Grade.A_MINUS, DdrScoring.grade(790_000))
        assertEquals(Grade.B_PLUS, DdrScoring.grade(789_990))
        assertEquals(Grade.D_PLUS, DdrScoring.grade(550_000))
        assertEquals(Grade.D, DdrScoring.grade(549_990))
        assertEquals(Grade.D, DdrScoring.grade(0))
        assertEquals(Grade.E, DdrScoring.grade(995_000, failed = true))
    }

    @Test
    fun `grade rejects impossible scores`() {
        assertFailsWith<IllegalArgumentException> { DdrScoring.grade(1_000_010) }
        assertFailsWith<IllegalArgumentException> { DdrScoring.grade(-10) }
    }

    @Test
    fun `grade labels parse back`() {
        Grade.entries.forEach { assertEquals(it, Grade.fromLabel(it.label)) }
        assertEquals(Grade.AA_PLUS, Grade.fromLabel("aa +"))
        assertNull(Grade.fromLabel("S"))
    }

    @Test
    fun `failed beats any lamp`() {
        assertEquals(ClearLamp.FAILED, DdrScoring.lamp(Judgments(marvelous = 10), failed = true))
    }

    @Test
    fun `score loss attributes the gap to a million`() {
        val j = Judgments(marvelous = 497, great = 2, good = 1)
        val loss = DdrScoring.scoreLoss(j)
        assertEquals(1_000_000 - DdrScoring.moneyScore(j), loss.values.sum() / 10 * 10)
    }
}
