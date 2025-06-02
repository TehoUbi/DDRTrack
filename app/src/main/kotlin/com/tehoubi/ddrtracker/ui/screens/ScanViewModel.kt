package com.tehoubi.ddrtracker.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val playRepository: PlayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState

    /** Called when the user taps the capture button. */
    fun captureAndParse() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            // TODO: Capture frame from CameraX, pass bitmap to OcrScoreParser
            // val play = parse(bitmap) ?: return
            // playRepository.savePlay(play)
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}
