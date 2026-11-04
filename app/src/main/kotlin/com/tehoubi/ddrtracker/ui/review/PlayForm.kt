package com.tehoubi.ddrtracker.ui.review

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.LEVEL_RANGE
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.ocr.CheckedScan
import com.tehoubi.ddrtracker.core.ocr.ScanIssue
import com.tehoubi.ddrtracker.core.ocr.ScanValidator
import com.tehoubi.ddrtracker.core.ocr.Severity
import com.tehoubi.ddrtracker.core.ocr.StatLabel
import com.tehoubi.ddrtracker.core.scoring.DdrScoring
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.model.PlaySource
import java.time.Instant
import java.util.UUID

/** A scanned value read with less confidence than this is highlighted, and the play notes it. */
const val LOW_CONFIDENCE = 0.5f

/** When the scan's numbers don't add up, values under this are highlighted too. */
const val DOUBTFUL_CONFIDENCE = 0.75f

/**
 * Editable state of the review / manual-entry form. Numbers are kept as the
 * text the player typed so a half-typed value doesn't jump around.
 */
data class PlayForm(
    val id: String = UUID.randomUUID().toString(),
    val source: PlaySource = PlaySource.MANUAL,
    val title: String = "",
    val artist: String = "",
    val style: PlayStyle = PlayStyle.SINGLE,
    val difficulty: Difficulty? = null,
    val level: String = "",
    val score: String = "",
    val exScore: String = "",
    val judgments: Map<StatLabel, String> = StatLabel.JUDGMENTS.associateWith { "" },
    val maxCombo: String = "",
    val fast: String = "",
    val slow: String = "",
    val failed: Boolean = false,
    val playedAt: Instant,
    val note: String = "",
    val scanConfidence: Float? = null,
    /** Field names the scanner wasn't sure about. */
    val lowConfidence: Set<String> = emptySet(),
    /** Field name -> what OCR originally read, for auto-corrected fields. */
    val repaired: Map<String, Int> = emptyMap(),
    /** Fields OCR couldn't read that were worked out from the other numbers. */
    val inferred: Set<String> = emptySet(),
) {
    fun judgment(label: StatLabel): String = judgments[label].orEmpty()

    fun withJudgment(label: StatLabel, value: String) = copy(judgments = judgments + (label to value.digitsOnly()))

    companion object {
        fun fromScan(scan: CheckedScan, source: PlaySource, capturedAt: Instant): PlayForm {
            val r = scan.result
            // When the score, EX score and step count all agree with the
            // judgments, the numbers are confirmed whatever OCR thought of
            // them; only the title and chart can still be off, and only a
            // particularly unsure read of those is worth a flag.
            val numbers = listOf("score" to r.score) + StatLabel.JUDGMENTS.map { it.name.lowercase() to r.stats[it] }
            val unconfirmed = listOf("title" to r.title, "difficulty" to r.difficulty, "level" to r.level) +
                if (scan.isConsistent) emptyList() else numbers
            val threshold = if (scan.isConsistent) LOW_CONFIDENCE else DOUBTFUL_CONFIDENCE
            val low = unconfirmed.filter { (_, f) -> (f?.confidence ?: 0f) < threshold }.map { it.first }.toSet()
            return PlayForm(
                source = source,
                title = r.title?.value.orEmpty(),
                artist = r.artist?.value.orEmpty(),
                style = r.style?.value ?: PlayStyle.SINGLE,
                difficulty = r.difficulty?.value,
                level = r.level?.value?.toString().orEmpty(),
                score = r.score?.value?.toString().orEmpty(),
                exScore = r.exScore?.value?.toString().orEmpty(),
                judgments = StatLabel.JUDGMENTS.associateWith { r.stats[it]?.value?.toString().orEmpty() },
                maxCombo = r.maxCombo?.value?.toString().orEmpty(),
                fast = r.stats[StatLabel.FAST]?.value?.toString().orEmpty(),
                slow = r.stats[StatLabel.SLOW]?.value?.toString().orEmpty(),
                failed = r.failed,
                playedAt = capturedAt,
                scanConfidence = unconfirmed.mapNotNull { it.second?.confidence }.minOrNull(),
                lowConfidence = low + scan.inferred,
                repaired = scan.repaired,
                inferred = scan.inferred,
            )
        }

        fun fromPlay(play: Play) = PlayForm(
            id = play.id,
            source = play.source,
            title = play.songTitle,
            artist = play.artist.orEmpty(),
            style = play.style,
            difficulty = play.difficulty,
            level = play.level.toString(),
            score = play.score.toString(),
            exScore = play.exScore.toString(),
            judgments = mapOf(
                StatLabel.MARVELOUS to play.judgments.marvelous, StatLabel.PERFECT to play.judgments.perfect,
                StatLabel.GREAT to play.judgments.great, StatLabel.GOOD to play.judgments.good,
                StatLabel.OK to play.judgments.ok, StatLabel.MISS to play.judgments.miss,
            ).mapValues { it.value.toString() },
            maxCombo = play.maxCombo.toString(),
            fast = play.fast?.toString().orEmpty(),
            slow = play.slow?.toString().orEmpty(),
            failed = play.failed,
            playedAt = play.playedAt,
            note = play.note.orEmpty(),
            scanConfidence = play.scanConfidence,
        )
    }
}

