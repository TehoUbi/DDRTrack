package com.tehoubi.ddrtracker.scan

import com.tehoubi.ddrtracker.core.ocr.CheckedScan
import com.tehoubi.ddrtracker.domain.model.PlaySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** A scan waiting for the player to confirm it on the review screen. */
data class ScanDraft(
    val scan: CheckedScan,
    val source: PlaySource,
    val capturedAt: Instant,
    val rawText: String,
)

/**
 * Hands the draft from the scan screen to the review screen. Too big and
 * too short-lived for navigation arguments.
 */
@Singleton
class ScanDraftStore @Inject constructor() {
    private val _draft = MutableStateFlow<ScanDraft?>(null)
    val draft: StateFlow<ScanDraft?> = _draft

    fun put(draft: ScanDraft) { _draft.value = draft }
    fun clear() { _draft.value = null }
}
