package com.tehoubi.ddrtracker.core.ocr

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.scoring.DdrScoring
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanValidatorTest {

    private val parser = ResultScreenParser()

    private fun check(page: OcrPage) = ScanValidator.check(parser.parse(page))

    @Test
    fun `consistent screen has no issues`() {
        val c = check(butterflyPfc())
        assertTrue(c.isConsistent, c.issues.toString())
        assertEquals(emptyMap(), c.repaired)
        assertEquals(0, c.inferredNg)
        assertEquals(Grade.AAA, c.result.grade?.value)
        assertEquals(ClearLamp.PERFECT_FULL_COMBO, c.result.lamp?.value)
    }

    @Test
    fun `a misread judgment is repaired by the EX checksum`() {
        // 452 read as 482: 5 and 8 are the classic seven-segment mix-up.
        val c = check(butterflyPfc(marvelous = "482"))
        assertEquals(mapOf("marvelous" to 482), c.repaired)
        assertEquals(452, c.result.stats[StatLabel.MARVELOUS]?.value)
        assertTrue(c.isConsistent, c.issues.toString())
    }

    @Test
    fun `a misread score digit is repaired`() {
        val c = check(butterflyPfc(score = "999,830"))
        assertEquals(mapOf("score" to 999_830), c.repaired)
        assertEquals(999_880, c.result.score?.value)
    }

    @Test
    fun `a misread EX score is repaired`() {
        val c = check(butterflyPfc(exScore = "1432"))
        assertEquals(1482, c.result.exScore?.value)
    }

    @Test
    fun `a stray last digit is repaired`() {
        val c = check(butterflyPfc(score = "999,886"))
        assertEquals(999_880, c.result.score?.value)
    }

    @Test
    fun `unexplainable screens are flagged, not guessed`() {
        val c = check(butterflyPfc(marvelous = "300", perfect = "40"))
        assertFalse(c.isConsistent)
        assertEquals(emptyMap(), c.repaired)
        assertTrue(c.issues.any { it.field == "exScore" })
    }

    @Test
    fun `grade is derived from the score`() {
        val base = butterflyPfc()
        val p = base.copy(lines = base.lines.map { if (it.text == "AAA") OcrLine.of("AA", it.box) else it })
        val c = check(p)
        assertEquals(Grade.AAA, c.result.grade?.value)
        assertTrue(c.issues.any { it.field == "grade" })
    }

    @Test
    fun `dropped freezes are reported and break the full combo`() {
        val j = Judgments(marvelous = 452, perfect = 12, ok = 33)
        val score = DdrScoring.moneyScore(j, ng = 1)
        val c = check(
            butterflyPfc(ok = "33", score = "%,d".format(score), exScore = DdrScoring.exScore(j).toString())
        )
        assertEquals(1, c.inferredNg)
        assertEquals(ClearLamp.CLEAR, c.result.lamp?.value)
        assertTrue(c.issues.any { it.message.contains("dropped") })
    }

    @Test
    fun `unreadable rows are worked out from the totals`() {
        val base = butterflyPfc()
        // Label and value both unreadable (the value is the only "0" on its row).
        val p = base.copy(lines = base.lines.filterNot { it.text == "GOOD" || (it.text == "0" && it.box.top == 502) })
        val c = check(p)
        assertEquals(0, c.result.stats[StatLabel.GOOD]?.value)
        assertEquals(setOf("good"), c.inferred)
        assertTrue(c.isConsistent)
    }

    @Test
    fun `rows that can't be worked out are listed`() {
        val base = butterflyPfc()
        // No EX score and no score: nothing to work Good out from.
        val p = base.copy(lines = base.lines.filterNot {
            it.text == "GOOD" || (it.text == "0" && it.box.top == 502) || it.text == "EX SCORE" || it.text == "1482" || it.text == "999,880"
        })
        val c = check(p)
        assertTrue(c.issues.any { it.field == "good" })
        assertNull(c.result.stats[StatLabel.GOOD])
    }

    @Test
    fun `alternatives cover swaps and drops`() {
        val alts = ScanValidator.alternatives(452)
        assertEquals(1, alts[482])
        assertEquals(1, alts[462])
        assertEquals(2, alts[42])
        assertFalse(452 in alts)
    }

    @Test
    fun `manual entry check`() {
        val j = Judgments(marvelous = 452, perfect = 12, ok = 34)
        val ok = ScanValidator.checkManual(j, 999_880, 1482, failed = false)
        assertTrue(ok.none { it.severity >= Severity.WARNING }, ok.toString())
        assertTrue(ScanValidator.checkManual(j, 990_000, null, failed = false).any { it.field == "score" })
    }
}
