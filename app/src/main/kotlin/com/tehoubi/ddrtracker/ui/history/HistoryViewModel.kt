package com.tehoubi.ddrtracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.core.stats.ProgressStats
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.model.PlaySession
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import javax.inject.Inject

/** A run of list rows under one session header. */
data class HistoryGroup(val session: PlaySession?, val plays: List<Play>)

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter(),
    val groups: List<HistoryGroup> = emptyList(),
    val total: Int = 0,
    val shown: Int = 0,
    val bestIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    repository: PlayRepository,
    clock: Clock,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter())

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.observePlays(), repository.observeSessions(), filter,
    ) { plays, sessions, f ->
        val stats = ProgressStats(plays, clock.zone)
        val bestIds = stats.personalBests.values.map { it.id }.toSet()
        val shown = f.apply(plays, bestIds)
        // Session headers only make sense in time order.
        val groups = if (f.sort == HistorySort.NEWEST || f.sort == HistorySort.OLDEST) {
            val byId = sessions.associateBy { it.id }
            shown.fold(mutableListOf<HistoryGroup>()) { acc, p ->
                val last = acc.lastOrNull()
                if (last != null && last.session?.id == p.sessionId) {
                    acc[acc.lastIndex] = last.copy(plays = last.plays + p)
                } else acc += HistoryGroup(byId[p.sessionId], listOf(p))
                acc
            }
        } else listOf(HistoryGroup(null, shown))
        HistoryUiState(f, groups, plays.size, shown.size, stats.newBestIds(), isLoading = false)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun update(transform: (HistoryFilter) -> HistoryFilter) = filter.update(transform)

    fun clear() = filter.update { HistoryFilter(sort = it.sort) }
}
