package com.tehoubi.ddrtracker.di

import android.content.Context
import androidx.room.Room
import com.tehoubi.ddrtracker.core.songs.SongCatalog
import com.tehoubi.ddrtracker.data.local.DdrDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DdrDatabase =
        Room.databaseBuilder(context, DdrDatabase::class.java, DdrDatabase.NAME).build()

    @Provides
    @Singleton
    fun provideSongCatalog(): SongCatalog = SongCatalog.bundled()

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
