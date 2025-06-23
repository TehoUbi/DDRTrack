package com.tehoubi.ddrtracker.ui.play

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.core.stats.bestFirst
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayDetailUiState(
    val isLoading: Boolean = true,
    val play: Play? = null,
    /** This chart's best play, which may be this one. */
    val best: Play? = null,
    /** 1-based position of this play among the chart's attempts, by time. */
    val attempt: Int = 0,
    val attempts: Int = 0,
    /** Score change against the best play before this one, if any. */
    val deltaToPreviousBest: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: PlayRepository,
) : ViewModel() {

    private val playId: String = checkNotNull(savedState["playId"])

    val uiState: StateFlow<PlayDetailUiState> = repository.observePlay(playId).flatMapLatest { play ->
        if (play == null) flowOf(PlayDetailUiState(isLoading = false))
        else repository.observeChart(play.songKey, play.style, play.difficulty).map { history ->
            val index = history.indexOfFirst { it.id == play.id }
            val before = history.take(index.coerceAtLeast(0))
            PlayDetailUiState(
                isLoading = false,
                play = play,
                best = history.sortedWith(bestFirst).firstOrNull(),
                attempt = index + 1,
                attempts = history.size,
                deltaToPreviousBest = before.maxOfOrNull { it.score }?.let { play.score - it },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayDetailUiState())

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch { repository.deletePlay(playId).onSuccess { onDone() } }
    }
}
