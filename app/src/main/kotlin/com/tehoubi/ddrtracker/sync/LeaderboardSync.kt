package com.tehoubi.ddrtracker.sync

import com.tehoubi.ddrtracker.core.api.ScoreSubmission
import com.tehoubi.ddrtracker.core.api.SubmissionRules
import com.tehoubi.ddrtracker.data.local.DdrDatabase
import com.tehoubi.ddrtracker.data.local.LeaderboardStatus
import com.tehoubi.ddrtracker.data.local.PlayEntity
import com.tehoubi.ddrtracker.data.remote.ApiException
import com.tehoubi.ddrtracker.data.remote.LeaderboardClient
import com.tehoubi.ddrtracker.data.settings.SettingsRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drains the leaderboard outbox: uploads pending plays in batches and
 * mirrors deletions. Each play's status is only updated if the row hasn't
 * changed since it was read, so an edit made mid-upload is uploaded again.
 */
@Singleton
class LeaderboardSync @Inject constructor(
    private val db: DdrDatabase,
    private val client: LeaderboardClient,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    enum class Result { DONE, NOT_CONFIGURED, RETRY }

    data class Report(val result: Result, val uploaded: Int = 0, val rejected: Int = 0, val removed: Int = 0)

    suspend fun run(batchSize: Int = 200): Report {
        val s = settings.current()
        val account = s.account
        if (account == null || !s.autoSubmit) return Report(Result.NOT_CONFIGURED)
        val dao = db.playDao()
        var uploaded = 0
        var rejected = 0
        var removed = 0
        try {
            dao.dropUnsubmittedTombstones()
            for (tomb in dao.pendingRemoval()) {
                client.deleteScore(account.serverUrl, account.token, tomb.id)
                dao.setLeaderboardStatus(tomb.id, tomb.updatedAt, LeaderboardStatus.REMOVED, null)
                removed++
            }
            while (true) {
                val batch = dao.pendingUpload(batchSize)
                if (batch.isEmpty()) break
                val now = clock.millis()
                // Don't send what the server's rules (the same code) will bounce.
                val (sendable, invalid) = batch.partition { SubmissionRules.rejectionReason(it.toSubmission(), now) == null }
                invalid.forEach {
                    dao.setLeaderboardStatus(it.id, it.updatedAt, LeaderboardStatus.REJECTED, SubmissionRules.rejectionReason(it.toSubmission(), now))
                }
                rejected += invalid.size
                if (sendable.isNotEmpty()) {
                    val response = client.submit(account.serverUrl, account.token, sendable.map { it.toSubmission() })
                    val byId = sendable.associateBy { it.id }
                    response.accepted.forEach { id ->
                        byId[id]?.let { dao.setLeaderboardStatus(it.id, it.updatedAt, LeaderboardStatus.SUBMITTED, null) }
                    }
                    response.rejected.forEach { r ->
                        byId[r.clientId]?.let { dao.setLeaderboardStatus(it.id, it.updatedAt, LeaderboardStatus.REJECTED, r.reason) }
                    }
                    uploaded += response.accepted.size
                    rejected += response.rejected.size
                }
                if (batch.size < batchSize) break
            }
        } catch (e: ApiException) {
            // A bad token won't fix itself by retrying; anything else might.
            return Report(if (e.status == 401 || e.status == 403) Result.NOT_CONFIGURED else Result.RETRY, uploaded, rejected, removed)
        } catch (e: java.io.IOException) {
            return Report(Result.RETRY, uploaded, rejected, removed)
        }
        return Report(Result.DONE, uploaded, rejected, removed)
    }
}

internal fun PlayEntity.toSubmission() = ScoreSubmission(
    clientId = id,
    songTitle = songTitle,
    style = style,
    difficulty = difficulty,
    level = level,
    score = score,
    exScore = exScore,
    marvelous = marvelous,
    perfect = perfect,
    great = great,
    good = good,
    ok = ok,
    miss = miss,
    maxCombo = maxCombo,
    failed = failed,
    playedAt = playedAt,
)
