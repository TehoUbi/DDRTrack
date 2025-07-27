package com.tehoubi.ddrtracker.core.ocr

import com.tehoubi.ddrtracker.core.model.Judgments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A two-player result screen: the title is centred over both tables, 1P's
 * table on the left and 2P's on the right, each with its own score.
 */
class TwoPlayerTest {

    private fun twoPlayer(rightMissing: Set<String> = emptySet()) = page {
        line("PARANOiA", 540, 40, 740, 80)
        line("EXPERT", 560, 90, 680, 118); line("14", 690, 90, 720, 118)
        line("951,230", 120, 200, 380, 260)
        line("889,500", 760, 200, 1020, 260)
        val left = listOf("MARVELOUS" to "300", "PERFECT" to "80", "GREAT" to "10", "GOOD" to "2", "O.K." to "30", "MISS" to "3")
        val right = listOf("MARVELOUS" to "250", "PERFECT" to "90", "GREAT" to "35", "GOOD" to "8", "O.K." to "28", "MISS" to "12")
        left.forEachIndexed { i, (label, v) ->
            line(label, 60, 300 + i * 34, 60 + label.length * 18, 326 + i * 34)
            line(v, 380, 300 + i * 34, 380 + v.length * 18, 326 + i * 34)
        }
        right.forEachIndexed { i, (label, v) ->
            if (label in rightMissing) return@forEachIndexed
            line(label, 700, 300 + i * 34, 700 + label.length * 18, 326 + i * 34)
            line(v, 1020, 300 + i * 34, 1020 + v.length * 18, 326 + i * 34)
        }
    }

    private val parser = ResultScreenParser()

    @Test
    fun `each side reads its own table`() {
        val l = parser.parse(twoPlayer(), ResultScreenParser.Side.LEFT)
        val r = parser.parse(twoPlayer(), ResultScreenParser.Side.RIGHT)
        assertEquals(Judgments(300, 80, 10, 2, 30, 3), l.judgments())
        assertEquals(Judgments(250, 90, 35, 8, 28, 12), r.judgments())
        assertEquals(951_230, l.score?.value)
        assertEquals(889_500, r.score?.value)
        // The centred title and chart belong to both.
        assertEquals("PARANOiA", l.title?.value)
        assertEquals("PARANOiA", r.title?.value)
        assertEquals(14, r.level?.value)
    }

    @Test
    fun `auto prefers 1P unless 2P read better`() {
        assertEquals(951_230, parser.parse(twoPlayer()).score?.value)
        val p = twoPlayer().let { pg -> pg.copy(lines = pg.lines.filterNot { it.text == "951,230" }) }
        assertEquals(889_500, parser.parse(p).score?.value)
    }

    @Test
    fun `single table is not split`() {
        assertNull(parser.parseBothSides(butterflyPfc()))
        assertNotNull(parser.parseBothSides(twoPlayer()))
    }
}
