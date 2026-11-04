package com.tehoubi.ddrtracker.server.db

import com.tehoubi.ddrtracker.core.api.PlayerProfile
import com.tehoubi.ddrtracker.core.api.PlayerSummary
import com.tehoubi.ddrtracker.server.auth.Tokens
import org.postgresql.util.PSQLException
import java.util.UUID

class DisplayNameTakenException(name: String) : RuntimeException("Display name \"$name\" is taken")

class PlayerRepository(private val db: Database) {

    /** Creates a player with their first token and a recovery code. Returns (summary, token, recovery code). */
    suspend fun register(displayName: String): Triple<PlayerSummary, String, String> = db.transaction {
        val id = UUID.randomUUID()
        val token = Tokens.generate()
        val code = Tokens.recoveryCode()
        try {
            update(
                "INSERT INTO players (id, display_name, recovery_hash) VALUES (?, ?, ?)",
                id, displayName, Tokens.hash(Tokens.normalizeRecoveryCode(code)),
            )
        } catch (e: PSQLException) {
            if (e.sqlState == UNIQUE_VIOLATION) throw DisplayNameTakenException(displayName)
            throw e
        }
        update("INSERT INTO api_tokens (token_hash, player_id) VALUES (?, ?)", Tokens.hash(token), id)
        Triple(PlayerSummary(id.toString(), displayName), token, code)
    }

    /** Replaces the player's recovery code (the old one stops working) and returns the new one. */
    suspend fun newRecoveryCode(playerId: String): String = db.transaction {
        val code = Tokens.recoveryCode()
        update("UPDATE players SET recovery_hash = ? WHERE id = ?", Tokens.hash(Tokens.normalizeRecoveryCode(code)), UUID.fromString(playerId))
        code
    }

    /**
     * Signs back in with a recovery code: every existing token is revoked (a
     * lost phone loses access) and a new one is issued. Null if no player
     * has that code.
     */
    suspend fun recover(code: String): Pair<PlayerSummary, String>? = db.transaction {
        val normalized = Tokens.normalizeRecoveryCode(code)
        if (normalized.isEmpty()) return@transaction null
        val player = query("SELECT id, display_name FROM players WHERE recovery_hash = ?", Tokens.hash(normalized)) {
            PlayerSummary(it.getString("id"), it.getString("display_name"))
        }.firstOrNull() ?: return@transaction null
        val id = UUID.fromString(player.playerId)
        val token = Tokens.generate()
        update("DELETE FROM api_tokens WHERE player_id = ?", id)
        update("INSERT INTO api_tokens (token_hash, player_id) VALUES (?, ?)", Tokens.hash(token), id)
        player to token
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
        profiles("WHERE p.id = ?", id).firstOrNull()
    }

    /** Everyone on the server, most recently active first, for browsing. */
    suspend fun all(limit: Int): List<PlayerProfile> = db.transaction {
        profiles("", limit = limit)
    }

    private fun java.sql.Connection.profiles(where: String, vararg args: Any, limit: Int = 1): List<PlayerProfile> =
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
            $where
            GROUP BY p.id
            ORDER BY last_played_at DESC NULLS LAST, lower(p.display_name)
            LIMIT ?
            """.trimIndent(),
            *args, limit,
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
        }

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
