package com.tehoubi.ddrtracker.di

import com.tehoubi.ddrtracker.data.OfflineFirstPlayRepository
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindPlayRepository(impl: OfflineFirstPlayRepository): PlayRepository
}
