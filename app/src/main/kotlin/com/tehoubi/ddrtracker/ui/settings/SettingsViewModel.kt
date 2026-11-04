package com.tehoubi.ddrtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.core.ocr.ResultScreenParser
import android.net.Uri
import com.tehoubi.ddrtracker.data.LeaderboardRepository
import com.tehoubi.ddrtracker.data.backup.BackupManager
import com.tehoubi.ddrtracker.data.UploadCounts
import com.tehoubi.ddrtracker.data.settings.Settings
import com.tehoubi.ddrtracker.data.settings.SettingsRepository
import com.tehoubi.ddrtracker.data.songs.SongListRefresh
import com.tehoubi.ddrtracker.data.songs.SongListStore
import com.tehoubi.ddrtracker.data.userMessage
import com.tehoubi.ddrtracker.scan.SongCatalogProvider
import com.tehoubi.ddrtracker.sync.AccountBackup
import com.tehoubi.ddrtracker.sync.BackupStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: Settings = Settings(),
    val backupStatus: BackupStatus = BackupStatus.Idle,
    val uploads: UploadCounts = UploadCounts(),
    /** Songs the scanner can match titles against, and when the list was last checked for new ones. */
    val songCount: Int = 0,
    val songsCheckedAt: Long? = null,
    val serverUrlDraft: String = "",
    val nameDraft: String = "",
    val recoveryCodeDraft: String = "",
    /** A recovery code just made, to show once; null once the player has seen it. */
    val newRecoveryCode: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val leaderboard: LeaderboardRepository,
    private val backup: AccountBackup,
    private val backups: BackupManager,
    catalogs: SongCatalogProvider,
    private val songList: SongListStore,
) : ViewModel() {

    private val drafts = MutableStateFlow(SettingsUiState())

    private val songs = combine(catalogs.catalog, songList.list) { catalog, list -> catalog.size to list.checkedAt }

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.settings, leaderboard.uploadCounts, backup.status, drafts, songs,
    ) { s, u, b, d, (songCount, checkedAt) ->
        d.copy(
            settings = s,
            uploads = u,
            songCount = songCount,
            songsCheckedAt = checkedAt,
            backupStatus = b,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            val s = repository.settings.first()
            drafts.update { it.copy(serverUrlDraft = s.serverUrl, nameDraft = s.account?.displayName.orEmpty()) }
        }
    }

    fun setAutoCapture(enabled: Boolean) = launch { repository.setAutoCapture(enabled) }
    fun setPlayerSide(side: ResultScreenParser.Side) = launch { repository.setPlayerSide(side) }
    fun setAutoSubmit(enabled: Boolean) = launch { repository.setAutoSubmit(enabled) }
    fun setTimingAdvice(enabled: Boolean) = launch { repository.setTimingAdvice(enabled) }

    fun setBackup(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBackup(enabled)
            if (enabled) backup.run()
        }
    }

    fun backupNow() {
        viewModelScope.launch { backup.run() }
    }

    fun checkSongList() = busy {
        when (val r = songList.refresh()) {
            is SongListRefresh.Updated -> "Song list updated: ${r.songs} songs"
            SongListRefresh.Unchanged -> "Song list is up to date"
            is SongListRefresh.Failed -> "Couldn't check the song list: ${r.message}"
        }
    }

    fun exportCsv(uri: Uri) = busy {
        runCatching { backups.exportCsv(uri) }.fold({ "Exported $it plays" }, { "Export failed: ${it.message}" })
    }

    fun exportBackup(uri: Uri) = busy {
        runCatching { backups.exportBackup(uri) }.fold({ "Backed up $it plays" }, { "Backup failed: ${it.message}" })
    }

    fun restore(uri: Uri) = busy {
        runCatching { backups.restore(uri) }.fold(
            { "Restored ${it.restored} plays" + if (it.skipped > 0) " (${it.skipped} unreadable rows skipped)" else "" },
            { "Restore failed: ${it.message}" },
        )
    }

    fun onServerUrlChanged(url: String) = drafts.update { it.copy(serverUrlDraft = url) }
    fun onNameChanged(name: String) = drafts.update { it.copy(nameDraft = name) }
    fun onMessageShown() = drafts.update { it.copy(message = null) }

    fun testConnection() = busy {
        leaderboard.testConnection(drafts.value.serverUrlDraft).fold(
            { "Connected — database ${it.database}" },
            { "Couldn't connect: ${it.userMessage()}" },
        )
    }

    fun register() = busy {
        leaderboard.register(drafts.value.serverUrlDraft, drafts.value.nameDraft).fold(
            { signedIn ->
                drafts.update { it.copy(newRecoveryCode = signedIn.recoveryCode) }
                "Signed up as ${signedIn.displayName}. Uploading your plays…"
            },
            { "Sign-up failed: ${it.userMessage()}" },
        )
    }

    fun onRecoveryCodeChanged(code: String) = drafts.update { it.copy(recoveryCodeDraft = code) }

    fun recover() = busy {
        leaderboard.recover(drafts.value.serverUrlDraft, drafts.value.recoveryCodeDraft).fold(
            { signedIn ->
                drafts.update { it.copy(recoveryCodeDraft = "", nameDraft = signedIn.displayName) }
                "Signed in as ${signedIn.displayName}. Restoring your plays…"
            },
            { "Couldn't restore the account: ${it.userMessage()}" },
        )
    }

    fun makeRecoveryCode() = busy {
        leaderboard.newRecoveryCode().fold(
            { code -> drafts.update { it.copy(newRecoveryCode = code) }; "Made a new recovery code; the old one no longer works" },
            { "Couldn't make a new code: ${it.userMessage()}" },
        )
    }

    fun onRecoveryCodeShown() = drafts.update { it.copy(newRecoveryCode = null) }

    fun rename() = busy {
        leaderboard.rename(drafts.value.nameDraft).fold({ "Renamed to ${it.displayName}" }, { "Rename failed: ${it.userMessage()}" })
    }

    fun signOut() = busy {
        leaderboard.signOut()
        "Signed out. Your plays stay on this phone."
    }

    fun retryRejected() = busy {
        leaderboard.retryRejected()
        "Re-queued plays for upload"
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun busy(block: suspend () -> String) {
        viewModelScope.launch {
            drafts.update { it.copy(busy = true) }
            val message = block()
            drafts.update { it.copy(busy = false, message = message) }
        }
    }
}
