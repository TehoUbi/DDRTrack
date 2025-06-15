package com.tehoubi.ddrtracker.ui.review

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.ocr.StatLabel
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayFormTest {

    private val complete = PlayForm(
        title = "Butterfly", difficulty = Difficulty.EXPERT, level = "12", score = "999880", exScore = "1482",
        judgments = mapOf(
            StatLabel.MARVELOUS to "452", StatLabel.PERFECT to "12", StatLabel.GREAT to "0",
            StatLabel.GOOD to "0", StatLabel.OK to "34", StatLabel.MISS to "0",
        ),
        playedAt = Instant.parse("2026-04-29T04:00:00Z"),
    )

    @Test
    fun `a complete form saves with derived grade and lamp`() {
        val c = complete.check()
        assertTrue(c.canSave, c.errors.toString())
        assertEquals(Grade.AAA, c.play?.grade)
        assertEquals(ClearLamp.PERFECT_FULL_COMBO, c.play?.lamp)
        assertTrue(c.issues.isEmpty(), c.issues.toString())
    }

    @Test
    fun `an empty form lists what is required`() {
        val c = PlayForm(playedAt = Instant.EPOCH).check()
        assertFalse(c.canSave)
        assertTrue(listOf("title", "difficulty", "level", "score", "marvelous", "miss").all { it in c.errors })
    }

    @Test
    fun `scores must end in zero`() {
        assertEquals("Scores always end in 0", complete.copy(score = "999885").check().errors["score"])
    }

    @Test
    fun `level is bounded`() {
        assertNotNull(complete.copy(level = "20").check().errors["level"])
        assertNull(complete.copy(level = "19").check().errors["level"])
    }

    @Test
    fun `missing EX score is filled from the judgments`() {
        assertEquals(1482, complete.copy(exScore = "").check().play?.exScore)
    }

    @Test
    fun `inconsistent numbers warn but still save`() {
        val c = complete.withJudgment(StatLabel.MARVELOUS, "450").check()
        assertTrue(c.canSave)
        assertTrue(c.issues.any { it.field == "exScore" })
    }

    @Test
    fun `typed input keeps digits only`() {
        assertEquals("1234", "1,2a34".digitsOnly())
    }
}
