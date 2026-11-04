package com.tehoubi.ddrtracker.core.api

import kotlinx.serialization.Serializable

/*
 * The leaderboard service's JSON contract, shared by the server and the app
 * so the two can't drift apart. Times are epoch milliseconds; style,
 * difficulty, grade and lamp are enum names from core.model.
 */

@Serializable
data class ApiError(val error: String)

@Serializable
data class RegisterRequest(val displayName: String)

/**
 * A new or recovered account. [recoveryCode] is only present when one was
 * just made (at sign-up): it's shown to the player once and can't be read
 * back, only replaced.
 */
@Serializable
data class RegisterResponse(val playerId: String, val displayName: String, val token: String, val recoveryCode: String? = null)

@Serializable
data class RecoverRequest(val recoveryCode: String)

@Serializable
data class RecoveryCodeResponse(val recoveryCode: String)

/**
 * One play in a player's private log, everything the app stores about it.
 * Deleted plays travel as tombstones ([deleted]) so the deletion reaches the
 * player's other phones; [updatedAt] decides which copy of an entry wins.
 */
@Serializable
data class LogEntry(
    val id: String,
    val songTitle: String,
    val artist: String? = null,
    val style: String,
    val difficulty: String,
    val level: Int,
    val score: Int,
    val exScore: Int,
    val grade: String,
    val lamp: String,
    val marvelous: Int,
    val perfect: Int,
    val great: Int,
    val good: Int,
    val ok: Int,
    val miss: Int,
    val maxCombo: Int,
    val fast: Int? = null,
    val slow: Int? = null,
    val failed: Boolean,
    val playedAt: Long,
    val source: String,
    val scanConfidence: Float? = null,
    val note: String? = null,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class LogUpload(val entries: List<LogEntry>)

/** How many entries were stored; older copies than the server's are skipped, not errors. */
@Serializable
data class LogUploadResponse(val stored: Int)

/**
 * Log entries changed after a revision, oldest change first. Pass [revision]
 * back as `after` for the next page; [more] says whether there is one.
 */
@Serializable
data class LogPage(val entries: List<LogEntry>, val revision: Long, val more: Boolean)

@Serializable
data class UpdatePlayerRequest(val displayName: String)

@Serializable
data class PlayerSummary(val playerId: String, val displayName: String)

@Serializable
data class PlayerProfile(
    val playerId: String,
    val displayName: String,
    val createdAt: Long,
    val plays: Int,
    val charts: Int,
    val aaaCharts: Int,
    val fullComboCharts: Int,
    val averageBest: Int?,
    val lastPlayedAt: Long?,
)

@Serializable
data class HealthResponse(val status: String, val database: String)

/** One play as the app submits it. [clientId] is the app's play id. */
@Serializable
data class ScoreSubmission(
    val clientId: String,
    val songTitle: String,
    val style: String,
    val difficulty: String,
    val level: Int,
    val score: Int,
    val exScore: Int,
    val marvelous: Int,
    val perfect: Int,
    val great: Int,
    val good: Int,
    val ok: Int,
    val miss: Int,
    val maxCombo: Int,
    val failed: Boolean,
    val playedAt: Long,
)

@Serializable
data class SubmitScoresRequest(val scores: List<ScoreSubmission>)

/**
 * One of a player's plays as anyone can see it, for their profile page.
 * Grade and lamp are the server's (recomputed on submission); FAST/SLOW
 * aren't uploaded, so they aren't here.
 */
@Serializable
data class PublicPlay(
    val id: String,
    val songTitle: String,
    val style: String,
    val difficulty: String,
    val level: Int,
    val score: Int,
    val exScore: Int,
    val grade: String,
    val lamp: String,
    val marvelous: Int,
    val perfect: Int,
    val great: Int,
    val good: Int,
    val ok: Int,
    val miss: Int,
    val maxCombo: Int,
    val failed: Boolean,
    val playedAt: Long,
)

@Serializable
data class RejectedScore(val clientId: String, val reason: String)

/** Already-stored client ids count as accepted, so retries are safe. */
@Serializable
data class SubmitScoresResponse(val accepted: List<String>, val rejected: List<RejectedScore>)

@Serializable
data class LeaderboardEntry(
    val rank: Int,
    val playerId: String,
    val displayName: String,
    val score: Int,
    val exScore: Int,
    val grade: String,
    val lamp: String,
    val playedAt: Long,
)

@Serializable
data class Leaderboard(
    val songKey: String,
    val songTitle: String,
    val style: String,
    val difficulty: String,
    val level: Int,
    val players: Int,
    val entries: List<LeaderboardEntry>,
    /** The caller's own entry when authenticated and ranked, even outside the top. */
    val you: LeaderboardEntry? = null,
    /** Share of other players the caller beats, 0..1. */
    val youBeat: Double? = null,
)

@Serializable
data class ChartSummary(
    val songKey: String,
    val songTitle: String,
    val style: String,
    val difficulty: String,
    val level: Int,
    val players: Int,
    val plays: Int,
    val topScore: Int,
)

@Serializable
data class ComparedChart(
    val songKey: String,
    val songTitle: String,
    val style: String,
    val difficulty: String,
    val level: Int,
    val playerScore: Int,
    val rivalScore: Int,
) {
    val delta: Int get() = playerScore - rivalScore
}

@Serializable
data class Comparison(
    val player: PlayerSummary,
    val rival: PlayerSummary,
    val wins: Int,
    val losses: Int,
    val ties: Int,
    val charts: List<ComparedChart>,
)
