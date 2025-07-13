package com.tehoubi.ddrtracker.ui.chart

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.core.api.Leaderboard
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.stats.ChartProgress
import com.tehoubi.ddrtracker.core.stats.ProgressStats
import com.tehoubi.ddrtracker.data.LeaderboardRepository
import com.tehoubi.ddrtracker.data.NotConnectedException
import com.tehoubi.ddrtracker.data.remote.ApiException
import com.tehoubi.ddrtracker.data.userMessage
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

sealed interface BoardState {
    data object NotConnected : BoardState
    data object Loading : BoardState
    data object Empty : BoardState
    data class Error(val message: String) : BoardState
    data class Loaded(val board: Leaderboard) : BoardState
}

data class ChartUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val label: String = "",
    val progress: ChartProgress<Play>? = null,
    val board: BoardState = BoardState.Loading,
)

@HiltViewModel
class ChartViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: PlayRepository,
    private val leaderboard: LeaderboardRepository,
    clock: Clock,
) : ViewModel() {

    val songKey: String = checkNotNull(savedState["songKey"])
    val style: PlayStyle = PlayStyle.valueOf(checkNotNull(savedState["style"]))
    val difficulty: Difficulty = Difficulty.valueOf(checkNotNull(savedState["difficulty"]))

    private val board = MutableStateFlow<BoardState>(BoardState.Loading)

    val uiState: StateFlow<ChartUiState> = combine(repository.observeChart(songKey, style, difficulty), board) { plays, b ->
        val progress = plays.firstOrNull()?.let { ProgressStats(plays, clock.zone).chart(it.chartKey) }
        val remote = (b as? BoardState.Loaded)?.board
        ChartUiState(
            isLoading = false,
            title = plays.lastOrNull()?.songTitle ?: remote?.songTitle.orEmpty(),
            label = plays.lastOrNull()?.chart?.label ?: "${difficulty.shortCode(style)} ${remote?.level ?: ""}".trim(),
            progress = progress,
            board = b,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartUiState())

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            board.value = BoardState.Loading
            board.value = leaderboard.leaderboard(songKey, style.name, difficulty.name).fold(
                { BoardState.Loaded(it) },
                { e ->
                    when {
                        e is NotConnectedException -> BoardState.NotConnected
                        e is ApiException && e.status == 404 -> BoardState.Empty
                        else -> BoardState.Error(e.userMessage())
                    }
                },
            )
        }
    }
}
