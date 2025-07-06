package com.tehoubi.ddrtracker.core.api

import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.LEVEL_RANGE
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.scoring.DdrScoring
import com.tehoubi.ddrtracker.core.songs.SongKey

/**
 * What the leaderboard accepts. The server enforces it; the app runs the
 * same check first so it doesn't upload plays that will bounce.
 *
 * Leaderboards compare players, so a play has to be internally consistent:
 * the EX score must match the judgments exactly and the money score must
 * follow from them (allowing dropped freezes). The local log is more
 * forgiving and keeps whatever the player typed.
 */
object SubmissionRules {

    const val MAX_BATCH = 500

    /** Plays may be stamped a little ahead to tolerate clock skew. */
    const val FUTURE_SLACK_MS = 24L * 60 * 60 * 1000

    fun judgments(s: ScoreSubmission): Judgments? = runCatching {
        Judgments(s.marvelous, s.perfect, s.great, s.good, s.ok, s.miss)
    }.getOrNull()

    /** Returns why [s] is rejected, or null if it is fine. */
    fun rejectionReason(s: ScoreSubmission, nowMillis: Long): String? {
        if (s.clientId.isBlank() || s.clientId.length > 64) return "clientId must be 1-64 characters"
        if (s.songTitle.isBlank() || s.songTitle.length > 200) return "songTitle must be 1-200 characters"
        if (SongKey.of(s.songTitle).isEmpty()) return "songTitle has no usable characters"
        if (PlayStyle.entries.none { it.name == s.style }) return "unknown style ${s.style}"
        if (Difficulty.entries.none { it.name == s.difficulty }) return "unknown difficulty ${s.difficulty}"
        if (s.level !in LEVEL_RANGE) return "level must be 1-19"
        if (s.score !in 0..DdrScoring.MAX_SCORE || s.score % 10 != 0) return "score must be a multiple of 10 up to 1,000,000"
        val j = judgments(s) ?: return "judgment counts cannot be negative"
        if (j.total == 0) return "no judgments"
        if (s.exScore != DdrScoring.exScore(j)) return "EX score doesn't match the judgments"
        if (!s.failed && DdrScoring.inferNg(j, s.score) == null) return "score doesn't follow from the judgments"
        if (s.maxCombo < 0 || s.maxCombo > j.total) return "max combo is more than the chart's objects"
        if (s.playedAt > nowMillis + FUTURE_SLACK_MS) return "playedAt is in the future"
        return null
    }
}
