package com.tehoubi.ddrtracker.di

import com.tehoubi.ddrtracker.sync.SyncScheduler
import com.tehoubi.ddrtracker.sync.SyncTrigger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindSyncTrigger(impl: SyncScheduler): SyncTrigger
}
