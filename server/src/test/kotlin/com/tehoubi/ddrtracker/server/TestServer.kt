package com.tehoubi.ddrtracker.server

import com.tehoubi.ddrtracker.core.api.RegisterRequest
import com.tehoubi.ddrtracker.core.api.RegisterResponse
import com.tehoubi.ddrtracker.server.db.Database
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

/**
 * A real PostgreSQL for the whole test run (zonky ships the binaries), with
 * every table emptied before each test.
 */
object TestDatabase {
    private val postgres: EmbeddedPostgres by lazy { EmbeddedPostgres.start() }

    val db: Database by lazy { Database(postgres.postgresDatabase).also { it.migrate() } }

    fun reset() {
        db.dataSource.connection.use { c ->
            c.createStatement().use { it.execute("TRUNCATE players, api_tokens, charts, scores RESTART IDENTITY CASCADE") }
        }
    }
}

fun serverTest(config: ServerConfig = ServerConfig(), block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) {
    TestDatabase.reset()
    testApplication {
        application { module(Services(TestDatabase.db, config)) }
        val client = createClient { install(ContentNegotiation) { json(ApiJson) } }
        block(client)
    }
}

suspend fun HttpClient.register(name: String): RegisterResponse =
    post("/v1/players") {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(name))
    }.body()
