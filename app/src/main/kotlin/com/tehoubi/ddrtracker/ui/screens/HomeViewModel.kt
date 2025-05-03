package com.tehoubi.ddrtracker.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.domain.model.Score
import com.tehoubi.ddrtracker.domain.repository.ScoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val scores: List<Score> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scoreRepository: ScoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        // TODO: replace with real userId from auth
        loadScores(userId = "local_user")
    }

    private fun loadScores(userId: String) {
        viewModelScope.launch {
            scoreRepository.observeScores(userId)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message,
                    )
                }
                .collect { scores ->
                    _uiState.value = HomeUiState(scores = scores, isLoading = false)
                }
        }
    }
}
