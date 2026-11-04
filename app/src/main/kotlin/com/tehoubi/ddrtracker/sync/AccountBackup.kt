package com.tehoubi.ddrtracker.sync

import com.tehoubi.ddrtracker.core.api.LogEntry
import com.tehoubi.ddrtracker.core.songs.SongKey
import com.tehoubi.ddrtracker.data.OfflineFirstPlayRepository
import com.tehoubi.ddrtracker.data.local.DdrDatabase
import com.tehoubi.ddrtracker.data.local.LeaderboardStatus
import com.tehoubi.ddrtracker.data.local.PlayEntity
import com.tehoubi.ddrtracker.data.remote.LeaderboardClient
import com.tehoubi.ddrtracker.data.settings.SettingsRepository
import com.tehoubi.ddrtracker.data.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BackupStatus {
    data object Idle : BackupStatus
    data object Running : BackupStatus
    data class Done(val atMillis: Long, val pushed: Int, val pulled: Int) : BackupStatus
    data class Failed(val message: String) : BackupStatus
}

/**
 * Backs the whole play log up to the player's leaderboard account, privately,
 * and restores it on another phone. Unlike the leaderboard upload it keeps
 * every play as saved: FAST/SLOW, notes, and plays the leaderboard rejects.
 *
 * Room stays the source of truth. A run pushes rows changed since the last
 * one (deletions as tombstones), then pulls whatever changed on the server
 * since the last revision it saw; the newer copy of a play wins.
 */
@Singleton
class AccountBackup @Inject constructor(
    private val db: DdrDatabase,
    private val client: LeaderboardClient,
    private val repository: OfflineFirstPlayRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    enum class Result { DONE, NOT_CONFIGURED, RETRY }

    private val _status = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val status: StateFlow<BackupStatus> = _status

    private val mutex = Mutex()

    suspend fun run(): Result = mutex.withLock {
        val s = settings.current()
        val account = s.account
        if (account == null || !s.backup) return Result.NOT_CONFIGURED
        _status.value = BackupStatus.Running
        return runCatching {
            val dao = db.playDao()
            var pushed = 0
            while (true) {
                val rows = dao.backupPending(limit = BATCH)
                if (rows.isEmpty()) break
                client.uploadLog(account.serverUrl, account.token, rows.map { it.toLogEntry() })
                rows.forEach { dao.markBackedUp(it.id, it.updatedAt) }
                pushed += rows.size
                if (rows.size < BATCH) break
            }
            var pulled = 0
            var revision = s.backupRevision
            do {
                val page = client.log(account.serverUrl, account.token, revision)
                pulled += repository.mergeFromBackup(page.entries.map { it.toEntity() })
                revision = page.revision
                settings.setBackupRevision(revision)
            } while (page.more)
            val now = clock.millis()
            settings.setBackedUpAt(now)
            _status.value = BackupStatus.Done(now, pushed, pulled)
            Result.DONE
        }.getOrElse { e ->
            _status.value = BackupStatus.Failed(e.userMessage())
            Result.RETRY
        }
    }

    private companion object {
        const val BATCH = 400
    }
}

internal fun PlayEntity.toLogEntry() = LogEntry(
    id = id, songTitle = songTitle, artist = artist, style = style, difficulty = difficulty, level = level,
    score = score, exScore = exScore, grade = grade, lamp = lamp,
    marvelous = marvelous, perfect = perfect, great = great, good = good, ok = ok, miss = miss,
    maxCombo = maxCombo, fast = fast, slow = slow, failed = failed, playedAt = playedAt,
    source = source, scanConfidence = scanConfidence, note = note, updatedAt = updatedAt, deleted = deleted,
)

/** A restored play goes to the leaderboard queue too, in case this account's server hasn't seen it. */
internal fun LogEntry.toEntity() = PlayEntity(
    id = id, songKey = SongKey.of(songTitle), songTitle = songTitle, artist = artist, style = style, difficulty = difficulty,
    level = level, score = score, exScore = exScore, grade = grade, lamp = lamp,
    marvelous = marvelous, perfect = perfect, great = great, good = good, ok = ok, miss = miss,
    maxCombo = maxCombo, fast = fast, slow = slow, failed = failed, playedAt = playedAt, sessionId = null,
    source = source, scanConfidence = scanConfidence, note = note, updatedAt = updatedAt, deleted = deleted,
    leaderboardStatus = LeaderboardStatus.PENDING,
)
