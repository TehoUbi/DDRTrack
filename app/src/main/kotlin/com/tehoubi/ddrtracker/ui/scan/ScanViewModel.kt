package com.tehoubi.ddrtracker.ui.scan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.domain.model.PlaySource
import com.tehoubi.ddrtracker.scan.ScanDraft
import com.tehoubi.ddrtracker.scan.ScanDraftStore
import com.tehoubi.ddrtracker.scan.ScanPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

data class ScanUiState(
    val isProcessing: Boolean = false,
    val message: String? = null,
)

sealed interface ScanEvent {
    data object OpenReview : ScanEvent
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val pipeline: ScanPipeline,
    private val drafts: ScanDraftStore,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState

    private val _events = Channel<ScanEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** A still from the shutter button. */
    fun onPhotoCaptured(bitmap: Bitmap, rotationDegrees: Int) {
        process(bitmap, rotationDegrees, PlaySource.CAMERA)
    }

    fun onCaptureFailed(error: Throwable) {
        _uiState.update { it.copy(isProcessing = false, message = "Capture failed: ${error.message}") }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private fun process(bitmap: Bitmap, rotationDegrees: Int, source: PlaySource) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, message = null) }
            val output = runCatching { pipeline.run(bitmap, rotationDegrees) }
            output.onSuccess { (page, scan) ->
                if (!scan.result.looksLikeResultScreen) {
                    _uiState.update {
                        it.copy(isProcessing = false, message = "That doesn't look like a result screen. Try again closer.")
                    }
                    return@launch
                }
                drafts.put(ScanDraft(scan, source, Instant.now(clock), page.text))
                _uiState.update { it.copy(isProcessing = false) }
                _events.send(ScanEvent.OpenReview)
            }.onFailure { e ->
                _uiState.update { it.copy(isProcessing = false, message = "Text recognition failed: ${e.message}") }
            }
        }
    }
}
