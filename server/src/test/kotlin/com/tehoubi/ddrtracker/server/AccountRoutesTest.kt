package com.tehoubi.ddrtracker.server

import com.tehoubi.ddrtracker.core.api.LogEntry
import com.tehoubi.ddrtracker.core.api.LogPage
import com.tehoubi.ddrtracker.core.api.LogUpload
import com.tehoubi.ddrtracker.core.api.LogUploadResponse
import com.tehoubi.ddrtracker.core.api.PlayerProfile
import com.tehoubi.ddrtracker.core.api.RecoverRequest
import com.tehoubi.ddrtracker.core.api.RecoveryCodeResponse
import com.tehoubi.ddrtracker.core.api.RegisterResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The private play log (the app's backup) and account recovery. */
class AccountRoutesTest {

    private fun entry(id: String, updatedAt: Long, note: String? = null, deleted: Boolean = false) = LogEntry(
        id = id, songTitle = "向日葵サンセット", style = "SINGLE", difficulty = "EXPERT", level = 13,
        score = 991_280, exScore = 977, grade = "AAA", lamp = "GREAT_FULL_COMBO",
        marvelous = 252, perfect = 80, great = 7, good = 0, ok = 18, miss = 0, maxCombo = 339,
        fast = 68, slow = 19, failed = false, playedAt = 1_777_000_000_000L, source = "CAMERA",
        note = note, updatedAt = updatedAt, deleted = deleted,
    )

    private suspend fun HttpClient.upload(token: String, vararg entries: LogEntry) =
        put("/v1/me/log") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(LogUpload(entries.toList())) }

    private suspend fun HttpClient.log(token: String, after: Long = 0, limit: Int? = null): LogPage =
        get("/v1/me/log?after=$after" + (limit?.let { "&limit=$it" } ?: "")) { bearerAuth(token) }.body()

    private suspend fun HttpClient.recover(code: String) =
        post("/v1/players/recover") { contentType(ContentType.Application.Json); setBody(RecoverRequest(code)) }

    @Test
    fun `the log keeps everything and the newer copy wins`() = serverTest { client ->
        val me = client.register("Alpha")
        assertEquals(2, client.upload(me.token, entry("a", 100, note = "first"), entry("b", 100)).body<LogUploadResponse>().stored)
        // An older copy (another phone that hadn't seen the edit) is skipped, a newer one replaces.
        assertEquals(0, client.upload(me.token, entry("a", 50, note = "stale")).body<LogUploadResponse>().stored)
        assertEquals(1, client.upload(me.token, entry("b", 200, deleted = true)).body<LogUploadResponse>().stored)

        val page = client.log(me.token)
        assertEquals(mapOf("a" to "first", "b" to null), page.entries.associate { it.id to it.note })
        assertTrue(page.entries.first { it.id == "b" }.deleted)
        assertEquals(68, page.entries.first().fast)
        assertFalse(page.more)
        // Only what changed since is sent next time.
        assertEquals(0, client.log(me.token, after = page.revision).entries.size)
    }

    @Test
    fun `restores page through entries that share an edit time`() = serverTest { client ->
        val me = client.register("Alpha")
        client.upload(me.token, *(1..7).map { entry("p$it", 500) }.toTypedArray())
        val seen = mutableListOf<String>()
        var after = 0L
        do {
            val page = client.log(me.token, after, limit = 3)
            seen += page.entries.map { it.id }
            after = page.revision
        } while (page.more)
        assertEquals((1..7).map { "p$it" }, seen)
    }

    @Test
    fun `each player sees only their own log`() = serverTest { client ->
        val a = client.register("Alpha")
        val b = client.register("Bravo")
        client.upload(a.token, entry("secret", 1, note = "just mine"))
        assertTrue(client.log(b.token).entries.isEmpty())
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/me/log").status)
    }

    @Test
    fun `a recovery code signs back in and revokes the old token`() = serverTest { client ->
        val me = client.register("Alpha")
        val code = assertNotNull(me.recoveryCode)
        client.upload(me.token, entry("a", 1))

        // Case, spaces and dashes don't matter when it's typed back in.
        val back = client.recover(code.lowercase().replace("-", " ")).body<RegisterResponse>()
        assertEquals(me.playerId, back.playerId)
        assertEquals(listOf("a"), client.log(back.token).entries.map { it.id })
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/players/me") { bearerAuth(me.token) }.status)
        assertEquals("Alpha", client.get("/v1/players/me") { bearerAuth(back.token) }.body<PlayerProfile>().displayName)

        assertEquals(HttpStatusCode.NotFound, client.recover("AAAAA-BBBBB-CCCCC-DDDDD-EEEEE").status)
    }

    @Test
    fun `a new recovery code replaces the old one`() = serverTest { client ->
        val me = client.register("Alpha")
        val fresh = client.post("/v1/players/me/recovery-code") { bearerAuth(me.token) }.body<RecoveryCodeResponse>().recoveryCode
        assertEquals(HttpStatusCode.NotFound, client.recover(me.recoveryCode!!).status)
        assertEquals(HttpStatusCode.OK, client.recover(fresh).status)
    }

    @Test
    fun `oversized uploads are refused`() = serverTest { client ->
        val me = client.register("Alpha")
        assertEquals(HttpStatusCode.BadRequest, client.upload(me.token, entry("a", 1, note = "x".repeat(2001))).status)
        assertEquals(HttpStatusCode.BadRequest, client.upload(me.token, *(0..500).map { entry("e$it", 1) }.toTypedArray()).status)
    }

    @Test
    fun `too many requests from one address are throttled`() = serverTest(ServerConfig(requestsPerMinute = 5)) { client ->
        val statuses = (1..7).map { client.get("/v1/charts/popular").status }
        assertEquals(HttpStatusCode.TooManyRequests, statuses.last())
        assertEquals(5, statuses.count { it == HttpStatusCode.OK })
    }
}
