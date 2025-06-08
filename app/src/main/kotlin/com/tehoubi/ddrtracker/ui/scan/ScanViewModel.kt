package com.tehoubi.ddrtracker.ui.scan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.core.ocr.Box
import com.tehoubi.ddrtracker.core.ocr.ParsedResult
import com.tehoubi.ddrtracker.core.ocr.ResultScreenParser
import com.tehoubi.ddrtracker.core.ocr.ScanValidator
import com.tehoubi.ddrtracker.core.vision.FieldConsensus
import com.tehoubi.ddrtracker.core.vision.FrameQuality
import com.tehoubi.ddrtracker.data.settings.SettingsRepository
import com.tehoubi.ddrtracker.domain.model.PlaySource
import com.tehoubi.ddrtracker.scan.LiveFrame
import com.tehoubi.ddrtracker.scan.ScanDraft
import com.tehoubi.ddrtracker.scan.ScanDraftStore
import com.tehoubi.ddrtracker.scan.ScanPipeline
import com.tehoubi.ddrtracker.scan.SongCatalogProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

enum class LivePhase { SEARCHING, READING, LOCKED, CAPTURING }

data class ScanUiState(
    val phase: LivePhase = LivePhase.SEARCHING,
    val hint: String? = null,
    /** Fraction of the score + judgments that has settled across frames. */
    val progress: Float = 0f,
    val live: ParsedResult = ParsedResult(),
    /** Text boxes of the latest frame, in upright image coordinates. */
    val boxes: List<Box> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val isProcessing: Boolean = false,
    val message: String? = null,
)

sealed interface ScanEvent {
    data object TakeStill : ScanEvent
    data object OpenReview : ScanEvent
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val pipeline: ScanPipeline,
    private val drafts: ScanDraftStore,
    private val catalogs: SongCatalogProvider,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState

    private val _events = Channel<ScanEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val autoCapture = settings.settings.map { it.autoCapture }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val consensus = FieldConsensus()
    private var parser = ResultScreenParser()

    init {
        viewModelScope.launch { catalogs.catalog.collect { parser = ResultScreenParser(it) } }
    }

    /** Called from the analysis thread for each analysed preview frame. */
    fun onLiveFrame(frame: LiveFrame) {
        val state = _uiState.value
        if (state.isProcessing || state.phase == LivePhase.CAPTURING) return
        val page = frame.page
        val parsed = page?.let(parser::parse)
        if (parsed != null) consensus.add(parsed, frame.quality.weight)

        val locked = consensus.isLocked
        val phase = when {
            locked -> LivePhase.LOCKED
            consensus.frameCount > 0 -> LivePhase.READING
            else -> LivePhase.SEARCHING
        }
        val hint = when {
            frame.quality.issue != null -> frame.quality.issue!!.hint
            parsed?.looksLikeResultScreen == false && consensus.frameCount == 0 -> "Fit the whole result screen in the frame"
            phase == LivePhase.LOCKED -> "Got it"
            phase == LivePhase.READING -> "Reading… hold still"
            else -> "Point at a DDR result screen"
        }
        _uiState.update {
            it.copy(
                phase = phase,
                hint = hint,
                progress = consensus.progress,
                live = consensus.current(),
                boxes = page?.lines?.map { l -> l.box }.orEmpty(),
                frameWidth = page?.width ?: it.frameWidth,
                frameHeight = page?.height ?: it.frameHeight,
            )
        }
        if (locked && autoCapture.value) requestStill()
    }

    fun requestStill() {
        if (_uiState.value.phase == LivePhase.CAPTURING || _uiState.value.isProcessing) return
        _uiState.update { it.copy(phase = LivePhase.CAPTURING) }
        _events.trySend(ScanEvent.TakeStill)
    }

    /** A full-resolution still, from the shutter button or an auto-capture. */
    fun onPhotoCaptured(bitmap: Bitmap, rotationDegrees: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, message = null) }
            runCatching { pipeline.run(bitmap, rotationDegrees) }
                .onSuccess { (page, checked) ->
                    // The still has the most pixels, so it gets a double vote,
                    // but the live frames can still outvote one bad read.
                    consensus.add(checked.result, weight = 2f)
                    val merged = if (consensus.frameCount > 0) consensus.current() else checked.result
                    if (!merged.looksLikeResultScreen) {
                        fail("That doesn't look like a result screen. Try again closer.")
                        return@launch
                    }
                    drafts.put(ScanDraft(ScanValidator.check(merged), PlaySource.CAMERA, Instant.now(clock), page.text))
                    consensus.reset()
                    _uiState.value = ScanUiState()
                    _events.send(ScanEvent.OpenReview)
                }
                .onFailure { fail("Text recognition failed: ${it.message}") }
        }
    }

    fun onCaptureFailed(error: Throwable) = fail("Capture failed: ${error.message}")

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private fun fail(message: String) {
        // Start the vote over, or a locked consensus would re-trigger at once.
        consensus.reset()
        _uiState.update { it.copy(isProcessing = false, phase = LivePhase.SEARCHING, message = message) }
    }

}
