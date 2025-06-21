package com.tehoubi.ddrtracker.domain.model

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.songs.SongKey
import com.tehoubi.ddrtracker.core.stats.PlayLike
import java.time.Instant

/** How a play got into the log. */
enum class PlaySource { CAMERA, SCREENSHOT, MANUAL, IMPORT, CLOUD }

/** One chart played once: everything the result screen shows. */
data class Play(
    override val id: String,
    override val songTitle: String,
    val artist: String? = null,
    override val style: PlayStyle,
    override val difficulty: Difficulty,
    override val level: Int,
    override val score: Int,
    override val exScore: Int,
    override val grade: Grade,
    override val lamp: ClearLamp,
    override val judgments: Judgments,
    val maxCombo: Int,
    override val fast: Int? = null,
    override val slow: Int? = null,
    override val failed: Boolean = false,
    override val playedAt: Instant,
    val sessionId: String? = null,
    val source: PlaySource,
    /** Lowest field confidence of the scan it came from, if scanned. */
    val scanConfidence: Float? = null,
    val note: String? = null,
) : PlayLike {
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

/** Summary of one visit to the arcade, as stored. */
data class PlaySession(
    val id: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val playCount: Int,
    val averageScore: Int,
    val bestScore: Int,
    val averageExRatio: Double,
    val stepsHit: Int,
    val highestLevel: Int,
)
