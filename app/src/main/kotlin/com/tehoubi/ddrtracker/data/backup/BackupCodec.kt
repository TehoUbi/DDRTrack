package com.tehoubi.ddrtracker.data.backup

import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.scoring.DdrScoring
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.model.PlaySource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Backup file format. Versioned so old backups keep restoring. Grade and
 * lamp are written for readability but recomputed on restore, so a
 * hand-edited backup can't contradict its own numbers.
 */
@Serializable
data class Backup(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: String,
    val plays: List<BackupPlay>,
) {
    companion object {
        const val FORMAT = "ddr-tracker-backup"
    }
}

@Serializable
data class BackupPlay(
    val id: String,
    val songTitle: String,
    val artist: String? = null,
    val style: String,
    val difficulty: String,
    val level: Int,
    val score: Int,
    val exScore: Int,
    val grade: String? = null,
    val lamp: String? = null,
    val marvelous: Int,
    val perfect: Int,
    val great: Int,
    val good: Int,
    val ok: Int,
    val miss: Int,
    val maxCombo: Int = 0,
    val fast: Int? = null,
    val slow: Int? = null,
    val failed: Boolean = false,
    val playedAt: String,
    val source: String? = null,
    val scanConfidence: Float? = null,
    val note: String? = null,
)

class BackupFormatException(message: String) : IllegalArgumentException(message)

object BackupCodec {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(plays: List<Play>, now: Instant): String =
        json.encodeToString(Backup.serializer(), Backup(exportedAt = now.toString(), plays = plays.sortedBy { it.playedAt }.map { it.toBackup() }))

    /** Parses a backup; plays that don't make sense are skipped and counted. */
    fun decode(text: String): Pair<List<Play>, Int> {
        val backup = runCatching { json.decodeFromString(Backup.serializer(), text) }
            .getOrElse { throw BackupFormatException("Not a DDR Tracker backup") }
        if (backup.format != Backup.FORMAT) throw BackupFormatException("Not a DDR Tracker backup")
        if (backup.version > 1) throw BackupFormatException("Backup is from a newer version of the app")
        val plays = backup.plays.mapNotNull { runCatching { it.toPlay() }.getOrNull() }
        return plays to (backup.plays.size - plays.size)
    }

    private fun Play.toBackup() = BackupPlay(
        id = id, songTitle = songTitle, artist = artist, style = style.name, difficulty = difficulty.name, level = level,
        score = score, exScore = exScore, grade = grade.name, lamp = lamp.name,
        marvelous = judgments.marvelous, perfect = judgments.perfect, great = judgments.great,
        good = judgments.good, ok = judgments.ok, miss = judgments.miss, maxCombo = maxCombo,
        fast = fast, slow = slow, failed = failed, playedAt = playedAt.toString(), source = source.name,
        scanConfidence = scanConfidence, note = note,
    )

    private fun BackupPlay.toPlay(): Play {
        val j = Judgments(marvelous, perfect, great, good, ok, miss)
        require(level in 1..19 && score in 0..DdrScoring.MAX_SCORE)
        return Play(
            id = id,
            songTitle = songTitle.trim().also { require(it.isNotEmpty()) },
            artist = artist,
            style = PlayStyle.valueOf(style),
            difficulty = Difficulty.valueOf(difficulty),
            level = level,
            score = score,
            exScore = exScore,
            grade = DdrScoring.grade(score, failed),
            lamp = DdrScoring.lamp(j, failed),
            judgments = j,
            maxCombo = maxCombo,
            fast = fast,
            slow = slow,
            failed = failed,
            playedAt = Instant.parse(playedAt),
            source = source?.let { runCatching { PlaySource.valueOf(it) }.getOrNull() } ?: PlaySource.IMPORT,
            scanConfidence = scanConfidence,
            note = note,
        )
    }

    // --- CSV -------------------------------------------------------------

    private val csvHeader = listOf(
        "played_at", "song", "artist", "style", "difficulty", "level", "score", "ex_score", "ex_percent", "grade", "lamp",
        "marvelous", "perfect", "great", "good", "ok", "miss", "max_combo", "fast", "slow", "failed", "session", "source",
    )

    /** One row per play, oldest first, for spreadsheets. RFC 4180 quoting. */
    fun csv(plays: List<Play>): String = buildString {
        appendLine(csvHeader.joinToString(","))
        plays.sortedBy { it.playedAt }.forEach { p ->
            val j = p.judgments
            val row = listOf(
                p.playedAt.toString(), p.songTitle, p.artist.orEmpty(), p.style.code, p.difficulty.label, p.level,
                p.score, p.exScore, "%.2f".format(java.util.Locale.ROOT, p.exRatio * 100), p.grade.label, p.lamp.label,
                j.marvelous, j.perfect, j.great, j.good, j.ok, j.miss, p.maxCombo, p.fast ?: "", p.slow ?: "",
                p.failed, p.sessionId.orEmpty(), p.source.name.lowercase(),
            )
            appendLine(row.joinToString(",") { escape(it.toString()) })
        }
    }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
}

