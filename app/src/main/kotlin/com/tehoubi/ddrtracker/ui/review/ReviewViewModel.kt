package com.tehoubi.ddrtracker.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.ocr.StatLabel
import com.tehoubi.ddrtracker.core.scoring.DdrScoring
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
import com.tehoubi.ddrtracker.scan.ScanDraft
import com.tehoubi.ddrtracker.scan.ScanDraftStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val drafts: ScanDraftStore,
    private val plays: PlayRepository,
) : ViewModel() {

    val draft: StateFlow<ScanDraft?> = drafts.draft

    /** The play the draft would save as, or null while fields are missing. */
    fun toPlay(draft: ScanDraft): Play? {
        val r = draft.scan.result
        val score = r.score?.value ?: return null
        val j = r.judgments().takeIf { r.hasAllJudgments } ?: return null
        return Play(
            id = UUID.randomUUID().toString(),
            songTitle = r.title?.value ?: return null,
            artist = r.artist?.value,
            style = r.style?.value ?: PlayStyle.SINGLE,
            difficulty = r.difficulty?.value ?: return null,
            level = r.level?.value ?: return null,
            score = score,
            exScore = r.exScore?.value ?: DdrScoring.exScore(j),
            grade = DdrScoring.grade(score, r.failed),
            lamp = DdrScoring.lamp(j, r.failed),
            judgments = j,
            maxCombo = r.maxCombo?.value ?: 0,
            fast = r.stats[StatLabel.FAST]?.value,
            slow = r.stats[StatLabel.SLOW]?.value,
            failed = r.failed,
            playedAt = draft.capturedAt,
            source = draft.source,
            scanConfidence = listOfNotNull(r.title, r.score, r.difficulty, r.level).minOfOrNull { it.confidence },
        )
    }

    fun save(play: Play, onDone: () -> Unit) {
        viewModelScope.launch {
            plays.savePlay(play).onSuccess {
                drafts.clear()
                onDone()
            }
        }
    }

    fun discard() = drafts.clear()
}
