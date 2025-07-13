package com.tehoubi.ddrtracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PlayEntity::class, UserSongEntity::class, SessionEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class DdrDatabase : RoomDatabase() {
    abstract fun playDao(): PlayDao
    abstract fun userSongDao(): UserSongDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "ddr-tracker.db"

        /** v2: plays belong to sessions. Sessions are rebuilt on the next write. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plays ADD COLUMN session_id TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plays_session_id ON plays (session_id)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        started_at INTEGER NOT NULL,
                        ended_at INTEGER NOT NULL,
                        play_count INTEGER NOT NULL,
                        average_score INTEGER NOT NULL,
                        best_score INTEGER NOT NULL,
                        average_ex_ratio REAL NOT NULL,
                        steps_hit INTEGER NOT NULL,
                        highest_level INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sessions_started_at ON sessions (started_at)")
            }
        }

        /** v3: per-play leaderboard upload state. Existing plays queue for upload. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plays ADD COLUMN leaderboard_status TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE plays ADD COLUMN leaderboard_error TEXT")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
