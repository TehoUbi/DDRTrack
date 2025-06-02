package com.tehoubi.ddrtracker.domain.repository

import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.songs.Song
import com.tehoubi.ddrtracker.domain.model.Play
import kotlinx.coroutines.flow.Flow

interface PlayRepository {

    /** Every play, newest first. */
    fun observePlays(): Flow<List<Play>>

    fun observePlay(id: String): Flow<Play?>

    /** Plays of one chart, oldest first (the order progression is drawn in). */
    fun observeChart(songKey: String, style: PlayStyle, difficulty: Difficulty): Flow<List<Play>>

    /** Songs the player has saved that are not in the bundled catalog. */
    fun observeUserSongs(): Flow<List<Song>>

    suspend fun getPlay(id: String): Play?

    /** Inserts or replaces [play]. */
    suspend fun savePlay(play: Play): Result<Play>

    suspend fun deletePlay(id: String): Result<Unit>
}
