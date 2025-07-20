package com.tehoubi.ddrtracker.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tehoubi.ddrtracker.cloud.CloudSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Something to poke when local plays change. */
interface SyncTrigger {
    fun playsChanged()
}

/**
 * Runs uploads through WorkManager: a one-off job (debounced a few seconds
 * so a burst of saves goes up as one batch) when plays change, plus a
 * periodic catch-up. Both need a network and back off exponentially.
 */
@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) : SyncTrigger {

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun playsChanged() {
        val request = OneTimeWorkRequestBuilder<LeaderboardSyncWorker>()
            .setConstraints(network)
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(LEADERBOARD_NOW, ExistingWorkPolicy.REPLACE, request)
        cloudNow()
    }

    /** Firestore backup; the worker exits at once if cloud sync is off or unavailable. */
    fun cloudNow() {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(network)
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(CLOUD_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<LeaderboardSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(LEADERBOARD_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        val cloud = PeriodicWorkRequestBuilder<CloudSyncWorker>(12, TimeUnit.HOURS).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(CLOUD_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, cloud)
    }

    companion object {
        const val LEADERBOARD_NOW = "leaderboard-sync"
        const val LEADERBOARD_PERIODIC = "leaderboard-sync-periodic"
        const val CLOUD_NOW = "cloud-sync"
        const val CLOUD_PERIODIC = "cloud-sync-periodic"
    }
}
