package com.tehoubi.ddrtracker.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayDao {

    @Query("SELECT * FROM plays WHERE deleted = 0 ORDER BY played_at DESC")
    fun observeAll(): Flow<List<PlayEntity>>

    @Query("SELECT * FROM plays WHERE id = :id AND deleted = 0")
    fun observeById(id: String): Flow<PlayEntity?>

    @Query("SELECT * FROM plays WHERE id = :id")
    suspend fun getById(id: String): PlayEntity?

    @Query(
        """
        SELECT * FROM plays
        WHERE song_key = :songKey AND style = :style AND difficulty = :difficulty AND deleted = 0
        ORDER BY played_at ASC
        """
    )
    fun observeChart(songKey: String, style: String, difficulty: String): Flow<List<PlayEntity>>

    @Query("SELECT * FROM plays WHERE deleted = 0 ORDER BY played_at ASC")
    suspend fun getAllChronological(): List<PlayEntity>

    @Upsert
    suspend fun upsert(play: PlayEntity)

    @Upsert
    suspend fun upsertAll(plays: List<PlayEntity>)

    @Query("UPDATE plays SET deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    @Query("SELECT COUNT(*) FROM plays WHERE deleted = 0")
    suspend fun count(): Int

    /** Live plays not yet on the leaderboard, oldest first. */
    @Query("SELECT * FROM plays WHERE deleted = 0 AND leaderboard_status = 'PENDING' ORDER BY played_at LIMIT :limit")
    suspend fun pendingUpload(limit: Int): List<PlayEntity>

    /** Deleted plays the server still has. */
    @Query("SELECT * FROM plays WHERE deleted = 1 AND leaderboard_status = 'SUBMITTED'")
    suspend fun pendingRemoval(): List<PlayEntity>

    @Query("UPDATE plays SET leaderboard_status = :status, leaderboard_error = :error WHERE id = :id AND updated_at = :updatedAt")
    suspend fun setLeaderboardStatus(id: String, updatedAt: Long, status: String, error: String?)

    /** Deleted before ever being uploaded: nothing to tell the server. */
    @Query("UPDATE plays SET leaderboard_status = 'REMOVED' WHERE deleted = 1 AND leaderboard_status IN ('PENDING', 'REJECTED')")
    suspend fun dropUnsubmittedTombstones()

    /** Re-queue everything, e.g. after registering on a new server. */
    @Query("UPDATE plays SET leaderboard_status = 'PENDING', leaderboard_error = NULL WHERE deleted = 0")
    suspend fun requeueAll()

    @Query("SELECT leaderboard_status AS status, COUNT(*) AS count FROM plays WHERE deleted = 0 GROUP BY leaderboard_status")
    fun observeLeaderboardCounts(): Flow<List<StatusCount>>

    @Query("UPDATE plays SET session_id = :sessionId WHERE id = :id")
    suspend fun setSession(id: String, sessionId: String)
}

data class StatusCount(val status: String, val count: Int)

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY started_at DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions")
    suspend fun getAll(): List<SessionEntity>

    @Query("DELETE FROM sessions")
    suspend fun clear()

    @Upsert
    suspend fun upsertAll(sessions: List<SessionEntity>)
}

@Dao
interface UserSongDao {
    @Query("SELECT * FROM user_songs ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<UserSongEntity>>

    @Upsert
    suspend fun upsert(song: UserSongEntity)
}
