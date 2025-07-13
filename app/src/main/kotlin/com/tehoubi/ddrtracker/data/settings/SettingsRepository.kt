package com.tehoubi.ddrtracker.data.settings

import android.content.Context
import com.tehoubi.ddrtracker.core.api.PlayerSummary
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The player's leaderboard identity, once registered with a server. */
data class LeaderboardAccount(
    val serverUrl: String,
    val playerId: String,
    val displayName: String,
    val token: String,
)

data class Settings(
    /** Take the still automatically once the live reading settles. */
    val autoCapture: Boolean = true,
    /** Base URL of the leaderboard service; blank means leaderboards are off. */
    val serverUrl: String = "",
    val account: LeaderboardAccount? = null,
    /** Upload new plays to the leaderboard in the background. */
    val autoSubmit: Boolean = true,
    /** Players saved on the Compare tab. */
    val rivals: List<PlayerSummary> = emptyList(),
)

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository(private val store: DataStore<Preferences>) {

    @Inject constructor(@ApplicationContext context: Context) : this(context.settingsDataStore)

    private object Keys {
        val autoCapture = booleanPreferencesKey("auto_capture")
        val serverUrl = stringPreferencesKey("server_url")
        val playerId = stringPreferencesKey("player_id")
        val displayName = stringPreferencesKey("display_name")
        val token = stringPreferencesKey("api_token")
        val accountServer = stringPreferencesKey("account_server")
        val autoSubmit = booleanPreferencesKey("auto_submit")
        val rivals = stringSetPreferencesKey("rivals")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        val account = p[Keys.playerId]?.let { id ->
            LeaderboardAccount(
                serverUrl = p[Keys.accountServer].orEmpty(),
                playerId = id,
                displayName = p[Keys.displayName].orEmpty(),
                token = p[Keys.token].orEmpty(),
            )
        }
        Settings(
            autoCapture = p[Keys.autoCapture] ?: true,
            serverUrl = p[Keys.serverUrl].orEmpty(),
            // An account only counts for the server it was created on.
            account = account?.takeIf { it.serverUrl == p[Keys.serverUrl].orEmpty() && it.token.isNotEmpty() },
            autoSubmit = p[Keys.autoSubmit] ?: true,
            rivals = p[Keys.rivals].orEmpty().mapNotNull { decodeRival(it) }.sortedBy { it.displayName.lowercase() },
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setAutoCapture(enabled: Boolean) = store.edit { it[Keys.autoCapture] = enabled }

    suspend fun setServerUrl(url: String) = store.edit { it[Keys.serverUrl] = url }

    suspend fun setAutoSubmit(enabled: Boolean) = store.edit { it[Keys.autoSubmit] = enabled }

    suspend fun saveAccount(account: LeaderboardAccount) = store.edit {
        it[Keys.accountServer] = account.serverUrl
        it[Keys.playerId] = account.playerId
        it[Keys.displayName] = account.displayName
        it[Keys.token] = account.token
    }

    suspend fun setDisplayName(name: String) = store.edit { it[Keys.displayName] = name }

    suspend fun addRival(rival: PlayerSummary) = store.edit { p ->
        val others = p[Keys.rivals].orEmpty().filterNot { decodeRival(it)?.playerId == rival.playerId }
        p[Keys.rivals] = (others + "${rival.playerId}\t${rival.displayName}").toSet()
    }

    suspend fun removeRival(playerId: String) = store.edit { p ->
        p[Keys.rivals] = p[Keys.rivals].orEmpty().filterNot { decodeRival(it)?.playerId == playerId }.toSet()
    }

    private fun decodeRival(raw: String): PlayerSummary? =
        raw.split('\t', limit = 2).takeIf { it.size == 2 }?.let { PlayerSummary(it[0], it[1]) }

    suspend fun signOut() = store.edit {
        it.remove(Keys.accountServer); it.remove(Keys.playerId); it.remove(Keys.displayName); it.remove(Keys.token)
        it.remove(Keys.rivals)
    }
}
