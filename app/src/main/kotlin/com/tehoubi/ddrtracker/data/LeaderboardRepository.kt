package com.tehoubi.ddrtracker.data

import com.tehoubi.ddrtracker.core.api.ChartSummary
import com.tehoubi.ddrtracker.core.api.Comparison
import com.tehoubi.ddrtracker.core.api.HealthResponse
import com.tehoubi.ddrtracker.core.api.Leaderboard
import com.tehoubi.ddrtracker.core.api.PlayerSummary
import com.tehoubi.ddrtracker.data.local.DdrDatabase
import com.tehoubi.ddrtracker.data.remote.ApiException
import com.tehoubi.ddrtracker.data.remote.LeaderboardClient
import com.tehoubi.ddrtracker.data.settings.LeaderboardAccount
import com.tehoubi.ddrtracker.data.settings.SettingsRepository
import com.tehoubi.ddrtracker.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

class NotConnectedException : IllegalStateException("Set up a leaderboard server in Settings first")

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
        client.health(LeaderboardClient.normalizeBaseUrl(url) ?: throw IllegalArgumentException("Not a valid URL"))
    }

    /** Registers on [url] and queues the whole log for upload there. */
    suspend fun register(url: String, displayName: String): Result<LeaderboardAccount> = runCatching {
        val base = LeaderboardClient.normalizeBaseUrl(url) ?: throw IllegalArgumentException("Not a valid URL")
        val r = client.register(base, displayName.trim())
        val account = LeaderboardAccount(base, r.playerId, r.displayName, r.token)
        settings.setServerUrl(base)
        settings.saveAccount(account)
        db.playDao().requeueAll()
        sync.playsChanged()
        account
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
