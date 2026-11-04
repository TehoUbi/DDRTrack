package com.tehoubi.ddrtracker.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AccountBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backup: AccountBackup,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (backup.run()) {
        AccountBackup.Result.RETRY -> if (runAttemptCount < 8) Result.retry() else Result.failure()
        else -> Result.success()
    }
}
