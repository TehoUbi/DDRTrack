package com.tehoubi.ddrtracker.server

import com.tehoubi.ddrtracker.core.api.ApiError
import com.tehoubi.ddrtracker.core.api.HealthResponse
import com.tehoubi.ddrtracker.core.api.PlayerSummary
import com.tehoubi.ddrtracker.server.db.Database
import com.tehoubi.ddrtracker.server.db.DisplayNameTakenException
import com.tehoubi.ddrtracker.server.db.PlayerRepository
import com.tehoubi.ddrtracker.server.db.ScoreRepository
import com.tehoubi.ddrtracker.server.db.query
import com.tehoubi.ddrtracker.server.routes.REGISTRATION_LIMIT
import com.tehoubi.ddrtracker.server.routes.playerRoutes
import com.tehoubi.ddrtracker.server.routes.scoreRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException as KtorBadRequest
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.hours

class BadRequestException(message: String) : RuntimeException(message)
class NotFoundException(message: String) : RuntimeException(message)

data class PlayerPrincipal(val player: PlayerSummary) : Principal

fun ApplicationCall.currentPlayer(): PlayerSummary = principal<PlayerPrincipal>()!!.player

/** Everything the routes need, built once in main() or by a test. */
class Services(val db: Database, val config: ServerConfig) {
    val players = PlayerRepository(db)
    val scores = ScoreRepository(db)
}

fun main() {
    val config = ServerConfig.fromEnv()
    val db = Database.connect(config.databaseUrl, config.databaseUser, config.databasePassword, config.maxPoolSize)
    db.migrate()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(Services(db, config))
    }.start(wait = true)
}

val ApiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Application.module(services: Services) {
    install(ContentNegotiation) { json(ApiJson) }
    install(CallLogging) { level = Level.INFO }
    install(RateLimit) {
        register(REGISTRATION_LIMIT) {
            rateLimiter(limit = services.config.registrationsPerHour, refillPeriod = 1.hours)
            requestKey { it.request.origin.remoteHost }
        }
    }
    install(Authentication) {
        bearer("api") {
            authenticate { credential -> services.players.authenticate(credential.token)?.let(::PlayerPrincipal) }
        }
    }
    install(StatusPages) {
        exception<BadRequestException> { call, e -> call.respond(HttpStatusCode.BadRequest, ApiError(e.message!!)) }
        exception<KtorBadRequest> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("Malformed request")) }
        exception<NotFoundException> { call, e -> call.respond(HttpStatusCode.NotFound, ApiError(e.message!!)) }
        exception<DisplayNameTakenException> { call, e -> call.respond(HttpStatusCode.Conflict, ApiError(e.message!!)) }
        exception<Throwable> { call, e ->
            call.application.environment.log.error("Unhandled error", e)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Internal error"))
        }
        status(HttpStatusCode.Unauthorized) { call, status -> call.respond(status, ApiError("Missing or invalid token")) }
        status(HttpStatusCode.TooManyRequests) { call, status -> call.respond(status, ApiError("Too many requests, try again later")) }
    }

    routing {
        get("/health") {
            val ok = runCatching { services.db.transaction { query("SELECT 1") { it.getInt(1) } } }.isSuccess
            call.respond(
                if (ok) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                HealthResponse(if (ok) "ok" else "degraded", if (ok) "up" else "down"),
            )
        }
        playerRoutes(services.players)
        scoreRoutes(services.players, services.scores)
    }
}
