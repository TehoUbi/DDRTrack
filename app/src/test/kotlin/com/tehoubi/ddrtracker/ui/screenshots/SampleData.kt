package com.tehoubi.ddrtracker.ui.screenshots

import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.scoring.DdrScoring
import com.tehoubi.ddrtracker.core.stats.Sessions
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.model.PlaySession
import com.tehoubi.ddrtracker.domain.model.PlaySource
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/** A deterministic, plausible log: a player climbing from 12s to 15s over three months. */
object SampleData {

    private val charts = listOf(
        Triple("Butterfly", Difficulty.EXPERT, 12), Triple("B4U", Difficulty.EXPERT, 12),
        Triple("AM-3P", Difficulty.EXPERT, 12), Triple("PARANOiA", Difficulty.EXPERT, 14),
        Triple("MAX 300", Difficulty.EXPERT, 15), Triple("Xepher", Difficulty.DIFFICULT, 13),
        Triple("Healing Vision ~Angelic mix~", Difficulty.EXPERT, 15), Triple("TRIP MACHINE CLIMAX", Difficulty.EXPERT, 14),
        Triple("CANDY☆", Difficulty.DIFFICULT, 11), Triple("DEAD END", Difficulty.EXPERT, 13),
        Triple("Pluto Relinquish", Difficulty.CHALLENGE, 16), Triple("SABER WING", Difficulty.EXPERT, 13),
    )

    val plays: List<Play> by lazy {
        val rnd = Random(42)
        val start = Instant.parse("2026-02-07T19:00:00Z")
        val out = mutableListOf<Play>()
        for (week in 0 until 15) {
            repeat(if (week % 4 == 3) 1 else 2) { s ->
                var t = start.plus(Duration.ofDays(week * 7L + s * 3L))
                repeat(6 + rnd.nextInt(5)) {
                    val (title, diff, level) = charts[rnd.nextInt(charts.size)]
                    val skill = 0.55 + week * 0.025 - (level - 12) * 0.06
                    val notes = 300 + level * 20
                    val miss = (rnd.nextDouble() * 12 * (1.2 - skill)).toInt().coerceAtLeast(0)
                    val good = (rnd.nextDouble() * 6 * (1.1 - skill)).toInt().coerceAtLeast(0)
                    val great = (rnd.nextDouble() * 40 * (1.1 - skill)).toInt().coerceAtLeast(0)
                    val perfect = (notes * (0.35 - skill * 0.25) + rnd.nextInt(10)).toInt().coerceAtLeast(0)
                    val j = Judgments(notes - miss - good - great - perfect, perfect, great, good, 24, miss)
                    val score = DdrScoring.moneyScore(j)
                    out += Play(
                        id = "p${out.size}", songTitle = title, style = PlayStyle.SINGLE, difficulty = diff, level = level,
                        score = score, exScore = DdrScoring.exScore(j), grade = DdrScoring.grade(score),
                        lamp = DdrScoring.lamp(j), judgments = j, maxCombo = notes - miss * 20,
                        fast = rnd.nextInt(30), slow = rnd.nextInt(40), playedAt = t, source = PlaySource.CAMERA,
                        scanConfidence = 0.9f,
                    )
                    t = t.plus(Duration.ofMinutes(4L + rnd.nextInt(4)))
                }
            }
        }
        val sessionOf = Sessions.assign(out)
        out.map { it.copy(sessionId = sessionOf[it.id]) }.sortedByDescending { it.playedAt }
    }

    val sessions: List<PlaySession> by lazy {
        Sessions.group(plays).map {
            PlaySession(it.id, it.startedAt, it.endedAt, it.playCount, it.averageScore, it.bestScore, it.averageExRatio, it.stepsHit, it.highestLevel)
        }.sortedByDescending { it.startedAt }
    }
}
