package com.tehoubi.ddrtracker.server.routes

import com.tehoubi.ddrtracker.core.api.Comparison
import com.tehoubi.ddrtracker.core.api.SubmissionRules
import com.tehoubi.ddrtracker.core.api.SubmitScoresRequest
import com.tehoubi.ddrtracker.server.BadRequestException
import com.tehoubi.ddrtracker.server.NotFoundException
import com.tehoubi.ddrtracker.server.PlayerPrincipal
import com.tehoubi.ddrtracker.server.currentPlayer
import com.tehoubi.ddrtracker.server.db.PlayerRepository
import com.tehoubi.ddrtracker.server.db.ScoreRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.scoreRoutes(players: PlayerRepository, scores: ScoreRepository) {
    authenticate("api") {
        post("/v1/scores") {
            val request = call.receive<SubmitScoresRequest>()
            if (request.scores.size > SubmissionRules.MAX_BATCH) {
                throw BadRequestException("At most ${SubmissionRules.MAX_BATCH} scores per request")
            }
            call.respond(scores.submit(call.currentPlayer().playerId, request.scores))
        }

        delete("/v1/scores/{clientId}") {
            val removed = scores.delete(call.currentPlayer().playerId, call.parameters["clientId"]!!)
            call.respond(if (removed) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }
    }

    authenticate("api", optional = true) {
        get("/v1/charts/{songKey}/{style}/{difficulty}/leaderboard") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
            val p = call.parameters
            val board = scores.leaderboard(
                p["songKey"]!!, p["style"]!!.uppercase(), p["difficulty"]!!.uppercase(), limit,
                call.principal<PlayerPrincipal>()?.player?.playerId,
            ) ?: throw NotFoundException("Nobody has submitted this chart yet")
            call.respond(board)
        }

        // "me" stands for the authenticated caller on either side.
        get("/v1/players/{id}/compare/{rival}") {
            val caller = call.principal<PlayerPrincipal>()?.player?.playerId
            fun resolve(raw: String) = if (raw == "me") caller ?: throw BadRequestException("Sign in to compare against yourself") else raw
            val a = players.find(resolve(call.parameters["id"]!!)) ?: throw NotFoundException("No such player")
            val b = players.find(resolve(call.parameters["rival"]!!)) ?: throw NotFoundException("No such rival")
            val charts = scores.compare(a.playerId, b.playerId)
            call.respond(
                Comparison(
                    player = a,
                    rival = b,
                    wins = charts.count { it.delta > 0 },
                    losses = charts.count { it.delta < 0 },
                    ties = charts.count { it.delta == 0 },
                    charts = charts,
                )
            )
        }
    }

    get("/v1/charts/popular") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        call.respond(scores.popular(limit))
    }
}
