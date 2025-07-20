package com.tehoubi.ddrtracker.core.stats

/**
 * Conflict rule for syncing one player's log between devices: last writer
 * wins, by the row's updated-at stamp. Deletions are ordinary rows with a
 * deleted flag, so a delete and an edit resolve the same way. A tie keeps
 * the local row, which makes re-pulling something you just pushed a no-op.
 */
object SyncMerge {
    fun remoteWins(localUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean =
        localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt
}
