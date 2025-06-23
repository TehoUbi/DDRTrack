package com.tehoubi.ddrtracker.ui.chart

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.stats.ChartProgress
import com.tehoubi.ddrtracker.core.stats.ProgressStats
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

data class ChartUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val label: String = "",
    val progress: ChartProgress<Play>? = null,
)

@HiltViewModel
class ChartViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: PlayRepository,
    clock: Clock,
) : ViewModel() {

    val songKey: String = checkNotNull(savedState["songKey"])
    val style: PlayStyle = PlayStyle.valueOf(checkNotNull(savedState["style"]))
    val difficulty: Difficulty = Difficulty.valueOf(checkNotNull(savedState["difficulty"]))

    val uiState: StateFlow<ChartUiState> = repository.observeChart(songKey, style, difficulty).map { plays ->
        val progress = plays.firstOrNull()?.let { ProgressStats(plays, clock.zone).chart(it.chartKey) }
        ChartUiState(
            isLoading = false,
            title = plays.lastOrNull()?.songTitle.orEmpty(),
            label = plays.lastOrNull()?.chart?.label ?: difficulty.shortCode(style),
            progress = progress,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartUiState())
}
