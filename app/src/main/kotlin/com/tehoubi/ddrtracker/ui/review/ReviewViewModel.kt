package com.tehoubi.ddrtracker.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tehoubi.ddrtracker.core.songs.Song
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
import com.tehoubi.ddrtracker.scan.ScanDraftStore
import com.tehoubi.ddrtracker.scan.SongCatalogProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

enum class ReviewMode { SCAN, MANUAL, EDIT }

data class ReviewUiState(
    val mode: ReviewMode,
    val form: PlayForm? = null,
    val check: FormCheck? = null,
    val suggestions: List<Song> = emptyList(),
    val saving: Boolean = false,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val drafts: ScanDraftStore,
    private val plays: PlayRepository,
    catalogs: SongCatalogProvider,
    clock: Clock,
) : ViewModel() {

    private val mode = ReviewMode.valueOf(savedState.get<String>("mode") ?: ReviewMode.SCAN.name)
    private val form = MutableStateFlow<PlayForm?>(null)
    private val saving = MutableStateFlow(false)
    private val titleQuery = MutableStateFlow("")

    private val suggestions = combine(catalogs.catalog, titleQuery) { catalog, q ->
        if (q.length < 2 || catalog.find(q) != null) emptyList() else catalog.suggest(q, limit = 5)
    }

    val uiState: StateFlow<ReviewUiState> = combine(form, suggestions, saving) { f, s, sv ->
        ReviewUiState(mode, f, f?.check(), s, sv)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState(mode))

    init {
        viewModelScope.launch {
            form.value = when (mode) {
                ReviewMode.SCAN -> drafts.draft.first()?.let { PlayForm.fromScan(it.scan, it.source, it.capturedAt) }
                ReviewMode.MANUAL -> PlayForm(playedAt = Instant.now(clock))
                ReviewMode.EDIT -> savedState.get<String>("playId")?.let { plays.getPlay(it) }?.let(PlayForm::fromPlay)
            }
        }
    }

    fun edit(transform: (PlayForm) -> PlayForm) {
        form.update { it?.let(transform) }
    }

    fun onTitleChanged(title: String) {
        edit { it.copy(title = title) }
        titleQuery.value = title
    }

    fun onSuggestionPicked(song: Song) {
        edit { it.copy(title = song.title, artist = song.artist ?: it.artist) }
        titleQuery.value = ""
    }

    fun save(onDone: (playId: String) -> Unit) {
        val play = uiState.value.check?.play ?: return
        viewModelScope.launch {
            saving.value = true
            plays.savePlay(play)
                .onSuccess {
                    if (mode == ReviewMode.SCAN) drafts.clear()
                    onDone(play.id)
                }
            saving.value = false
        }
    }

    fun discard() {
        if (mode == ReviewMode.SCAN) drafts.clear()
    }
}
