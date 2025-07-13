package com.tehoubi.ddrtracker.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.core.api.ChartSummary
import com.tehoubi.ddrtracker.core.api.Comparison
import com.tehoubi.ddrtracker.core.api.PlayerSummary
import com.tehoubi.ddrtracker.data.LeaderboardRepository
import com.tehoubi.ddrtracker.data.settings.SettingsRepository
import com.tehoubi.ddrtracker.data.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompareUiState(
    val connected: Boolean = false,
    val me: PlayerSummary? = null,
    val query: String = "",
    val results: List<PlayerSummary> = emptyList(),
    val rivals: List<PlayerSummary> = emptyList(),
    val popular: List<ChartSummary> = emptyList(),
    val comparison: Comparison? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class CompareViewModel @Inject constructor(
    private val leaderboard: LeaderboardRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val local = MutableStateFlow(CompareUiState())

    val uiState: StateFlow<CompareUiState> = combine(settings.settings, local) { s, l ->
        l.copy(
            connected = s.account != null,
            me = s.account?.let { PlayerSummary(it.playerId, it.displayName) },
            rivals = s.rivals,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareUiState())

    init {
        viewModelScope.launch {
            local.map { it.query.trim() }.distinctUntilChanged().debounce(300).filter { it.length >= 2 }.collect { q ->
                leaderboard.search(q).onSuccess { found ->
                    val me = settings.current().account?.playerId
                    local.update { it.copy(results = found.filter { p -> p.playerId != me }) }
                }.onFailure { e -> local.update { it.copy(error = e.userMessage()) } }
            }
        }
        refreshPopular()
    }

    fun refreshPopular() {
        viewModelScope.launch {
            leaderboard.popular().onSuccess { list -> local.update { it.copy(popular = list) } }
        }
    }

    fun onQueryChanged(q: String) = local.update { it.copy(query = q, results = if (q.length < 2) emptyList() else it.results) }

    fun open(rival: PlayerSummary) {
        viewModelScope.launch {
            local.update { it.copy(loading = true, error = null) }
            leaderboard.compare(rival.playerId)
                .onSuccess { c -> local.update { it.copy(comparison = c, loading = false, query = "", results = emptyList()) } }
                .onFailure { e -> local.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun close() = local.update { it.copy(comparison = null) }

    fun saveRival(rival: PlayerSummary) {
        viewModelScope.launch { settings.addRival(rival) }
    }

    fun removeRival(rival: PlayerSummary) {
        viewModelScope.launch { settings.removeRival(rival.playerId) }
    }

    fun onErrorShown() = local.update { it.copy(error = null) }
}
