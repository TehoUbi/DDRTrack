package com.tehoubi.ddrtracker.data.backup

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.model.PlaySource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupCodecTest {

    private val play = Play(
        id = "p1", songTitle = "Healing Vision ~Angelic mix~", artist = "2MB", style = PlayStyle.SINGLE,
        difficulty = Difficulty.EXPERT, level = 15, score = 999_880, exScore = 1482, grade = Grade.AAA,
        lamp = ClearLamp.PERFECT_FULL_COMBO, judgments = Judgments(452, 12, 0, 0, 34, 0), maxCombo = 464,
        fast = 3, slow = 9, playedAt = Instant.parse("2026-05-24T03:12:00Z"), source = PlaySource.CAMERA,
        scanConfidence = 0.93f, note = "first PFC, \"finally\"",
    )

    @Test
    fun `backup round-trips`() {
        val text = BackupCodec.encode(listOf(play), Instant.parse("2026-05-24T04:00:00Z"))
        val (plays, skipped) = BackupCodec.decode(text)
        assertEquals(listOf(play), plays)
        assertEquals(0, skipped)
    }

    @Test
    fun `grade and lamp are recomputed on restore`() {
        val text = BackupCodec.encode(listOf(play), Instant.EPOCH).replace("\"AAA\"", "\"E\"")
        assertEquals(Grade.AAA, BackupCodec.decode(text).first.single().grade)
    }

    @Test
    fun `bad rows are skipped, bad files refused`() {
        val text = BackupCodec.encode(listOf(play, play.copy(id = "p2")), Instant.EPOCH)
            .replaceFirst("\"level\": 15", "\"level\": 40")
        val (plays, skipped) = BackupCodec.decode(text)
        assertEquals(1, plays.size)
        assertEquals(1, skipped)
        assertFailsWith<BackupFormatException> { BackupCodec.decode("{\"hello\":1}") }
        assertFailsWith<BackupFormatException> { BackupCodec.decode("not json") }
    }

    @Test
    fun `csv quotes awkward fields`() {
        val lines = BackupCodec.csv(listOf(play)).trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("played_at,song,artist"))
        assertTrue(lines[1].contains(",Healing Vision ~Angelic mix~,2MB,SP,Expert,15,999880,1482,99.20,AAA,Perfect Full Combo,"))
        assertTrue(lines[1].endsWith(",camera"))
    }
}
