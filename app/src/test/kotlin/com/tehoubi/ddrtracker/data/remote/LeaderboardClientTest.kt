package com.tehoubi.ddrtracker.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeaderboardClientTest {

    private val server = MockWebServer().apply { start() }
    private val base = server.url("/").toString()
    private val client = LeaderboardClient()

    @After fun stop() = server.shutdown()

    @Test
    fun `register posts json and parses the reply`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"playerId":"p","displayName":"Teho","token":"t"}"""))
        val r = client.register(base, "Teho")
        assertEquals("t", r.token)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/players", req.path)
        assertEquals("""{"displayName":"Teho"}""", req.body.readUtf8())
    }

    @Test
    fun `token goes in the authorization header`() = runTest {
        server.enqueue(MockResponse().setBody("""{"playerId":"p","displayName":"T","createdAt":1,"plays":0,"charts":0,"aaaCharts":0,"fullComboCharts":0,"averageBest":null,"lastPlayedAt":null}"""))
        client.me(base, "secret")
        assertEquals("Bearer secret", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `errors carry the server's message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"Display name \"Teho\" is taken"}"""))
        val e = assertFailsWith<ApiException> { client.register(base, "Teho") }
        assertEquals(409, e.status)
        assertEquals("Display name \"Teho\" is taken", e.message)
    }

    @Test
    fun `delete maps 404 to false`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"nope"}"""))
        assertFalse(client.deleteScore(base, "t", "abc"))
    }

    @Test
    fun `path segments are encoded`() = runTest {
        server.enqueue(MockResponse().setBody("""{"songKey":"u1","songTitle":"x","style":"SINGLE","difficulty":"EXPERT","level":9,"players":0,"entries":[]}"""))
        val board = client.leaderboard(base, null, "a b", "SINGLE", "EXPERT")
        assertNull(board.you)
        assertEquals("/v1/charts/a%20b/SINGLE/EXPERT/leaderboard?limit=50", server.takeRequest().path)
    }

    @Test
    fun `base urls are normalised`() {
        assertEquals("http://192.168.1.5:8080", LeaderboardClient.normalizeBaseUrl(" 192.168.1.5:8080/ "))
        assertEquals("https://ddr.example.com/api", LeaderboardClient.normalizeBaseUrl("https://ddr.example.com/api/"))
        assertNull(LeaderboardClient.normalizeBaseUrl(""))
        assertEquals("http://h:1/v1/x", LeaderboardClient.resolve("h:1/", "v1/x").toString())
    }

    @Test
    fun `plain http is only for private networks`() {
        listOf(
            "http://10.0.2.2:8080", "http://192.168.1.20:8080", "http://172.20.0.5", "http://127.0.0.1:8080",
            "http://localhost:8080", "http://100.101.102.103", "http://nas.local", "http://laptop:8080",
            "http://box.tail1234.ts.net", "http://[::1]:8080", "https://ddr.example.com",
        ).forEach { assertTrue(LeaderboardClient.isSafeToUse(it), it) }
        listOf("http://ddr.example.com", "http://203.0.113.7:8080", "http://172.32.0.1", "http://[2001:db8::1]")
            .forEach { assertFalse(LeaderboardClient.isSafeToUse(it), it) }
    }

    @Test
    fun `the address a server listens on gets a hint instead of an https warning`() {
        assertTrue(LeaderboardClient.unreachableReason("http://0.0.0.0:8080")!!.contains("10.0.2.2"))
        assertTrue(LeaderboardClient.unreachableReason("http://[::]:8080") != null)
        assertEquals(null, LeaderboardClient.unreachableReason("http://10.0.2.2:8080"))
    }
}
