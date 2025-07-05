package com.tehoubi.ddrtracker.server

import com.tehoubi.ddrtracker.core.api.ApiError
import com.tehoubi.ddrtracker.core.api.HealthResponse
import com.tehoubi.ddrtracker.core.api.PlayerProfile
import com.tehoubi.ddrtracker.core.api.PlayerSummary
import com.tehoubi.ddrtracker.core.api.RegisterRequest
import com.tehoubi.ddrtracker.core.api.UpdatePlayerRequest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlayerRoutesTest {

    @Test
    fun `health reports the database`() = serverTest { client ->
        val r = client.get("/health")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(HealthResponse("ok", "up"), r.body())
    }

    @Test
    fun `register then read your profile`() = serverTest { client ->
        val reg = client.register("  Tehoubi ")
        assertEquals("Tehoubi", reg.displayName)
        assertTrue(reg.token.length >= 40)

        val me = client.get("/v1/players/me") { bearerAuth(reg.token) }
        assertEquals(HttpStatusCode.OK, me.status)
        val profile = me.body<PlayerProfile>()
        assertEquals(reg.playerId, profile.playerId)
        assertEquals(0, profile.plays)

        assertEquals(profile, client.get("/v1/players/${reg.playerId}").body())
    }

    @Test
    fun `names are unique ignoring case`() = serverTest { client ->
        client.register("Rival")
        val r = client.post("/v1/players") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("rival"))
        }
        assertEquals(HttpStatusCode.Conflict, r.status)
        assertEquals("Display name \"rival\" is taken", r.body<ApiError>().error)
    }

    @Test
    fun `bad names are rejected`() = serverTest { client ->
        listOf("x", "a".repeat(25), "<script>").forEach { name ->
            val r = client.post("/v1/players") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(name))
            }
            assertEquals(HttpStatusCode.BadRequest, r.status, name)
        }
    }

    @Test
    fun `me needs a valid token`() = serverTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/players/me").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/players/me") { bearerAuth("nope") }.status)
    }

    @Test
    fun `rename`() = serverTest { client ->
        val a = client.register("Alpha")
        client.register("Bravo")
        val ok = client.patch("/v1/players/me") {
            bearerAuth(a.token); contentType(ContentType.Application.Json); setBody(UpdatePlayerRequest("Alpha2"))
        }
        assertEquals(PlayerSummary(a.playerId, "Alpha2"), ok.body())
        val clash = client.patch("/v1/players/me") {
            bearerAuth(a.token); contentType(ContentType.Application.Json); setBody(UpdatePlayerRequest("BRAVO"))
        }
        assertEquals(HttpStatusCode.Conflict, clash.status)
    }

    @Test
    fun `search finds rivals by prefix first`() = serverTest { client ->
        listOf("Kaito", "Akai", "Kaiser", "Bob").forEach { client.register(it) }
        val found = client.get("/v1/players/search?q=kai").body<List<PlayerSummary>>().map { it.displayName }
        assertEquals(listOf("Kaiser", "Kaito", "Akai"), found)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/players/search?q=k").status)
    }

    @Test
    fun `registration is rate limited`() = serverTest(ServerConfig(registrationsPerHour = 2)) { client ->
        client.register("One1")
        client.register("Two2")
        val r = client.post("/v1/players") { contentType(ContentType.Application.Json); setBody(RegisterRequest("Three3")) }
        assertEquals(HttpStatusCode.TooManyRequests, r.status)
    }

    @Test
    fun `unknown player is 404`() = serverTest { client ->
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/players/00000000-0000-0000-0000-000000000000").status)
        assertNotEquals(HttpStatusCode.InternalServerError, client.get("/v1/players/not-a-uuid").status)
    }
}
