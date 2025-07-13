package com.tehoubi.ddrtracker.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class LeaderboardSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: LeaderboardSync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (sync.run().result) {
        LeaderboardSync.Result.DONE, LeaderboardSync.Result.NOT_CONFIGURED -> Result.success()
        LeaderboardSync.Result.RETRY -> if (runAttemptCount < 8) Result.retry() else Result.failure()
    }
}
