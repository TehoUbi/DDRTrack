package com.tehoubi.ddrtracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plays",
    indices = [
        Index(value = ["song_key", "style", "difficulty"]),
        Index(value = ["played_at"]),
        Index(value = ["session_id"]),
    ],
)
data class PlayEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "song_key") val songKey: String,
    @ColumnInfo(name = "song_title") val songTitle: String,
    val artist: String?,
    val style: String,
    val difficulty: String,
    val level: Int,
    val score: Int,
    @ColumnInfo(name = "ex_score") val exScore: Int,
    val grade: String,
    val lamp: String,
    val marvelous: Int,
    val perfect: Int,
    val great: Int,
    val good: Int,
    val ok: Int,
    val miss: Int,
    @ColumnInfo(name = "max_combo") val maxCombo: Int,
    val fast: Int?,
    val slow: Int?,
    val failed: Boolean,
    @ColumnInfo(name = "played_at") val playedAt: Long,
    @ColumnInfo(name = "session_id") val sessionId: String? = null,
    val source: String,
    @ColumnInfo(name = "scan_confidence") val scanConfidence: Float?,
    val note: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Kept as a tombstone so a deletion can be synced before the row goes. */
    val deleted: Boolean = false,
)

@Entity(tableName = "user_songs")
data class UserSongEntity(
    @PrimaryKey val key: String,
    val title: String,
    val artist: String?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** Derived from plays by Sessions.group; rebuilt whenever plays change. */
@Entity(tableName = "sessions", indices = [Index(value = ["started_at"])])
data class SessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "average_score") val averageScore: Int,
    @ColumnInfo(name = "best_score") val bestScore: Int,
    @ColumnInfo(name = "average_ex_ratio") val averageExRatio: Double,
    @ColumnInfo(name = "steps_hit") val stepsHit: Int,
    @ColumnInfo(name = "highest_level") val highestLevel: Int,
)
