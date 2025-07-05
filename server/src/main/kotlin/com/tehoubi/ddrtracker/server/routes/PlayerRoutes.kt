package com.tehoubi.ddrtracker.server.routes

import com.tehoubi.ddrtracker.core.api.RegisterRequest
import com.tehoubi.ddrtracker.core.api.RegisterResponse
import com.tehoubi.ddrtracker.core.api.UpdatePlayerRequest
import com.tehoubi.ddrtracker.server.BadRequestException
import com.tehoubi.ddrtracker.server.NotFoundException
import com.tehoubi.ddrtracker.server.currentPlayer
import com.tehoubi.ddrtracker.server.db.PlayerRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

val REGISTRATION_LIMIT = RateLimitName("registration")

private val namePattern = Regex("^[\\p{L}\\p{N} ._\\-]{2,24}$")

/** Trims and checks a display name; letters, digits, space and ._- only. */
fun validDisplayName(raw: String): String {
    val name = raw.trim().replace(Regex("\\s+"), " ")
    if (!namePattern.matches(name)) throw BadRequestException("Display name must be 2-24 letters, digits, spaces or ._-")
    return name
}

fun Route.playerRoutes(players: PlayerRepository) {
    route("/v1/players") {
        rateLimit(REGISTRATION_LIMIT) {
            post {
                val request = call.receive<RegisterRequest>()
                val (player, token) = players.register(validDisplayName(request.displayName))
                call.respond(HttpStatusCode.Created, RegisterResponse(player.playerId, player.displayName, token))
            }
        }

        get("/search") {
            val q = call.request.queryParameters["q"]?.trim().orEmpty()
            if (q.length < 2) throw BadRequestException("Search needs at least two characters")
            call.respond(players.search(q))
        }

        authenticate("api") {
            get("/me") {
                call.respond(players.profile(call.currentPlayer().playerId) ?: throw NotFoundException("No such player"))
            }
            patch("/me") {
                val request = call.receive<UpdatePlayerRequest>()
                call.respond(players.rename(call.currentPlayer().playerId, validDisplayName(request.displayName)))
            }
        }

        get("/{id}") {
            val id = call.parameters["id"]!!
            call.respond(players.profile(id) ?: throw NotFoundException("No such player"))
        }
    }
}
