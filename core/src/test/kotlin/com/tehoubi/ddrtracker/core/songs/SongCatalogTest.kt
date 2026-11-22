package com.tehoubi.ddrtracker.core.songs

import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.PlayStyle
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
        assertTrue(catalog.size > 1500)
        assertNotNull(catalog.find("PARANOiA"))
    }

    @Test
    fun `ocr spellings find the right song`() {
        assertEquals("PARANOiA", catalog.bestMatch(listOf("PARANOIA"))?.song?.title)
        assertEquals("MAX 300", catalog.bestMatch(listOf("MAX 3OO"))?.song?.title)
        // The game, and the catalog, use fullwidth tildes.
        assertEquals("Healing Vision ～Angelic mix～", catalog.bestMatch(listOf("Healing Vision ~Angelic mix~"))?.song?.title)
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
        val s = catalog.suggest("para", limit = 40).map { it.title }
        assertTrue("PARANOiA" in s, s.toString())
        assertTrue("Seta Para Cima↑↑" in s, s.toString())
        val firstOther = s.indexOfFirst { !it.lowercase().startsWith("para") }
        assertTrue(firstOther > 0 && s.drop(firstOther).none { it.lowercase().startsWith("para") }, s.toString())
    }

    @Test
    fun `japanese titles are found by their romanized names`() {
        assertTrue("向日葵サンセット" in catalog.suggest("himawari sun").map { it.title })
        assertEquals("ブタサンダー", catalog.suggest("buta thunder").first().title)
    }

    @Test
    fun `partly read japanese titles still match`() {
        // Small kana are the first characters to go missing.
        assertEquals("向日葵サンセット", catalog.bestMatch(listOf("向日葵サセ"))?.song?.title)
        assertEquals("アイスクリームマジック", catalog.bestMatch(listOf("アイスクリーマック"))?.song?.title)
    }

    @Test
    fun `the chart that was read picks between similar titles`() {
        val ex = ChartHint(PlayStyle.SINGLE, Difficulty.EXPERT, 12)
        // "PARANOiA" (ESP 11) and "PARANOIA EVOLUTION" (ESP 12) are both close to this misread.
        assertEquals("PARANOIA EVOLUTION", catalog.bestMatch(listOf("PARANOIA EVOLUTI"), chart = ex)?.song?.title)
        val scarlet = catalog.find("Scarlet Moon")!!
        assertEquals(12, scarlet.level(PlayStyle.SINGLE, Difficulty.EXPERT))
        assertEquals(null, scarlet.level(PlayStyle.SINGLE, Difficulty.CHALLENGE))
        assertEquals(9, scarlet.level(PlayStyle.DOUBLE, Difficulty.DIFFICULT))
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
        assertEquals("Healing Vision ～Angelic mix～", ResultScreenParser(catalog).parse(wrapped).title?.value)
    }

    @Test
    fun `skipping titles by length never changes the match`() {
        // bestMatch skips songs whose length alone rules them out. Compare it
        // with checking every song, on misreads of every 25th title.
        val songs = catalog.songs
        val keys = songs.map { SongCatalog.matchKey(it.title) }
        val misreads = songs.filterIndexed { i, _ -> i % 25 == 0 }.flatMap { s ->
            val t = s.title
            listOf(t, t.drop(1), t.dropLast(2), t.replace('O', '0').replace('S', '5'), "$t NEW", t.take(t.length / 2))
        }
        for (text in misreads) {
            val key = SongCatalog.matchKey(text)
            if (key.length < 2) continue
            var want: Song? = null
            var wantSim = 0f
            songs.forEachIndexed { i, song ->
                val sim = SongCatalog.similarity(key, keys[i])
                if (sim >= SongCatalog.DEFAULT_THRESHOLD && (want == null || sim > wantSim)) { want = song; wantSim = sim }
            }
            val got = catalog.bestMatch(listOf(text))
            assertEquals(want, got?.song, text)
            if (got != null) assertEquals(wantSim, got.similarity, text)
        }
    }
}
