package com.tehoubi.ddrtracker.server

import com.tehoubi.ddrtracker.core.api.ChartSummary
import com.tehoubi.ddrtracker.core.api.Comparison
import com.tehoubi.ddrtracker.core.api.Leaderboard
import com.tehoubi.ddrtracker.core.api.PlayerProfile
import com.tehoubi.ddrtracker.core.api.RegisterResponse
import com.tehoubi.ddrtracker.core.api.ScoreSubmission
import com.tehoubi.ddrtracker.core.api.SubmitScoresRequest
import com.tehoubi.ddrtracker.core.api.SubmitScoresResponse
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.scoring.DdrScoring
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreRoutesTest {

    private var seq = 0

    /** A consistent play: [misses] Misses on a 500-object chart, everything else Marvelous. */
    private fun play(
        title: String = "PARANOiA",
        difficulty: String = "EXPERT",
        level: Int = 14,
        misses: Int = 0,
        perfects: Int = 0,
        clientId: String = "play-${seq++}",
        playedAt: Long = 1_777_000_000_000L + seq * 60_000L,
    ): ScoreSubmission {
        val j = Judgments(marvelous = 480 - misses - perfects, perfect = perfects, miss = misses, ok = 20)
        return ScoreSubmission(
            clientId = clientId, songTitle = title, style = "SINGLE", difficulty = difficulty, level = level,
            score = DdrScoring.moneyScore(j), exScore = DdrScoring.exScore(j),
            marvelous = j.marvelous, perfect = j.perfect, great = 0, good = 0, ok = j.ok, miss = j.miss,
            maxCombo = 100, failed = false, playedAt = playedAt,
        )
    }

    private suspend fun HttpClient.submit(token: String, vararg scores: ScoreSubmission): SubmitScoresResponse =
        post("/v1/scores") {
            bearerAuth(token); contentType(ContentType.Application.Json); setBody(SubmitScoresRequest(scores.toList()))
        }.body()

    private suspend fun HttpClient.board(token: String? = null, title: String = "paranoia"): Leaderboard =
        get("/v1/charts/$title/SINGLE/EXPERT/leaderboard") { token?.let { bearerAuth(it) } }.body()

    @Test
    fun `submissions are validated and idempotent`() = serverTest { client ->
        val me = client.register("Tehoubi")
        val good = play(clientId = "a")
        val bad = play(clientId = "b").copy(exScore = 1)
        val r = client.submit(me.token, good, bad)
        assertEquals(listOf("a"), r.accepted)
        assertEquals("EX score doesn't match the judgments", r.rejected.single().reason)

        // Retrying after a dropped response is harmless.
        assertEquals(listOf("a"), client.submit(me.token, good).accepted)
        assertEquals(1, client.get("/v1/players/me") { bearerAuth(me.token) }.body<PlayerProfile>().plays)
    }

    @Test
    fun `submitting needs a token`() = serverTest { client ->
        val r = client.post("/v1/scores") { contentType(ContentType.Application.Json); setBody(SubmitScoresRequest(listOf(play()))) }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `leaderboard ranks each player's best`() = serverTest { client ->
        val a = client.register("Alpha")
        val b = client.register("Bravo")
        val c = client.register("Charlie")
        client.submit(a.token, play(misses = 10), play(misses = 2)) // best: 2 misses
        client.submit(b.token, play(misses = 0))
        client.submit(c.token, play(misses = 30))

        val board = client.board()
        assertEquals("PARANOiA", board.songTitle)
        assertEquals(3, board.players)
        assertEquals(listOf("Bravo" to 1, "Alpha" to 2, "Charlie" to 3), board.entries.map { it.displayName to it.rank })
        assertEquals("AAA", board.entries.first().grade)
        assertEquals("MARVELOUS_FULL_COMBO", board.entries.first().lamp)
        assertNull(board.you)

        val asAlpha = client.board(a.token)
        assertEquals(2, asAlpha.you?.rank)
        assertEquals(0.5, asAlpha.youBeat)
    }

    @Test
    fun `ties share a rank and your row shows outside the top`() = serverTest { client ->
        val players = (1..4).map { client.register("Player$it") }
        players.forEachIndexed { i, p -> client.submit(p.token, play(misses = if (i < 2) 1 else 10 + i)) }
        val board = client.get("/v1/charts/paranoia/SINGLE/EXPERT/leaderboard?limit=2") { bearerAuth(players[3].token) }.body<Leaderboard>()
        assertEquals(listOf(1, 1), board.entries.map { it.rank })
        assertEquals(4, board.you?.rank)
        assertEquals(0.0, board.youBeat)
    }

    @Test
    fun `unknown chart is 404`() = serverTest { client ->
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/charts/nope/SINGLE/EXPERT/leaderboard").status)
    }

    @Test
    fun `compare covers shared charts only`() = serverTest { client ->
        val a = client.register("Alpha")
        val b = client.register("Bravo")
        client.submit(a.token, play(title = "PARANOiA", misses = 0), play(title = "MAX 300", level = 15, misses = 5), play(title = "B4U", level = 12))
        client.submit(b.token, play(title = "PARANOiA", misses = 3), play(title = "MAX 300", level = 15, misses = 1), play(title = "Xepher", level = 16))

        val cmp = client.get("/v1/players/me/compare/${b.playerId}") { bearerAuth(a.token) }.body<Comparison>()
        assertEquals("Alpha", cmp.player.displayName)
        assertEquals(1, cmp.wins)
        assertEquals(1, cmp.losses)
        assertEquals(listOf("MAX 300", "PARANOiA"), cmp.charts.map { it.songTitle })
        assertTrue(cmp.charts.first().delta < 0)

        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/players/me/compare/${b.playerId}").status)
    }

    @Test
    fun `deleting a play removes it from the board`() = serverTest { client ->
        val a = client.register("Alpha")
        client.submit(a.token, play(clientId = "x"))
        assertEquals(HttpStatusCode.NoContent, client.delete("/v1/scores/x") { bearerAuth(a.token) }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/v1/scores/x") { bearerAuth(a.token) }.status)
        assertEquals(0, client.board().players)
    }

    @Test
    fun `popular charts by player count`() = serverTest { client ->
        val a = client.register("Alpha")
        val b = client.register("Bravo")
        client.submit(a.token, play(title = "PARANOiA"), play(title = "PARANOiA"), play(title = "MAX 300"))
        client.submit(b.token, play(title = "PARANOiA"))
        val popular = client.get("/v1/charts/popular").body<List<ChartSummary>>()
        assertEquals(listOf("PARANOiA" to 2, "MAX 300" to 1), popular.map { it.songTitle to it.players })
        assertEquals(3, popular.first().plays)
    }

    @Test
    fun `oversized batches are refused`() = serverTest { client ->
        val a: RegisterResponse = client.register("Alpha")
        val r = client.post("/v1/scores") {
            bearerAuth(a.token); contentType(ContentType.Application.Json)
            setBody(SubmitScoresRequest(List(501) { play() }))
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }
}
