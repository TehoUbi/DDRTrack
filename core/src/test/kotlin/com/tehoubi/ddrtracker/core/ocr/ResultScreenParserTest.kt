package com.tehoubi.ddrtracker.core.ocr

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResultScreenParserTest {

    private val parser = ResultScreenParser()

    @Test
    fun `clean screen parses every field`() {
        val r = parser.parse(butterflyPfc())
        assertTrue(r.looksLikeResultScreen)
        assertEquals("Butterfly", r.title?.value)
        assertEquals("SMiLE.dk", r.artist?.value)
        assertEquals(PlayStyle.SINGLE, r.style?.value)
        assertEquals(Difficulty.EXPERT, r.difficulty?.value)
        assertEquals(12, r.level?.value)
        assertEquals(999_880, r.score?.value)
        assertEquals(Judgments(452, 12, 0, 0, 34, 0), r.judgments())
        assertEquals(464, r.maxCombo?.value)
        assertEquals(1482, r.exScore?.value)
        assertEquals(5, r.stats[StatLabel.FAST]?.value)
        assertEquals(7, r.stats[StatLabel.SLOW]?.value)
        assertEquals(Grade.AAA, r.grade?.value)
        assertEquals(ClearLamp.PERFECT_FULL_COMBO, r.lamp?.value)
        assertFalse(r.failed)
        assertEquals(emptyList(), r.missingFields())
    }

    @Test
    fun `look-alike letters in values are read as digits`() {
        val r = parser.parse(
            butterflyPfc(marvelous = "O452", perfect = "l2", great = "O", miss = "D", score = "999,88O")
        )
        assertEquals(Judgments(452, 12, 0, 0, 34, 0), r.judgments())
        assertEquals(999_880, r.score?.value)
        // Substituted digits cost confidence.
        assertTrue(r.stats.getValue(StatLabel.MARVELOUS).confidence < r.stats.getValue(StatLabel.OK).confidence)
    }

    @Test
    fun `misspelled labels still match`() {
        val r = parser.parse(
            butterflyPfc(
                labels = mapOf(
                    "MARVELOUS" to "MARVEL0US", "PERFECT" to "PERFFCT", "O.K." to "0.K.",
                    "MISS" to "MlSS", "MAX COMBO" to "MAX C0MBO", "EX SCORE" to "EXSCORE",
                )
            )
        )
        assertTrue(r.hasAllJudgments)
        assertEquals(Judgments(452, 12, 0, 0, 34, 0), r.judgments())
        assertEquals(464, r.maxCombo?.value)
        assertEquals(1482, r.exScore?.value)
    }

    @Test
    fun `label and value on one line`() {
        val p = page {
            line("MARVELOUS 0321", 420, 400, 900, 426)
            line("PERFECT 0045", 420, 434, 900, 460)
            line("GREAT 0003", 420, 468, 900, 494)
            line("GOOD 0001", 420, 502, 900, 528)
            line("OK 0020", 420, 536, 900, 562)
            line("MISS 0002", 420, 570, 900, 596)
        }
        assertEquals(Judgments(321, 45, 3, 1, 20, 2), parser.parse(p).judgments())
    }

    @Test
    fun `score split at the thousands separator is joined`() {
        val p = butterflyPfc().let { base ->
            base.copy(lines = base.lines.map { line ->
                if (line.text == "999,880") OcrLine.of("999, 880", line.box) else line
            })
        }
        assertEquals(999_880, parser.parse(p).score?.value)
    }

    @Test
    fun `lamp text does not steal the perfect row`() {
        val r = parser.parse(butterflyPfc())
        assertEquals(12, r.stats[StatLabel.PERFECT]?.value)
    }

    @Test
    fun `failed stage`() {
        val base = butterflyPfc(score = "512,330")
        val p = base.copy(lines = base.lines.filterNot { it.text == "AAA" || it.text.contains("FULL") } +
            OcrLine.of("FAILED", Box(470, 240, 640, 300)))
        val r = parser.parse(p)
        assertTrue(r.failed)
        assertEquals(ClearLamp.FAILED, r.lamp?.value)
        assertEquals(512_330, r.score?.value)
    }

    @Test
    fun `level fused to the difficulty name`() {
        val p = page {
            line("CHALLENGE16", 420, 140, 620, 168)
            line("MARVELOUS", 420, 400, 580, 426)
        }
        val r = parser.parse(p)
        assertEquals(Difficulty.CHALLENGE, r.difficulty?.value)
        assertEquals(16, r.level?.value)
    }

    @Test
    fun `level printed under the difficulty name`() {
        val p = page {
            line("DIFFICULT", 420, 140, 580, 168)
            line("9", 490, 172, 510, 200)
        }
        assertEquals(9, parser.parse(p).level?.value)
    }

    @Test
    fun `numbers that spell words are not labels`() {
        val p = page {
            line("GOOD", 420, 502, 500, 528)
            line("6000", 800, 502, 880, 528)
        }
        val r = parser.parse(p)
        assertEquals(1, r.stats.size)
        assertEquals(6000, r.stats[StatLabel.GOOD]?.value)
    }

    @Test
    fun `song select screen is not a result screen`() {
        val p = page {
            line("SELECT MUSIC", 500, 20, 780, 60)
            line("PARANOiA", 420, 300, 600, 340)
            line("BASIC 8", 420, 360, 560, 388)
            line("DIFFICULT 11", 420, 400, 600, 428)
        }
        val r = parser.parse(p)
        assertFalse(r.looksLikeResultScreen)
        assertNull(r.score)
    }
}

class WideTableTest {
    @Test
    fun `values far from their labels with nothing else on the row`() {
        val p = page {
            listOf("MARVELOUS" to "452", "PERFECT" to "12", "GREAT" to "0", "GOOD" to "0", "O.K." to "34", "MISS" to "0").forEachIndexed { i, (l, v) ->
                line(l, 420, 400 + i * 34, 420 + l.length * 18, 426 + i * 34)
                line(v, 800, 400 + i * 34, 860, 426 + i * 34)
            }
        }
        assertEquals(Judgments(452, 12, 0, 0, 34, 0), ResultScreenParser().parse(p).judgments())
    }
}
