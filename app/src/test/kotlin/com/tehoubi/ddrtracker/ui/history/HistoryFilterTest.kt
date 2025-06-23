package com.tehoubi.ddrtracker.ui.history

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.model.PlaySource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryFilterTest {

    private fun play(id: String, title: String, d: Difficulty, level: Int, score: Int, day: Int, fc: Boolean = false, style: PlayStyle = PlayStyle.SINGLE) = Play(
        id = id, songTitle = title, style = style, difficulty = d, level = level, score = score, exScore = 0,
        grade = Grade.A, lamp = if (fc) ClearLamp.FULL_COMBO else ClearLamp.CLEAR, judgments = Judgments(10),
        maxCombo = 0, playedAt = Instant.parse("2026-05-%02dT20:00:00Z".format(day)), source = PlaySource.MANUAL,
    )

    private val plays = listOf(
        play("1", "PARANOiA", Difficulty.EXPERT, 14, 900_000, 1),
        play("2", "PARANOiA Rebirth", Difficulty.CHALLENGE, 16, 800_000, 2, fc = true),
        play("3", "Butterfly", Difficulty.BASIC, 6, 990_000, 3, fc = true),
        play("4", "MAX 300", Difficulty.EXPERT, 15, 700_000, 4, style = PlayStyle.DOUBLE),
    )

    @Test
    fun `default is everything newest first`() {
        assertFalse(HistoryFilter().isActive)
        assertEquals(listOf("4", "3", "2", "1"), HistoryFilter().apply(plays, emptySet()).map { it.id })
    }

    @Test
    fun `search ignores case and punctuation`() {
        assertEquals(setOf("1", "2"), HistoryFilter(query = "paranoia").apply(plays, emptySet()).map { it.id }.toSet())
        assertEquals(listOf("4"), HistoryFilter(query = "max300").apply(plays, emptySet()).map { it.id })
    }

    @Test
    fun `filters combine`() {
        val f = HistoryFilter(difficulties = setOf(Difficulty.EXPERT, Difficulty.CHALLENGE), levels = 14..15, style = PlayStyle.SINGLE)
        assertTrue(f.isActive)
        assertEquals(listOf("1"), f.apply(plays, emptySet()).map { it.id })
        assertEquals(listOf("3", "2"), HistoryFilter(fullCombosOnly = true).apply(plays, emptySet()).map { it.id })
        assertEquals(listOf("2"), HistoryFilter(bestsOnly = true).apply(plays, setOf("2")).map { it.id })
    }

    @Test
    fun `sorts`() {
        assertEquals(listOf("3", "1", "2", "4"), HistoryFilter(sort = HistorySort.SCORE).apply(plays, emptySet()).map { it.id })
        assertEquals(listOf("2", "4", "1", "3"), HistoryFilter(sort = HistorySort.LEVEL).apply(plays, emptySet()).map { it.id })
        assertEquals(listOf("1", "2", "3", "4"), HistoryFilter(sort = HistorySort.OLDEST).apply(plays, emptySet()).map { it.id })
    }
}
