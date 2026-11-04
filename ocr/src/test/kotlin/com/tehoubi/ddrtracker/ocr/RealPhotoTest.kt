package com.tehoubi.ddrtracker.ocr

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.ocr.ResultScreenParser
import com.tehoubi.ddrtracker.core.ocr.StatLabel
import com.tehoubi.ddrtracker.core.songs.SongCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Photos of real DDR WORLD result screens (samples/), read end to end
 * exactly as the app reads a photo: the same models, the same crops and
 * re-reads, the same parser and validator. Ground truth is what the screens
 * show. Skipped when the photos aren't checked out.
 */
class RealPhotoTest {

    private data class Truth(
        val title: String, val difficulty: Difficulty, val level: Int,
        val score: Int, val judgments: Judgments, val ex: Int, val combo: Int, val total: Int,
        val fast: Int, val slow: Int, val grade: Grade, val lamp: ClearLamp,
    )

    private val reader by lazy { ResultScreenReader(Samples.ocr) }
    private val parser = ResultScreenParser(SongCatalog.bundled())

    private fun check(photo: String, t: Truth) {
        val reading = reader.read(Samples.photo(photo), parser)
        val c = reading.scan
        val r = c.result
        val got = buildMap {
            put("title", r.title?.value); put("style", r.style?.value); put("difficulty", r.difficulty?.value)
            put("level", r.level?.value); put("score", r.score?.value); put("judgments", if (r.hasAllJudgments) r.judgments() else null)
            put("ex", r.exScore?.value); put("combo", r.maxCombo?.value); put("total", r.comboTotal?.value)
            put("fast", r.stats[StatLabel.FAST]?.value); put("slow", r.stats[StatLabel.SLOW]?.value)
            put("grade", r.grade?.value); put("lamp", r.lamp?.value)
        }
        val want = mapOf(
            "title" to t.title, "style" to PlayStyle.SINGLE, "difficulty" to t.difficulty,
            "level" to t.level, "score" to t.score, "judgments" to t.judgments,
            "ex" to t.ex, "combo" to t.combo, "total" to t.total,
            "fast" to t.fast, "slow" to t.slow, "grade" to t.grade, "lamp" to t.lamp,
        )
        val wrong = want.filter { (k, v) -> got[k] != v }.map { (k, v) -> "$k: want $v, got ${got[k]}" }
        assertTrue(wrong.isEmpty(), "$photo (${reading.passes} passes)\n" + wrong.joinToString("\n") + "\n\n" + reading.page.text)
        assertTrue(c.isConsistent, c.issues.toString())
        assertTrue(r.title!!.confidence >= 0.7f, "title confidence ${r.title!!.confidence}")
    }

    @Test
    fun `scarlet moon`() = check(
        "sample1.jpg",
        Truth("Scarlet Moon", Difficulty.EXPERT, 12, 891_650, Judgments(191, 129, 86, 3, 5, 8), 932, 140, 417, 59, 159, Grade.AA_MINUS, ClearLamp.CLEAR),
    )

    @Test
    fun `himawari sunset, a small low-resolution photo`() = check(
        "sample2.jpg",
        Truth("向日葵サンセット", Difficulty.EXPERT, 13, 991_280, Judgments(252, 80, 7, 0, 18, 0), 977, 339, 339, 68, 19, Grade.AAA, ClearLamp.GREAT_FULL_COMBO),
    )

    @Test
    fun `buta thunder`() = check(
        "sample3.jpg",
        Truth("ブタサンダー", Difficulty.EXPERT, 13, 999_910, Judgments(434, 9, 0, 0, 13, 0), 1359, 443, 443, 2, 7, Grade.AAA, ClearLamp.PERFECT_FULL_COMBO),
    )

    @Test
    fun `trip machine evolution, landscape with the rival panel beside it`() = check(
        "sample4.jpg",
        Truth("TRIP MACHINE EVOLUTION", Difficulty.CHALLENGE, 18, 913_890, Judgments(411, 147, 85, 7, 31, 18), 1705, 227, 668, 169, 70, Grade.AA, ClearLamp.CLEAR),
    )

    @Test
    fun `ice cream magic, glare across the panel`() = check(
        "sample5.jpg",
        Truth("アイスクリームマジック", Difficulty.DIFFICULT, 10, 940_620, Judgments(148, 81, 41, 0, 12, 0), 683, 270, 270, 89, 33, Grade.AA, ClearLamp.GREAT_FULL_COMBO),
    )
}
