package com.tehoubi.ddrtracker.core.songs

import com.tehoubi.ddrtracker.core.ocr.Box
import com.tehoubi.ddrtracker.core.ocr.OcrLine
import com.tehoubi.ddrtracker.core.ocr.ResultScreenParser
import com.tehoubi.ddrtracker.core.ocr.butterflyPfc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongCatalogTest {

    private val catalog = SongCatalog.bundled()

    @Test
    fun `bundled catalog loads`() {
        assertTrue(catalog.size > 80)
        assertNotNull(catalog.find("PARANOiA"))
    }

    @Test
    fun `ocr spellings find the right song`() {
        assertEquals("PARANOiA", catalog.bestMatch(listOf("PARANOIA"))?.song?.title)
        assertEquals("MAX 300", catalog.bestMatch(listOf("MAX 3OO"))?.song?.title)
        assertEquals("Healing Vision ~Angelic mix~", catalog.bestMatch(listOf("Healing Vision Angelic mix"))?.song?.title)
        assertEquals("AM-3P", catalog.bestMatch(listOf("AM 3P"))?.song?.title)
        assertEquals("CANDY☆", catalog.bestMatch(listOf("CANDY*"))?.song?.title)
        assertEquals("Xepher", catalog.bestMatch(listOf("Xephar"))?.song?.title)
    }

    @Test
    fun `close relatives are told apart`() {
        assertEquals("PARANOiA Rebirth", catalog.bestMatch(listOf("PARANOiA Rebirth"))?.song?.title)
        assertEquals("MAX 360", catalog.bestMatch(listOf("MAX 360"))?.song?.title)
        assertEquals("Pluto Relinquish", catalog.bestMatch(listOf("Pluto Relinquish"))?.song?.title)
    }

    @Test
    fun `unrelated text does not match`() {
        assertNull(catalog.bestMatch(listOf("NEW RECORD")))
        assertNull(catalog.bestMatch(listOf("A Song Nobody Has Heard Of")))
    }

    @Test
    fun `suggestions prefer prefixes`() {
        val s = catalog.suggest("para").map { it.title }
        assertEquals("PARANOiA", s.first())
        val firstInfix = s.indexOf("Seta Para Cima↑↑")
        assertTrue(firstInfix > 0)
        assertTrue(s.take(firstInfix).all { it.lowercase().startsWith("para") })
    }

    @Test
    fun `keys are stable and readable`() {
        assertEquals("paranoia-rebirth", SongKey.of("PARANOiA Rebirth"))
        assertEquals("max-300", SongKey.of(" MAX 300 "))
        assertEquals("candy", SongKey.of("CANDY☆"))
        val jp = SongKey.of("津軽")
        assertTrue(jp.startsWith("u") && jp.length == 13)
        assertEquals(jp, SongKey.of("津軽"))
    }

    @Test
    fun `parser uses the catalog spelling`() {
        val base = butterflyPfc()
        val misread = base.copy(lines = base.lines.map {
            if (it.text == "Butterfly") OcrLine.of("Butterf1y", it.box) else it
        })
        assertEquals("Butterf1y", ResultScreenParser().parse(misread).title?.value)
        assertEquals("Butterfly", ResultScreenParser(catalog).parse(misread).title?.value)
    }

    @Test
    fun `titles wrapped over two lines are matched together`() {
        val base = butterflyPfc()
        val wrapped = base.copy(lines = base.lines.filterNot { it.text == "Butterfly" || it.text == "SMiLE.dk" } +
            OcrLine.of("Healing Vision", Box(420, 40, 640, 76)) +
            OcrLine.of("~Angelic mix~", Box(420, 80, 620, 112)))
        assertEquals("Healing Vision ~Angelic mix~", ResultScreenParser(catalog).parse(wrapped).title?.value)
    }
}
