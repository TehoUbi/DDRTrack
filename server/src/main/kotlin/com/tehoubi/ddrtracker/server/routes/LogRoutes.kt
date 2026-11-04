package com.tehoubi.ddrtracker.server.routes

import com.tehoubi.ddrtracker.core.api.LogEntry
import com.tehoubi.ddrtracker.core.api.LogUpload
import com.tehoubi.ddrtracker.core.api.LogUploadResponse
import com.tehoubi.ddrtracker.server.BadRequestException
import com.tehoubi.ddrtracker.server.currentPlayer
import com.tehoubi.ddrtracker.server.db.LogRepository
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put

/** Most entries per upload, and the most one page of a restore returns. */
const val LOG_BATCH = 500

/** The signed-in player's private log: only ever their own, so the paths say "me". */
fun Route.logRoutes(logs: LogRepository) {
    authenticate("api") {
        put("/v1/me/log") {
            val upload = call.receive<LogUpload>()
            if (upload.entries.size > LOG_BATCH) throw BadRequestException("At most $LOG_BATCH entries per request")
            upload.entries.forEach { validate(it) }
            call.respond(LogUploadResponse(logs.upsert(call.currentPlayer().playerId, upload.entries)))
        }

        get("/v1/me/log") {
            val after = call.request.queryParameters["after"]?.toLongOrNull() ?: 0
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, LOG_BATCH) ?: LOG_BATCH
            call.respond(logs.page(call.currentPlayer().playerId, after, limit))
        }
    }
}

/** Bounds, not rules: the log keeps whatever the player saved, but not without limit. */
private fun validate(e: LogEntry) {
    if (e.id.isBlank() || e.id.length > 64) throw BadRequestException("Entry ids must be 1-64 characters")
    if (e.songTitle.length > 200 || (e.artist?.length ?: 0) > 200) throw BadRequestException("Title or artist too long in ${e.id}")
    if ((e.note?.length ?: 0) > 2000) throw BadRequestException("Note too long in ${e.id}")
}
