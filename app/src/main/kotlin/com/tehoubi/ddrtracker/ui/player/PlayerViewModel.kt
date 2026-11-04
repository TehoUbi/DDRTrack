package com.tehoubi.ddrtracker.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.core.api.Comparison
import com.tehoubi.ddrtracker.core.api.PlayerSummary
import com.tehoubi.ddrtracker.core.stats.Overview
import com.tehoubi.ddrtracker.core.stats.ProgressStats
import com.tehoubi.ddrtracker.core.stats.Session
import com.tehoubi.ddrtracker.core.stats.Sessions
import com.tehoubi.ddrtracker.data.LeaderboardRepository
import com.tehoubi.ddrtracker.data.settings.SettingsRepository
import com.tehoubi.ddrtracker.data.userMessage
import com.tehoubi.ddrtracker.domain.model.Play
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class PlayerUiState(
    val name: String = "",
    val isMe: Boolean = false,
    val following: Boolean = false,
    val isLoading: Boolean = true,
    val overview: Overview? = null,
    val lastSession: Session<Play>? = null,
    val recent: List<Play> = emptyList(),
    val newBestIds: Set<String> = emptySet(),
    /** Head to head with this player; null for yourself or while loading. */
    val comparison: Comparison? = null,
    val error: String? = null,
)

/**
 * Another player's profile: their plays come from the leaderboard server,
 * and the same statistics as your own home page are worked out from them.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val leaderboard: LeaderboardRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    val playerId: String = checkNotNull(savedState["playerId"])

    private val local = MutableStateFlow(PlayerUiState(name = savedState.get<String>("name").orEmpty()))

    val uiState: StateFlow<PlayerUiState> = combine(settings.settings, local) { s, l ->
        l.copy(isMe = s.account?.playerId == playerId, following = s.rivals.any { it.playerId == playerId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(isLoading = true, error = null) }
            leaderboard.playsOf(playerId).onSuccess { plays ->
                val built = withContext(Dispatchers.Default) {
                    val stats = ProgressStats(plays, clock.zone)
                    local.value.copy(
                        isLoading = false,
                        overview = stats.overview(LocalDate.now(clock)),
                        lastSession = Sessions.group(plays).lastOrNull(),
                        recent = plays.sortedByDescending { it.playedAt }.take(12),
                        newBestIds = stats.newBestIds(),
                    )
                }
                local.update { built.copy(comparison = it.comparison, name = it.name) }
            }.onFailure { e -> local.update { it.copy(isLoading = false, error = e.userMessage()) } }

            val me = settings.current().account?.playerId
            if (me != null && me != playerId) {
                leaderboard.compare(playerId).onSuccess { c -> local.update { it.copy(comparison = c, name = c.rival.displayName) } }
            }
        }
    }

    fun follow() {
        viewModelScope.launch { settings.addRival(PlayerSummary(playerId, uiState.value.name)) }
    }

    fun unfollow() {
        viewModelScope.launch { settings.removeRival(playerId) }
    }
}
