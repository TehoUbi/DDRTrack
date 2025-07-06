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

@Serializable
data class RegisterResponse(val playerId: String, val displayName: String, val token: String)

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
