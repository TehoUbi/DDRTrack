package com.tehoubi.ddrtracker.data

import com.tehoubi.ddrtracker.domain.model.Score
import com.tehoubi.ddrtracker.domain.repository.ScoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Stand-in until the Room database lands; scores live for the process lifetime. */
@Singleton
class InMemoryScoreRepository @Inject constructor() : ScoreRepository {

    private val scores = MutableStateFlow<List<Score>>(emptyList())

    override fun observeScores(userId: String): Flow<List<Score>> =
        scores.map { all -> all.filter { it.userId == userId }.sortedByDescending { it.playedAt } }

    override suspend fun getLeaderboard(songTitle: String, difficulty: String): Result<List<Score>> =
        Result.success(
            scores.value
                .filter { it.songTitle == songTitle && it.difficulty.name == difficulty }
                .sortedByDescending { it.score }
        )

    override suspend fun saveScore(score: Score): Result<Unit> {
        scores.update { it + score }
        return Result.success(Unit)
    }

    override suspend fun deleteScore(scoreId: String): Result<Unit> {
        scores.update { list -> list.filterNot { it.id == scoreId } }
        return Result.success(Unit)
    }
}
