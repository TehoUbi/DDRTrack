package com.tehoubi.ddrtracker.core.scoring

import com.tehoubi.ddrtracker.core.api.ScoreSubmission
import com.tehoubi.ddrtracker.core.api.SubmissionRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubmissionRulesTest {

    private val now = 1_780_000_000_000L
    private val ok = ScoreSubmission(
        clientId = "p1", songTitle = "Butterfly", style = "SINGLE", difficulty = "EXPERT", level = 12,
        score = 999_880, exScore = 1482, marvelous = 452, perfect = 12, great = 0, good = 0, ok = 34, miss = 0,
        maxCombo = 464, failed = false, playedAt = now - 1000,
    )

    @Test
    fun `consistent play passes`() = assertNull(SubmissionRules.rejectionReason(ok, now))

    @Test
    fun `inconsistent plays are rejected with a reason`() {
        assertEquals("EX score doesn't match the judgments", SubmissionRules.rejectionReason(ok.copy(exScore = 1480), now))
        assertEquals("score doesn't follow from the judgments", SubmissionRules.rejectionReason(ok.copy(score = 900_000), now))
        assertEquals("unknown difficulty HARD", SubmissionRules.rejectionReason(ok.copy(difficulty = "HARD"), now))
        assertEquals("level must be 1-19", SubmissionRules.rejectionReason(ok.copy(level = 20), now))
        assertEquals("playedAt is in the future", SubmissionRules.rejectionReason(ok.copy(playedAt = now + 2 * SubmissionRules.FUTURE_SLACK_MS), now))
        assertEquals("judgment counts cannot be negative", SubmissionRules.rejectionReason(ok.copy(miss = -1), now))
    }

    @Test
    fun `failed plays skip the score formula`() {
        assertNull(SubmissionRules.rejectionReason(ok.copy(score = 500_000, failed = true), now))
    }
}
