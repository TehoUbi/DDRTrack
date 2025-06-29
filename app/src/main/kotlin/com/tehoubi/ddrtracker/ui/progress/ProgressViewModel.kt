package com.tehoubi.ddrtracker.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.stats.LevelSummary
import com.tehoubi.ddrtracker.core.stats.ProgressStats
import com.tehoubi.ddrtracker.core.stats.TimePoint
import com.tehoubi.ddrtracker.core.stats.WeekPoint
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.model.PlaySession
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

/** Best lamps per level, folded into the four groups the chart shows. */
data class LevelLamps(val level: Int, val failed: Int, val clear: Int, val fullCombo: Int, val perfect: Int)

data class ProgressUiState(
    val isLoading: Boolean = true,
    val playCount: Int = 0,
    val weeks: List<WeekPoint> = emptyList(),
    val comfortHistory: List<TimePoint> = emptyList(),
    val comfortLevel: Int? = null,
    val levels: List<LevelSummary> = emptyList(),
    val levelLamps: List<LevelLamps> = emptyList(),
    val recentExRatio: Double? = null,
    val fastShare: Double? = null,
    val sessions: List<PlaySession> = emptyList(),
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    repository: PlayRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<ProgressUiState> = combine(repository.observePlays(), repository.observeSessions()) { plays, sessions ->
        build(plays, sessions)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    private fun build(plays: List<Play>, sessions: List<PlaySession>): ProgressUiState {
        val stats = ProgressStats(plays, clock.zone)
        val weeks = stats.weekly()
        val bests = stats.personalBests.values
        val lamps = bests.groupBy { it.level }.map { (level, list) ->
            LevelLamps(
                level = level,
                failed = list.count { it.lamp == ClearLamp.FAILED },
                clear = list.count { it.lamp == ClearLamp.CLEAR },
                fullCombo = list.count { it.lamp == ClearLamp.FULL_COMBO || it.lamp == ClearLamp.GREAT_FULL_COMBO },
                perfect = list.count { it.lamp >= ClearLamp.PERFECT_FULL_COMBO },
            )
        }.sortedByDescending { it.level }
        return ProgressUiState(
            isLoading = false,
            playCount = plays.size,
            weeks = weeks.takeLast(16),
            comfortHistory = stats.comfortLevelHistory(),
            comfortLevel = stats.comfortLevel(),
            levels = stats.levels(),
            levelLamps = lamps,
            recentExRatio = weeks.takeLast(4).mapNotNull { it.averageExRatio }.takeIf { it.isNotEmpty() }?.average(),
            fastShare = stats.fastShare(),
            sessions = sessions.take(8),
        )
    }
}
