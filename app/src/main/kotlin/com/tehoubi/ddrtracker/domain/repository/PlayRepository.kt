package com.tehoubi.ddrtracker.domain.repository

import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.songs.Song
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.model.PlaySession
import kotlinx.coroutines.flow.Flow

interface PlayRepository {

    /** Every play, newest first. */
    fun observePlays(): Flow<List<Play>>

    fun observePlay(id: String): Flow<Play?>

    /** Plays of one chart, oldest first (the order progression is drawn in). */
    fun observeChart(songKey: String, style: PlayStyle, difficulty: Difficulty): Flow<List<Play>>

    /** Sessions, newest first. */
    fun observeSessions(): Flow<List<PlaySession>>

    /** Songs the player has saved that are not in the bundled catalog. */
    fun observeUserSongs(): Flow<List<Song>>

    suspend fun getPlay(id: String): Play?

    /** Inserts or replaces [play]. */
    suspend fun savePlay(play: Play): Result<Play>

    suspend fun deletePlay(id: String): Result<Unit>

    /** Inserts many plays at once (imports, cloud restore). */
    suspend fun savePlays(plays: List<Play>): Result<Int>
}
