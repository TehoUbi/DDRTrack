package com.tehoubi.ddrtracker.core.stats

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.songs.SongKey
import java.time.Instant

/** What the statistics need from a play; the app's and server's models implement it. */
interface PlayLike {
    val id: String
    val songTitle: String
    val style: PlayStyle
    val difficulty: Difficulty
    val level: Int
    val score: Int
    val exScore: Int
    val grade: Grade
    val lamp: ClearLamp
    val judgments: Judgments
    val fast: Int?
    val slow: Int?
    val failed: Boolean
    val playedAt: Instant

    /** Identifies the chart regardless of how the title was typed. */
    val chartKey: String get() = SongKey.chart(SongKey.of(songTitle), style.name, difficulty.name)

    /** EX score as a share of the chart's maximum, 0..1. */
    val exRatio: Double
        get() = judgments.total.takeIf { it > 0 }?.let { exScore / (3.0 * it) } ?: 0.0
}

/** Personal-best ordering: score, then EX score, then the earlier play. */
val bestFirst: Comparator<PlayLike> = compareByDescending<PlayLike> { it.score }
    .thenByDescending { it.exScore }
    .thenBy { it.playedAt }
