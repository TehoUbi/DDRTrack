package com.tehoubi.ddrtracker.di

import com.tehoubi.ddrtracker.scan.MlKitTextRecognizer
import com.tehoubi.ddrtracker.scan.TextRecognizerEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {
    @Binds
    abstract fun bindRecognizer(impl: MlKitTextRecognizer): TextRecognizerEngine
}
