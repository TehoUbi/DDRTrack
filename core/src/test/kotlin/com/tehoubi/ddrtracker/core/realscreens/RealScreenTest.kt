package com.tehoubi.ddrtracker.core.realscreens

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.ocr.CheckedScan
import com.tehoubi.ddrtracker.core.ocr.OcrPage
import com.tehoubi.ddrtracker.core.ocr.ResultScreenParser
import com.tehoubi.ddrtracker.core.ocr.ScanValidator
import com.tehoubi.ddrtracker.core.ocr.StatLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Photos of real DDR WORLD result screens, read by a real OCR engine.
 * Ground truth is what the screens show. Each screen is checked both as a
 * single downscaled pass and with the app's second full-resolution read of
 * the stats panel.
 */
class RealScreenTest {

    private data class Truth(
        val score: Int, val judgments: Judgments, val ex: Int, val combo: Int, val total: Int,
        val fast: Int, val slow: Int, val level: Int, val grade: Grade, val lamp: ClearLamp,
    )

    private fun check(page: OcrPage): CheckedScan = ScanValidator.check(ResultScreenParser().parse(page))

    private fun assertMatches(t: Truth, c: CheckedScan) {
        val r = c.result
        assertTrue(r.looksLikeResultScreen)
        assertEquals(t.score, r.score?.value)
        assertEquals(t.judgments, r.judgments())
        assertTrue(r.hasAllJudgments)
        assertEquals(t.ex, r.exScore?.value)
        assertEquals(t.combo, r.maxCombo?.value)
        assertEquals(t.total, r.comboTotal?.value)
        assertEquals(t.fast, r.stats[StatLabel.FAST]?.value)
        assertEquals(t.slow, r.stats[StatLabel.SLOW]?.value)
        assertEquals(Difficulty.EXPERT, r.difficulty?.value)
        assertEquals(t.level, r.level?.value)
        assertEquals(t.grade, r.grade?.value)
        assertEquals(t.lamp, r.lamp?.value)
        assertTrue(c.isConsistent, c.issues.toString())
        assertEquals(0, c.inferredNg)
    }

    // Scarlet Moon, ESP 12: 191 / 129 / 86 / 3 / 5 / 8 -> 891,650, EX 932.
    private val scarletMoon = Truth(891_650, Judgments(191, 129, 86, 3, 5, 8), 932, 140, 417, 59, 159, 12, Grade.AA_MINUS, ClearLamp.CLEAR)

    // 向日葵サンセット, ESP 13, great full combo: 252 / 80 / 7 / 0 / 18 / 0 -> 991,280, EX 977.
    private val sunset = Truth(991_280, Judgments(252, 80, 7, 0, 18, 0), 977, 339, 339, 68, 19, 13, Grade.AAA, ClearLamp.GREAT_FULL_COMBO)

    // ブタサンダー, ESP 13, perfect full combo: 434 / 9 / 0 / 0 / 13 / 0 -> 999,910, EX 1,359.
    private val butaThunder = Truth(999_910, Judgments(434, 9, 0, 0, 13, 0), 1359, 443, 443, 2, 7, 13, Grade.AAA, ClearLamp.PERFECT_FULL_COMBO)

    @Test
    fun `scarlet moon, single pass`() {
        val c = check(RealScreens.page("world-1"))
        assertMatches(scarletMoon, c)
        // OCR never saw the Great..Miss digits; the totals pin them down.
        assertEquals(setOf("great", "good", "ok", "miss"), c.inferred)
    }

    @Test
    fun `scarlet moon, with panel re-read`() {
        val c = check(RealScreens.twoPass("world-1"))
        assertMatches(scarletMoon, c)
        assertEquals("Scarlet Moon", c.result.title?.value)
        assertTrue(c.result.artist!!.value.startsWith("REDAL"))
    }

    @Test
    fun `sunset, single pass`() = assertMatches(sunset, check(RealScreens.page("world-2")))

    @Test
    fun `sunset, with panel re-read reads every digit`() {
        val c = check(RealScreens.twoPass("world-2"))
        assertMatches(sunset, c)
        // Great and Good came back as one stacked "70"; split per row, nothing inferred.
        assertEquals(emptySet(), c.inferred)
        assertTrue(c.result.title!!.value.startsWith("向日葵"))
    }

    @Test
    fun `buta thunder, single pass`() = assertMatches(butaThunder, check(RealScreens.page("world-3")))

    @Test
    fun `buta thunder, with panel re-read corrects a misread digit`() {
        val c = check(RealScreens.twoPass("world-3"))
        assertMatches(butaThunder, c)
        // The re-read saw Perfect's 9 as 6; nothing else fits the totals.
        assertEquals(mapOf("perfect" to 6), c.repaired)
        // The title is Japanese and the Latin model can't read it: whatever
        // was picked instead is flagged, not trusted.
        assertTrue((c.result.title?.confidence ?: 0f) < 0.75f)
    }

    @Test
    fun `player names and cabinet text are not titles`() {
        for (n in 1..3) {
            val title = ResultScreenParser().parse(RealScreens.twoPass("world-$n")).title?.value.orEmpty()
            assertTrue(title !in listOf("SEN", "SIRCHEEE", "SORREL", "RESULTS", "BEMANI"), title)
        }
    }

    @Test
    fun `without the score the solver doesn't guess`() {
        val parsed = ResultScreenParser().parse(RealScreens.page("world-1"))
        val c = ScanValidator.check(parsed.copy(score = null))
        // EX and the step total alone allow many splits of Great..Miss.
        assertNull(c.result.stats[StatLabel.GOOD])
        assertTrue(c.inferred.isEmpty())
    }
}

class RealScreenRegionsTest {
    @Test
    fun `stats region covers the whole panel`() {
        for (n in 1..3) {
            val page = RealScreens.page("world-$n")
            val region = ResultScreenParser().statsRegion(page)!!
            val inside = { text: String -> page.lines.filter { it.text == text }.all { region.contains(it.box.centerX, it.box.centerY) } }
            assertTrue(inside("Marvelous"), "world-$n Marvelous")
            val combo = page.lines.first { it.text.replace(" ", "").startsWith("MAX") }
            assertTrue(region.contains(combo.box.right.toFloat(), combo.box.centerY), "world-$n MAX COMBO")
            assertTrue(region.height < page.height / 3, "world-$n region $region too tall")
        }
    }

    @Test
    fun `title region sits between the banner and the panel`() {
        val page = RealScreens.page("world-1")
        val region = ResultScreenParser().titleRegion(page)!!
        val title = page.lines.first { it.text == "Scarlet Moon" }
        assertTrue(region.contains(title.box.centerX, title.box.centerY))
        assertTrue(page.lines.filter { it.text == "SEN" }.none { region.contains(it.box.centerX, it.box.centerY) })
    }
}
