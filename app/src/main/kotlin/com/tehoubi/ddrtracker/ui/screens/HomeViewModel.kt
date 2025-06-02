package com.tehoubi.ddrtracker.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val plays: List<Play> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    playRepository: PlayRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = playRepository.observePlays()
        .map { HomeUiState(plays = it, isLoading = false) }
        .catch { emit(HomeUiState(isLoading = false, error = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
