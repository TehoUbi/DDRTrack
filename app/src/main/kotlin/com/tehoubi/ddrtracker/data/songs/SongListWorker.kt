package com.tehoubi.ddrtracker.data.songs

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Weekly check for songs added since the last one. */
@HiltWorker
class SongListWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val store: SongListStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        if (store.refresh() !is SongListRefresh.Failed) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
}
