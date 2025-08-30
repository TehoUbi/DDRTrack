package com.tehoubi.ddrtracker.data.backup

import com.tehoubi.ddrtracker.core.api.ScoreSubmission
import com.tehoubi.ddrtracker.core.api.SubmissionRules
import com.tehoubi.ddrtracker.core.stats.Sessions
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** samples/sample-backup.json must restore cleanly and be accepted by a leaderboard. */
class SampleBackupTest {

    @Test
    fun `sample backup restores and uploads`() {
        val (plays, skipped) = BackupCodec.decode(File("../samples/sample-backup.json").readText())
        assertEquals(0, skipped)
        assertTrue(plays.size >= 200)
        assertTrue(Sessions.group(plays).size >= 25)
        val now = System.currentTimeMillis()
        val rejected = plays.mapNotNull { p ->
            val j = p.judgments
            SubmissionRules.rejectionReason(
                ScoreSubmission(
                    p.id, p.songTitle, p.style.name, p.difficulty.name, p.level, p.score, p.exScore,
                    j.marvelous, j.perfect, j.great, j.good, j.ok, j.miss, p.maxCombo, p.failed, p.playedAt.toEpochMilli(),
                ),
                now,
            )?.let { p.id to it }
        }
        assertEquals(emptyList(), rejected)
    }
}
