package com.tehoubi.ddrtracker.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.core.api.ChartSummary
import com.tehoubi.ddrtracker.core.api.PlayerProfile
import com.tehoubi.ddrtracker.core.api.PlayerSummary
import com.tehoubi.ddrtracker.core.songs.Song
import com.tehoubi.ddrtracker.core.songs.SongCatalog
import com.tehoubi.ddrtracker.data.LeaderboardRepository
import com.tehoubi.ddrtracker.data.settings.SettingsRepository
import com.tehoubi.ddrtracker.data.userMessage
import com.tehoubi.ddrtracker.scan.SongCatalogProvider
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
    /** Players matching [query], from the server. */
    val results: List<PlayerSummary> = emptyList(),
    /** Songs matching [query], by title or romanized name, from the song list. */
    val songs: List<Song> = emptyList(),
    val rivals: List<PlayerSummary> = emptyList(),
    /** Everyone on the server, most recently active first. */
    val players: List<PlayerProfile> = emptyList(),
    val popular: List<ChartSummary> = emptyList(),
    val error: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class CompareViewModel @Inject constructor(
    private val leaderboard: LeaderboardRepository,
    private val settings: SettingsRepository,
    catalogs: SongCatalogProvider,
) : ViewModel() {

    private val local = MutableStateFlow(CompareUiState())
    private var catalog = SongCatalog(emptyList())

    val uiState: StateFlow<CompareUiState> = combine(settings.settings, local) { s, l ->
        l.copy(
            connected = s.account != null,
            me = s.account?.let { PlayerSummary(it.playerId, it.displayName) },
            rivals = s.rivals,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareUiState())

    init {
        viewModelScope.launch { catalogs.catalog.collect { catalog = it } }
        viewModelScope.launch {
            local.map { it.query.trim() }.distinctUntilChanged().debounce(300).filter { it.length >= 2 }.collect { q ->
                leaderboard.search(q).onSuccess { found ->
                    val me = settings.current().account?.playerId
                    local.update { it.copy(results = found.filter { p -> p.playerId != me }) }
                }.onFailure { e -> local.update { it.copy(error = e.userMessage()) } }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            leaderboard.popular().onSuccess { list -> local.update { it.copy(popular = list) } }
            leaderboard.players().onSuccess { list -> local.update { it.copy(players = list) } }
        }
    }

    fun onQueryChanged(q: String) = local.update {
        val searching = q.trim().length >= 2
        it.copy(
            query = q,
            results = if (searching) it.results else emptyList(),
            // The song list is on the phone, so songs match as you type.
            songs = if (searching) catalog.suggest(q.trim(), limit = 8) else emptyList(),
        )
    }

    fun onErrorShown() = local.update { it.copy(error = null) }
}
