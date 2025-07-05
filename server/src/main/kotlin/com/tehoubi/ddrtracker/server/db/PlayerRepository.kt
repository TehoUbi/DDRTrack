package com.tehoubi.ddrtracker.server.db

import com.tehoubi.ddrtracker.core.api.PlayerProfile
import com.tehoubi.ddrtracker.core.api.PlayerSummary
import com.tehoubi.ddrtracker.server.auth.Tokens
import org.postgresql.util.PSQLException
import java.util.UUID

class DisplayNameTakenException(name: String) : RuntimeException("Display name \"$name\" is taken")

class PlayerRepository(private val db: Database) {

    /** Creates a player and their first token. Returns (summary, token). */
    suspend fun register(displayName: String): Pair<PlayerSummary, String> = db.transaction {
        val id = UUID.randomUUID()
        val token = Tokens.generate()
        try {
            update("INSERT INTO players (id, display_name) VALUES (?, ?)", id, displayName)
        } catch (e: PSQLException) {
            if (e.sqlState == UNIQUE_VIOLATION) throw DisplayNameTakenException(displayName)
            throw e
        }
        update("INSERT INTO api_tokens (token_hash, player_id) VALUES (?, ?)", Tokens.hash(token), id)
        PlayerSummary(id.toString(), displayName) to token
    }

    /** The player a bearer token belongs to, or null. Records the use. */
    suspend fun authenticate(token: String): PlayerSummary? = db.transaction {
        val hash = Tokens.hash(token)
        val player = query(
            """
            SELECT p.id, p.display_name FROM api_tokens t JOIN players p ON p.id = t.player_id
            WHERE t.token_hash = ?
            """.trimIndent(),
            hash,
        ) { PlayerSummary(it.getString("id"), it.getString("display_name")) }.firstOrNull()
        if (player != null) update("UPDATE api_tokens SET last_used_at = now() WHERE token_hash = ?", hash)
        player
    }

    suspend fun rename(playerId: String, displayName: String): PlayerSummary = db.transaction {
        try {
            update("UPDATE players SET display_name = ? WHERE id = ?", displayName, UUID.fromString(playerId))
        } catch (e: PSQLException) {
            if (e.sqlState == UNIQUE_VIOLATION) throw DisplayNameTakenException(displayName)
            throw e
        }
        PlayerSummary(playerId, displayName)
    }

    suspend fun find(playerId: String): PlayerSummary? = db.transaction {
        val id = runCatching { UUID.fromString(playerId) }.getOrNull() ?: return@transaction null
        query("SELECT id, display_name FROM players WHERE id = ?", id) {
            PlayerSummary(it.getString("id"), it.getString("display_name"))
        }.firstOrNull()
    }

    /** Players whose name starts with (or failing that, contains) [prefix]. */
    suspend fun search(prefix: String, limit: Int = 20): List<PlayerSummary> = db.transaction {
        val escaped = prefix.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        query(
            """
            SELECT id, display_name FROM players
            WHERE lower(display_name) LIKE ? ESCAPE '\'
            ORDER BY (lower(display_name) LIKE ? ESCAPE '\') DESC, lower(display_name)
            LIMIT ?
            """.trimIndent(),
            "%$escaped%", "$escaped%", limit,
        ) { PlayerSummary(it.getString("id"), it.getString("display_name")) }
    }

    /** Profile with summary stats over the player's personal bests. */
    suspend fun profile(playerId: String): PlayerProfile? = db.transaction {
        val id = runCatching { UUID.fromString(playerId) }.getOrNull() ?: return@transaction null
        query(
            """
            SELECT p.id, p.display_name, p.created_at,
                   (SELECT count(*) FROM scores s WHERE s.player_id = p.id) AS plays,
                   (SELECT max(played_at) FROM scores s WHERE s.player_id = p.id) AS last_played_at,
                   count(pb.chart_id) AS charts,
                   count(*) FILTER (WHERE pb.grade = 'AAA') AS aaa,
                   count(*) FILTER (WHERE pb.lamp IN ('FULL_COMBO', 'GREAT_FULL_COMBO', 'PERFECT_FULL_COMBO', 'MARVELOUS_FULL_COMBO')) AS fcs,
                   avg(pb.score)::int AS average_best
            FROM players p LEFT JOIN personal_bests pb ON pb.player_id = p.id
            WHERE p.id = ?
            GROUP BY p.id
            """.trimIndent(),
            id,
        ) {
            PlayerProfile(
                playerId = it.getString("id"),
                displayName = it.getString("display_name"),
                createdAt = it.instant("created_at")!!.toEpochMilli(),
                plays = it.getInt("plays"),
                charts = it.getInt("charts"),
                aaaCharts = it.getInt("aaa"),
                fullComboCharts = it.getInt("fcs"),
                averageBest = (it.getObject("average_best") as Number?)?.toInt(),
                lastPlayedAt = it.instant("last_played_at")?.toEpochMilli(),
            )
        }.firstOrNull()
    }

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
