package com.tehoubi.ddrtracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.core.stats.Overview
import com.tehoubi.ddrtracker.core.stats.ProgressStats
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.model.PlaySession
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val overview: Overview? = null,
    val lastSession: PlaySession? = null,
    val lastSessionPlays: List<Play> = emptyList(),
    val recent: List<Play> = emptyList(),
    val newBestIds: Set<String> = emptySet(),
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    playRepository: PlayRepository,
    clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        playRepository.observePlays(),
        playRepository.observeSessions(),
    ) { plays, sessions ->
        val stats = ProgressStats(plays, clock.zone)
        val last = sessions.firstOrNull()
        HomeUiState(
            isLoading = false,
            overview = stats.overview(LocalDate.now(clock)),
            lastSession = last,
            lastSessionPlays = last?.let { s -> plays.filter { it.sessionId == s.id } }.orEmpty(),
            recent = plays.take(12),
            newBestIds = stats.newBestIds(),
        )
    }
        .flowOn(Dispatchers.Default)
        .catch { emit(HomeUiState(isLoading = false, error = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
