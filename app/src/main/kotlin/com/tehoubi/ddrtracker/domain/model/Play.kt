package com.tehoubi.ddrtracker.domain.model

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.songs.SongKey
import java.time.Instant

/** How a play got into the log. */
enum class PlaySource { CAMERA, SCREENSHOT, MANUAL, IMPORT, CLOUD }

/** One chart played once: everything the result screen shows. */
data class Play(
    val id: String,
    val songTitle: String,
    val artist: String? = null,
    val style: PlayStyle,
    val difficulty: Difficulty,
    val level: Int,
    val score: Int,
    val exScore: Int,
    val grade: Grade,
    val lamp: ClearLamp,
    val judgments: Judgments,
    val maxCombo: Int,
    val fast: Int? = null,
    val slow: Int? = null,
    val failed: Boolean = false,
    val playedAt: Instant,
    val sessionId: String? = null,
    val source: PlaySource,
    /** Lowest field confidence of the scan it came from, if scanned. */
    val scanConfidence: Float? = null,
    val note: String? = null,
) {
    val songKey: String get() = SongKey.of(songTitle)
    val chart: ChartRef get() = ChartRef(songKey, songTitle, style, difficulty, level)
}

/** Identifies one chart of one song. */
data class ChartRef(
    val songKey: String,
    val songTitle: String,
    val style: PlayStyle,
    val difficulty: Difficulty,
    val level: Int,
) {
    val label: String get() = "${difficulty.shortCode(style)} $level"
}
