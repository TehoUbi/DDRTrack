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

    @Query("UPDATE plays SET session_id = :sessionId WHERE id = :id")
    suspend fun setSession(id: String, sessionId: String)
}

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