/** What the form currently adds up to. */
data class FormCheck(
    /** Field -> problem that blocks saving. */
    val errors: Map<String, String>,
    /** Rule-based warnings that don't block saving. */
    val issues: List<ScanIssue>,
    val grade: Grade?,
    val lamp: ClearLamp?,
    val expectedExScore: Int?,
    val play: Play?,
) {
    val canSave: Boolean get() = play != null
}

fun String.digitsOnly(): String = filter { it.isDigit() }.take(7)

fun PlayForm.check(): FormCheck {
    val errors = linkedMapOf<String, String>()
    if (title.isBlank()) errors["title"] = "Song title is required"
    if (difficulty == null) errors["difficulty"] = "Pick a difficulty"
    val levelValue = level.toIntOrNull()
    if (levelValue == null || levelValue !in LEVEL_RANGE) errors["level"] = "Level is 1–19"
    val scoreValue = score.toIntOrNull()
    if (scoreValue == null || scoreValue > DdrScoring.MAX_SCORE) errors["score"] = "Score is 0–1,000,000"
    else if (scoreValue % 10 != 0) errors["score"] = "Scores always end in 0"

    val counts = StatLabel.JUDGMENTS.associateWith { judgment(it).toIntOrNull() }
    counts.filterValues { it == null }.keys.forEach { errors[it.name.lowercase()] = "Required" }

    val j = if (counts.values.all { it != null }) {
        Judgments(
            counts.getValue(StatLabel.MARVELOUS)!!, counts.getValue(StatLabel.PERFECT)!!,
            counts.getValue(StatLabel.GREAT)!!, counts.getValue(StatLabel.GOOD)!!,
            counts.getValue(StatLabel.OK)!!, counts.getValue(StatLabel.MISS)!!,
        )
    } else null

    val ex = exScore.toIntOrNull()
    val combo = maxCombo.toIntOrNull()
    if (j != null && combo != null && combo > j.total) errors["maxCombo"] = "More than the ${j.total} notes"

    val issues = if (j != null && scoreValue != null && "score" !in errors) {
        ScanValidator.checkManual(j, scoreValue, ex, failed)
            .filter { it.field != "maxCombo" && it.severity >= Severity.INFO }
            .filterNot { it.message.startsWith("Couldn't read") }
    } else emptyList()

    val grade = scoreValue?.takeIf { "score" !in errors }?.let { DdrScoring.grade(it, failed) }
    val lamp = j?.let { DdrScoring.lamp(it, failed) }
    val expectedEx = j?.let(DdrScoring::exScore)

    val play = if (errors.isEmpty() && j != null && grade != null && lamp != null) {
        Play(
            id = id,
            songTitle = title.trim(),
            artist = artist.trim().ifEmpty { null },
            style = style,
            difficulty = difficulty!!,
            level = levelValue!!,
            score = scoreValue!!,
            exScore = ex ?: expectedEx!!,
            grade = grade,
            lamp = lamp,
            judgments = j,
            maxCombo = combo ?: 0,
            fast = fast.toIntOrNull(),
            slow = slow.toIntOrNull(),
            failed = failed,
            playedAt = playedAt,
            source = source,
            scanConfidence = scanConfidence,
            note = note.trim().ifEmpty { null },
        )
    } else null

    return FormCheck(errors, issues, grade, lamp, expectedEx, play)
}
