package com.tehoubi.ddrtracker.domain.repository

import com.tehoubi.ddrtracker.domain.model.Score
import kotlinx.coroutines.flow.Flow

interface ScoreRepository {

    /** Observe all scores for the current user, ordered by most recent. */
    fun observeScores(userId: String): Flow<List<Score>>

    /** Fetch leaderboard scores for a specific song + difficulty. */
    suspend fun getLeaderboard(songTitle: String, difficulty: String): Result<List<Score>>

    /** Save a newly scanned score locally and sync to remote. */
    suspend fun saveScore(score: Score): Result<Unit>

    /** Delete a score by ID. */
    suspend fun deleteScore(scoreId: String): Result<Unit>
}
