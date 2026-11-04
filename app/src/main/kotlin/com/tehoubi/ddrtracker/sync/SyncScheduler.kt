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
import com.tehoubi.ddrtracker.data.songs.SongListWorker
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
 * periodic catch-up. Both need a network and back off exponentially. Also
 * the weekly song-list check.
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
        backupNow()
    }

    /** Account backup; the worker exits at once if there's no account or backup is off. */
    fun backupNow() {
        val request = OneTimeWorkRequestBuilder<AccountBackupWorker>()
            .setConstraints(network)
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(BACKUP_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<LeaderboardSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(LEADERBOARD_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        // Also picks up plays changed on the player's other phones.
        val backup = PeriodicWorkRequestBuilder<AccountBackupWorker>(12, TimeUnit.HOURS).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(BACKUP_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, backup)
        // The Firestore backup this replaced.
        WorkManager.getInstance(context).cancelUniqueWork("cloud-sync-periodic")
        // New songs arrive every few weeks; the first run is right after install.
        val songs = PeriodicWorkRequestBuilder<SongListWorker>(7, TimeUnit.DAYS)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(SONGS_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, songs)
    }

    companion object {
        const val LEADERBOARD_NOW = "leaderboard-sync"
        const val LEADERBOARD_PERIODIC = "leaderboard-sync-periodic"
        const val BACKUP_NOW = "account-backup"
        const val BACKUP_PERIODIC = "account-backup-periodic"
        const val SONGS_PERIODIC = "song-list-periodic"
    }
}
