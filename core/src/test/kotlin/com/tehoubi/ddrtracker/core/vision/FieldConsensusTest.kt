package com.tehoubi.ddrtracker.core.vision

import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.ocr.ResultScreenParser
import com.tehoubi.ddrtracker.core.ocr.butterflyPfc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FieldConsensusTest {

    private val parser = ResultScreenParser()
    private val clean = parser.parse(butterflyPfc())
    private val misread = parser.parse(butterflyPfc(marvelous = "482", score = "999,830"))

    @Test
    fun `locks after enough agreeing frames`() {
        val c = FieldConsensus(minVotes = 3)
        c.add(clean); c.add(clean)
        assertFalse(c.isLocked)
        c.add(clean)
        assertTrue(c.isLocked)
        assertEquals(1f, c.progress)
    }

    @Test
    fun `the majority outvotes a misread frame`() {
        val c = FieldConsensus()
        c.add(clean); c.add(misread); c.add(clean); c.add(clean)
        val r = c.current()
        assertEquals(Judgments(452, 12, 0, 0, 34, 0), r.judgments())
        assertEquals(999_880, r.score?.value)
        assertTrue(c.isLocked)
    }

    @Test
    fun `a split vote does not lock`() {
        val c = FieldConsensus(minVotes = 2)
        c.add(clean); c.add(misread); c.add(clean); c.add(misread)
        assertFalse(c.isLocked)
        assertTrue(c.progress < 1f)
    }

    @Test
    fun `non-result frames are ignored`() {
        val c = FieldConsensus()
        c.add(parser.parse(com.tehoubi.ddrtracker.core.ocr.OcrPage(1280, 720, emptyList())))
        assertEquals(0, c.frameCount)
    }

    @Test
    fun `old frames fall out of the window`() {
        val c = FieldConsensus(window = 3, minVotes = 3)
        repeat(3) { c.add(misread) }
        repeat(3) { c.add(clean) }
        assertEquals(452, c.current().stats.values.first().value)
        assertTrue(c.isLocked)
    }
}
