package com.tehoubi.ddrtracker.core.ocr

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle

/** A value read off the screen, with how sure the parser is of it (0..1). */
data class Field<out T>(val value: T, val confidence: Float)

/** The labelled rows of the result screen's stats table. */
enum class StatLabel(vararg val spellings: String) {
    MARVELOUS("MARVELOUS"),
    PERFECT("PERFECT"),
    GREAT("GREAT"),
    GOOD("GOOD"),
    OK("OK", "O.K."),
    MISS("MISS"),
    MAX_COMBO("MAX COMBO", "MAXCOMBO"),
    EX_SCORE("EX SCORE", "EXSCORE"),
    FAST("FAST"),
    SLOW("SLOW"),
    /** WORLD's details tab shows the previous best under the score; read so it isn't mistaken for it. */
    BEST_SCORE("BEST SCORE", "BESTSCORE"),
    SCORE("SCORE");

    val isJudgment: Boolean get() = this in JUDGMENTS

    companion object {
        val JUDGMENTS = listOf(MARVELOUS, PERFECT, GREAT, GOOD, OK, MISS)
    }
}

data class ParsedResult(
    val title: Field<String>? = null,
    val artist: Field<String>? = null,
    val style: Field<PlayStyle>? = null,
    val difficulty: Field<Difficulty>? = null,
    val level: Field<Int>? = null,
    val score: Field<Int>? = null,
    val stats: Map<StatLabel, Field<Int>> = emptyMap(),
    val grade: Field<Grade>? = null,
    val lamp: Field<ClearLamp>? = null,
    val failed: Boolean = false,
    /**
     * The "/ 417" after MAX COMBO on WORLD: the chart's step count, which
     * must equal Marvelous + Perfect + Great + Good + Miss.
     */
    val comboTotal: Field<Int>? = null,
    /** Table labels that were recognised, whether or not their value was. */
    val labelsSeen: Set<StatLabel> = stats.keys,
) {
    val exScore: Field<Int>? get() = stats[StatLabel.EX_SCORE]
    val maxCombo: Field<Int>? get() = stats[StatLabel.MAX_COMBO]

    /**
     * True once enough of the stats table is visible to be a result screen.
     * Counts labels, not values: small digits are often unreadable while the
     * labels next to them are fine, and missing values can be worked out.
     */
    val looksLikeResultScreen: Boolean
        get() {
            val judgments = StatLabel.JUDGMENTS.count { it in labelsSeen || it in stats }
            val totals = listOf(StatLabel.EX_SCORE, StatLabel.MAX_COMBO).count { it in labelsSeen || it in stats }
            return judgments >= 4 || (judgments >= 2 && totals >= 1)
        }

    val hasAllJudgments: Boolean get() = StatLabel.JUDGMENTS.all { it in stats }

    /** Judgments, treating unread rows as zero. */
    fun judgments(): Judgments = Judgments(
        marvelous = stats[StatLabel.MARVELOUS]?.value ?: 0,
        perfect = stats[StatLabel.PERFECT]?.value ?: 0,
        great = stats[StatLabel.GREAT]?.value ?: 0,
        good = stats[StatLabel.GOOD]?.value ?: 0,
        ok = stats[StatLabel.OK]?.value ?: 0,
        miss = stats[StatLabel.MISS]?.value ?: 0,
    )

    /** Fields the user will have to fill in by hand. */
    fun missingFields(): List<String> = buildList {
        if (title == null) add("title")
        if (difficulty == null) add("difficulty")
        if (level == null) add("level")
        if (score == null) add("score")
        StatLabel.JUDGMENTS.filter { it !in stats }.forEach { add(it.name.lowercase()) }
    }
}
