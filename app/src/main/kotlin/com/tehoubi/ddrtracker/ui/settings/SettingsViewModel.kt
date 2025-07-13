package com.tehoubi.ddrtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.data.LeaderboardRepository
import com.tehoubi.ddrtracker.data.UploadCounts
import com.tehoubi.ddrtracker.data.settings.Settings
import com.tehoubi.ddrtracker.data.settings.SettingsRepository
import com.tehoubi.ddrtracker.data.userMessage
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
    val uploads: UploadCounts = UploadCounts(),
    val serverUrlDraft: String = "",
    val nameDraft: String = "",
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val leaderboard: LeaderboardRepository,
) : ViewModel() {

    private val drafts = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(repository.settings, leaderboard.uploadCounts, drafts) { s, u, d ->
        d.copy(settings = s, uploads = u)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            val s = repository.settings.first()
            drafts.update { it.copy(serverUrlDraft = s.serverUrl, nameDraft = s.account?.displayName.orEmpty()) }
        }
    }

    fun setAutoCapture(enabled: Boolean) = launch { repository.setAutoCapture(enabled) }
    fun setAutoSubmit(enabled: Boolean) = launch { repository.setAutoSubmit(enabled) }
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
            { "Signed up as ${it.displayName}. Uploading your plays…" },
            { "Sign-up failed: ${it.userMessage()}" },
        )
    }

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
