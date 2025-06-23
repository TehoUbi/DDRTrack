package com.tehoubi.ddrtracker.ui.history

import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.LEVEL_RANGE
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.songs.SongKey
import com.tehoubi.ddrtracker.domain.model.Play

enum class HistorySort(val label: String) { NEWEST("Newest"), OLDEST("Oldest"), SCORE("Score"), LEVEL("Level") }

data class HistoryFilter(
    val query: String = "",
    val style: PlayStyle? = null,
    val difficulties: Set<Difficulty> = emptySet(),
    val levels: IntRange = LEVEL_RANGE,
    val fullCombosOnly: Boolean = false,
    val bestsOnly: Boolean = false,
    val sort: HistorySort = HistorySort.NEWEST,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || style != null || difficulties.isNotEmpty() ||
            levels != LEVEL_RANGE || fullCombosOnly || bestsOnly

    fun apply(plays: List<Play>, bestIds: Set<String>): List<Play> {
        // Compare slugs without their dashes so "max300" finds "MAX 300".
        val q = SongKey.of(query).replace("-", "").takeIf { query.isNotBlank() }
        val filtered = plays.filter { p ->
            (q == null || p.songKey.replace("-", "").contains(q) || p.songTitle.contains(query.trim(), ignoreCase = true)) &&
                (style == null || p.style == style) &&
                (difficulties.isEmpty() || p.difficulty in difficulties) &&
                p.level in levels &&
                (!fullCombosOnly || p.lamp.isFullCombo) &&
                (!bestsOnly || p.id in bestIds)
        }
        return when (sort) {
            HistorySort.NEWEST -> filtered.sortedByDescending { it.playedAt }
            HistorySort.OLDEST -> filtered.sortedBy { it.playedAt }
            HistorySort.SCORE -> filtered.sortedWith(compareByDescending<Play> { it.score }.thenByDescending { it.exScore })
            HistorySort.LEVEL -> filtered.sortedWith(compareByDescending<Play> { it.level }.thenByDescending { it.score })
        }
    }
}
