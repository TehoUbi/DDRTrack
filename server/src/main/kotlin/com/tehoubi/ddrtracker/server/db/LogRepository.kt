package com.tehoubi.ddrtracker.server.db

import com.tehoubi.ddrtracker.core.api.LogEntry
import com.tehoubi.ddrtracker.core.api.LogPage
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Each player's private play log: the app's backup. Entries are stored as
 * JSON; the server only looks at the id and edit time to merge them.
 */
class LogRepository(private val db: Database) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Stores [entries], keeping whichever copy of each was edited last.
     * Returns how many were stored; an older copy than the server's is
     * skipped, which is how two phones editing the same play settle.
     */
    suspend fun upsert(playerId: String, entries: List<LogEntry>): Int = db.transaction {
        val player = UUID.fromString(playerId)
        entries.sumOf { e ->
            update(
                """
                INSERT INTO log_entries (player_id, id, data, updated_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (player_id, id) DO UPDATE SET
                    data = EXCLUDED.data, updated_at = EXCLUDED.updated_at, revision = nextval('log_revision')
                WHERE log_entries.updated_at < EXCLUDED.updated_at
                """.trimIndent(),
                player, e.id, json.encodeToString(LogEntry.serializer(), e), e.updatedAt,
            )
        }
    }

    /** Entries changed after revision [after], oldest change first. */
    suspend fun page(playerId: String, after: Long, limit: Int): LogPage = db.transaction {
        val rows = query(
            "SELECT data, revision FROM log_entries WHERE player_id = ? AND revision > ? ORDER BY revision LIMIT ?",
            UUID.fromString(playerId), after, limit + 1,
        ) { json.decodeFromString(LogEntry.serializer(), it.getString("data")) to it.getLong("revision") }
        val page = rows.take(limit)
        LogPage(page.map { it.first }, page.lastOrNull()?.second ?: after, more = rows.size > limit)
    }
}
