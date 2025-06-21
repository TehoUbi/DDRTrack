package com.tehoubi.ddrtracker.core.stats

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.scoring.DdrScoring
import java.time.Instant

internal data class TestPlay(
    override val id: String,
    override val songTitle: String,
    override val level: Int,
    override val score: Int,
    override val playedAt: Instant,
    override val difficulty: Difficulty = Difficulty.EXPERT,
    override val style: PlayStyle = PlayStyle.SINGLE,
    override val judgments: Judgments = Judgments(marvelous = 300, perfect = 50, great = 10, ok = 20),
    override val fast: Int? = null,
    override val slow: Int? = null,
    override val failed: Boolean = false,
    val fc: Boolean = false,
) : PlayLike {
    override val exScore: Int get() = DdrScoring.exScore(judgments)
    override val grade: Grade get() = DdrScoring.grade(score, failed)
    override val lamp: ClearLamp get() = if (fc) ClearLamp.FULL_COMBO else if (failed) ClearLamp.FAILED else ClearLamp.CLEAR
}

internal fun at(s: String): Instant = Instant.parse(s)
