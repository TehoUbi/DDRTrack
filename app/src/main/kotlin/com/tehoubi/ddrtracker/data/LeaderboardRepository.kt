package com.tehoubi.ddrtracker.data

import com.tehoubi.ddrtracker.core.api.ChartSummary
import com.tehoubi.ddrtracker.core.api.Comparison
import com.tehoubi.ddrtracker.core.api.HealthResponse
import com.tehoubi.ddrtracker.core.api.Leaderboard
import com.tehoubi.ddrtracker.core.api.PlayerProfile
import com.tehoubi.ddrtracker.core.api.PlayerSummary
import com.tehoubi.ddrtracker.core.api.PublicPlay
import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.data.local.DdrDatabase
import com.tehoubi.ddrtracker.data.remote.ApiException
import com.tehoubi.ddrtracker.data.remote.LeaderboardClient
import com.tehoubi.ddrtracker.data.settings.LeaderboardAccount
import com.tehoubi.ddrtracker.data.settings.SettingsRepository
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.model.PlaySource
import com.tehoubi.ddrtracker.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

class NotConnectedException : IllegalStateException("Set up a leaderboard server in Settings first")

/** A successful sign-up or recovery. [recoveryCode] is set only when one was just made. */
data class SignedIn(val displayName: String, val recoveryCode: String?)

/** Upload state of the local log, for the settings screen. */
data class UploadCounts(val pending: Int = 0, val submitted: Int = 0, val rejected: Int = 0)

@Singleton
class LeaderboardRepository @Inject constructor(
    private val client: LeaderboardClient,
    private val settings: SettingsRepository,
    private val db: DdrDatabase,
    private val sync: SyncTrigger,
) {
    val account: Flow<LeaderboardAccount?> = settings.settings.map { it.account }

    val uploadCounts: Flow<UploadCounts> = db.playDao().observeLeaderboardCounts().map { rows ->
        val m = rows.associate { it.status to it.count }
        UploadCounts(m["PENDING"] ?: 0, m["SUBMITTED"] ?: 0, m["REJECTED"] ?: 0)
    }

    suspend fun testConnection(url: String): Result<HealthResponse> = runCatching {
        val base = LeaderboardClient.normalizeBaseUrl(url) ?: throw IllegalArgumentException("Not a valid URL")
        LeaderboardClient.unreachableReason(base)?.let { throw IllegalArgumentException(it) }
        client.health(base)
    }

    /**
     * Registers on [url] and queues the whole log for upload and backup
     * there. The result carries the recovery code, which the player must be
     * shown: the server can't show it again.
     */
    suspend fun register(url: String, displayName: String): Result<SignedIn> = runCatching {
        val base = secureBase(url)
        val r = client.register(base, displayName.trim())
        signIn(LeaderboardAccount(base, r.playerId, r.displayName, r.token))
        SignedIn(r.displayName, r.recoveryCode)
    }

    /**
     * Signs back in to an existing account on [url] with its recovery code
     * (on a new phone, say). The log backed up there is restored by the next
     * backup run, which starts at once.
     */
    suspend fun recover(url: String, recoveryCode: String): Result<SignedIn> = runCatching {
        val base = secureBase(url)
        val r = client.recover(base, recoveryCode.trim())
        signIn(LeaderboardAccount(base, r.playerId, r.displayName, r.token))
        SignedIn(r.displayName, null)
    }

    /** Replaces the account's recovery code; the old one stops working. */
    suspend fun newRecoveryCode(): Result<String> = withAccount { a -> client.newRecoveryCode(a.serverUrl, a.token).recoveryCode }

    private suspend fun signIn(account: LeaderboardAccount) {
        settings.setServerUrl(account.serverUrl)
        settings.saveAccount(account)
        db.playDao().requeueAll()
        db.playDao().markAllForBackup()
        sync.playsChanged()
    }

    /** The normalised URL, refusing plain HTTP to anything but a private network (the token would travel in the clear). */
    private fun secureBase(url: String): String {
        val base = LeaderboardClient.normalizeBaseUrl(url) ?: throw IllegalArgumentException("Not a valid URL")
        LeaderboardClient.unreachableReason(base)?.let { throw IllegalArgumentException(it) }
        if (!LeaderboardClient.isSafeToUse(base)) {
            throw IllegalArgumentException("Use an https:// address for a server on the internet; plain http would send your login unencrypted")
        }
        return base
    }

    suspend fun rename(displayName: String): Result<PlayerSummary> = withAccount { a ->
        client.rename(a.serverUrl, a.token, displayName.trim()).also { settings.setDisplayName(it.displayName) }
    }

    suspend fun signOut() = settings.signOut()

    suspend fun retryRejected() {
        db.playDao().requeueAll()
        sync.playsChanged()
    }

    suspend fun leaderboard(songKey: String, style: String, difficulty: String): Result<Leaderboard> = withAccount { a ->
        client.leaderboard(a.serverUrl, a.token, songKey, style, difficulty)
    }

    suspend fun compare(rivalId: String): Result<Comparison> = withAccount { a -> client.compare(a.serverUrl, a.token, rivalId) }

    suspend fun search(query: String): Result<List<PlayerSummary>> = withAccount { a -> client.search(a.serverUrl, query) }

    suspend fun popular(): Result<List<ChartSummary>> = withAccount { a -> client.popular(a.serverUrl) }

    /** Everyone on the server, most recently active first. */
    suspend fun players(): Result<List<PlayerProfile>> = withAccount { a -> client.players(a.serverUrl) }

    /** Another player's plays, newest first, in the app's own model so the same statistics apply. */
    suspend fun playsOf(playerId: String): Result<List<Play>> = withAccount { a ->
        client.plays(a.serverUrl, playerId).mapNotNull { runCatching { it.toPlay() }.getOrNull() }
    }

    private suspend fun <T> withAccount(block: suspend (LeaderboardAccount) -> T): Result<T> {
        val account = settings.current().account ?: return Result.failure(NotConnectedException())
        return runCatching { block(account) }
    }
}

/** A short message for showing a failed call to the player. */
fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message ?: "Server error"
    is NotConnectedException -> message!!
    is java.net.UnknownHostException, is java.net.ConnectException -> "Can't reach the server"
    is java.net.SocketTimeoutException -> "The server took too long to answer"
    else -> message ?: javaClass.simpleName
}

private fun PublicPlay.toPlay() = Play(
    id = id,
    songTitle = songTitle,
    style = PlayStyle.valueOf(style),
    difficulty = Difficulty.valueOf(difficulty),
    level = level,
    score = score,
    exScore = exScore,
    grade = Grade.valueOf(grade),
    lamp = ClearLamp.valueOf(lamp),
    judgments = Judgments(marvelous, perfect, great, good, ok, miss),
    maxCombo = maxCombo,
    failed = failed,
    playedAt = Instant.ofEpochMilli(playedAt),
    source = PlaySource.IMPORT,
)
