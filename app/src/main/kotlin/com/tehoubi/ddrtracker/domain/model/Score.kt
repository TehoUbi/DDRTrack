package com.tehoubi.ddrtracker.domain.model

import java.time.Instant

/**
 * Represents a single DDR play session result.
 */
data class Score(
    val id: String,
    val songTitle: String,
    val difficulty: Difficulty,
    val score: Int,           // Numeric score (0–1,000,000)
    val grade: Grade,
    val maxCombo: Int,
    val perfect: Int,
    val great: Int,
    val good: Int,
    val bad: Int,
    val miss: Int,
    val playedAt: Instant,
    val userId: String,
)

enum class Difficulty {
    BEGINNER, BASIC, DIFFICULT, EXPERT, CHALLENGE
}

enum class Grade {
    AAA, AA_PLUS, AA, AA_MINUS,
    A_PLUS, A, A_MINUS,
    B, C, D, E
}
