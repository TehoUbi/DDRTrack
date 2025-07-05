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
