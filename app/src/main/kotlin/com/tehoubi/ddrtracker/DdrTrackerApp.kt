package com.tehoubi.ddrtracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.tehoubi.ddrtracker.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DdrTrackerApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        syncScheduler.schedulePeriodic()
    }

    /**
     * Song jackets: each is downloaded once (about 7 KB) and kept in a
     * 20 MB cache, enough for a couple of thousand songs. Jackets don't
     * change, so the server's "revalidate every time" headers are ignored.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("jackets")).maxSizeBytes(20L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}
