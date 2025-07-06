package com.tehoubi.ddrtracker.server.db

import com.tehoubi.ddrtracker.core.api.ChartSummary
import com.tehoubi.ddrtracker.core.api.ComparedChart
import com.tehoubi.ddrtracker.core.api.Leaderboard
import com.tehoubi.ddrtracker.core.api.LeaderboardEntry
import com.tehoubi.ddrtracker.core.api.RejectedScore
import com.tehoubi.ddrtracker.core.api.ScoreSubmission
import com.tehoubi.ddrtracker.core.api.SubmissionRules
import com.tehoubi.ddrtracker.core.api.SubmitScoresResponse
import com.tehoubi.ddrtracker.core.scoring.DdrScoring
import com.tehoubi.ddrtracker.core.songs.SongKey
import java.sql.Connection
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.util.UUID

class ScoreRepository(private val db: Database, private val clock: Clock = Clock.systemUTC()) {

    /**
     * Stores every valid play in one transaction. Grade and lamp are
     * recomputed here rather than trusted from the client.
     */
    suspend fun submit(playerId: String, scores: List<ScoreSubmission>): SubmitScoresResponse = db.transaction {
        val player = UUID.fromString(playerId)
        val now = clock.millis()
        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<RejectedScore>()
        for (s in scores) {
            val reason = SubmissionRules.rejectionReason(s, now)
            if (reason != null) {
                rejected += RejectedScore(s.clientId, reason)
                continue
            }
            val chartId = upsertChart(s)
            val j = SubmissionRules.judgments(s)!!
            update(
                """
                INSERT INTO scores (player_id, chart_id, client_id, score, ex_score, grade, lamp,
                    marvelous, perfect, great, good, ok, miss, max_combo, failed, played_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (player_id, client_id) DO NOTHING
                """.trimIndent(),
                player, chartId, s.clientId, s.score, s.exScore,
                DdrScoring.grade(s.score, s.failed).name, DdrScoring.lamp(j, s.failed).name,
                j.marvelous, j.perfect, j.great, j.good, j.ok, j.miss, s.maxCombo, s.failed,
                Instant.ofEpochMilli(s.playedAt),
            )
            accepted += s.clientId
        }
        SubmitScoresResponse(accepted, rejected)
    }

    private fun Connection.upsertChart(s: ScoreSubmission): Long = query(
        """
        INSERT INTO charts (song_key, song_title, style, difficulty, level) VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (song_key, style, difficulty)
            DO UPDATE SET song_title = EXCLUDED.song_title, level = EXCLUDED.level
        RETURNING id
        """.trimIndent(),
        SongKey.of(s.songTitle), s.songTitle.trim(), s.style, s.difficulty, s.level.toShort(),
    ) { it.getLong(1) }.single()

    /** Removes a play the player deleted from their log. */
    suspend fun delete(playerId: String, clientId: String): Boolean = db.transaction {
        update("DELETE FROM scores WHERE player_id = ? AND client_id = ?", UUID.fromString(playerId), clientId) > 0
    }

    /**
     * Ranks every player's best on one chart. Ties on score fall back to EX
     * score and share a rank; [viewerId]'s own row is returned even when it
     * sits below [limit].
     */
    suspend fun leaderboard(
        songKey: String,
        style: String,
        difficulty: String,
        limit: Int,
        viewerId: String?,
    ): Leaderboard? = db.transaction {
        data class Chart(val id: Long, val title: String, val level: Int)
        val chart = query(
            "SELECT id, song_title, level FROM charts WHERE song_key = ? AND style = ? AND difficulty = ?",
            songKey, style, difficulty,
        ) { Chart(it.getLong("id"), it.getString("song_title"), it.getInt("level")) }.firstOrNull()
            ?: return@transaction null

        val viewer = viewerId?.let(UUID::fromString)
        data class Row(val entry: LeaderboardEntry, val players: Int, val beat: Double)
        val rows = query(
            """
            WITH ranked AS (
                SELECT pb.player_id, p.display_name, pb.score, pb.ex_score, pb.grade, pb.lamp, pb.played_at,
                       RANK() OVER (ORDER BY pb.score DESC, pb.ex_score DESC) AS rank,
                       PERCENT_RANK() OVER (ORDER BY pb.score, pb.ex_score) AS beat,
                       COUNT(*) OVER () AS players
                FROM personal_bests pb JOIN players p ON p.id = pb.player_id
                WHERE pb.chart_id = ?
            )
            SELECT * FROM ranked WHERE rank <= ? OR player_id = ?::uuid
            ORDER BY rank, played_at
            """.trimIndent(),
            chart.id, limit, viewer,
        ) { Row(it.toEntry(), it.getInt("players"), it.getDouble("beat")) }

        val mine = viewer?.let { v -> rows.firstOrNull { it.entry.playerId == v.toString() } }
        Leaderboard(
            songKey = songKey,
            songTitle = chart.title,
            style = style,
            difficulty = difficulty,
            level = chart.level,
            players = rows.firstOrNull()?.players ?: 0,
            entries = rows.filter { it.entry.rank <= limit }.map { it.entry },
            you = mine?.entry,
            youBeat = mine?.beat,
        )
    }

    /** Charts both players have scores on, with each one's best. */
    suspend fun compare(playerId: String, rivalId: String): List<ComparedChart> = db.transaction {
        query(
            """
            SELECT c.song_key, c.song_title, c.style, c.difficulty, c.level, a.score AS a_score, b.score AS b_score
            FROM personal_bests a
            JOIN personal_bests b ON b.chart_id = a.chart_id AND b.player_id = ?
            JOIN charts c ON c.id = a.chart_id
            WHERE a.player_id = ?
            ORDER BY c.level DESC, c.song_title, c.style, c.difficulty
            """.trimIndent(),
            UUID.fromString(rivalId), UUID.fromString(playerId),
        ) {
            ComparedChart(
                songKey = it.getString("song_key"),
                songTitle = it.getString("song_title"),
                style = it.getString("style"),
                difficulty = it.getString("difficulty"),
                level = it.getInt("level"),
                playerScore = it.getInt("a_score"),
                rivalScore = it.getInt("b_score"),
            )
        }
    }

    /** Charts with the most players, for discovering who else plays what. */
    suspend fun popular(limit: Int): List<ChartSummary> = db.transaction {
        query(
            """
            SELECT c.song_key, c.song_title, c.style, c.difficulty, c.level,
                   count(DISTINCT s.player_id) AS players, count(*) AS plays, max(s.score) AS top_score
            FROM scores s JOIN charts c ON c.id = s.chart_id
            GROUP BY c.id
            ORDER BY players DESC, plays DESC, c.song_title
            LIMIT ?
            """.trimIndent(),
            limit,
        ) {
            ChartSummary(
                songKey = it.getString("song_key"),
                songTitle = it.getString("song_title"),
                style = it.getString("style"),
                difficulty = it.getString("difficulty"),
                level = it.getInt("level"),
                players = it.getInt("players"),
                plays = it.getInt("plays"),
                topScore = it.getInt("top_score"),
            )
        }
    }

    private fun ResultSet.toEntry() = LeaderboardEntry(
        rank = getInt("rank"),
        playerId = getString("player_id"),
        displayName = getString("display_name"),
        score = getInt("score"),
        exScore = getInt("ex_score"),
        grade = getString("grade"),
        lamp = getString("lamp"),
        playedAt = instant("played_at")!!.toEpochMilli(),
    )
}
