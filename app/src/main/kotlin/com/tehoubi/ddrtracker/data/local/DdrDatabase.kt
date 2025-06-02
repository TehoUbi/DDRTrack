package com.tehoubi.ddrtracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PlayEntity::class, UserSongEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DdrDatabase : RoomDatabase() {
    abstract fun playDao(): PlayDao
    abstract fun userSongDao(): UserSongDao

    companion object {
        const val NAME = "ddr-tracker.db"
    }
}
