package com.tehoubi.ddrtracker.server

import com.tehoubi.ddrtracker.core.api.ScoreSubmission
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.scoring.DdrScoring
import com.tehoubi.ddrtracker.core.songs.SongCatalog
import com.tehoubi.ddrtracker.server.db.Database
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant
import kotlin.random.Random

/**
 * `./gradlew :server:runDev [--args="--seed-demo"]`
 *
 * The real server on a throwaway embedded PostgreSQL. Data lives in
 * server/build/dev-postgres and is kept between runs. Point the app at
 * http://<this machine's LAN IP>:8080 (or http://10.0.2.2:8080 from an
 * emulator).
 */
fun main(args: Array<String>) {
    val dir = File("build/dev-postgres").apply { mkdirs() }
    val postgres = EmbeddedPostgres.builder().setDataDirectory(dir).setCleanDataDirectory(false).setPort(54329).start()
    val db = Database(postgres.postgresDatabase).also { it.migrate() }
    val services = Services(db, ServerConfig(registrationsPerHour = 1000))
    if ("--seed-demo" in args) runBlocking { seedDemo(services) }
    println("Leaderboard server on http://0.0.0.0:8080 (embedded PostgreSQL on :54329, data in ${dir.absolutePath})")
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") { module(services) }.start(wait = true)
}

/**
 * Demo rivals so the leaderboard and compare screens have something to
 * show. Every name is prefixed "demo-" so they are never mistaken for
 * real players.
 */
private suspend fun seedDemo(services: Services) {
    val rnd = Random(2026)
    val songs = SongCatalog.bundled().songs.shuffled(rnd).take(25)
    val skills = mapOf("demo-Kaito" to 0.97, "demo-Mika" to 0.93, "demo-Ren" to 0.88, "demo-Sora" to 0.82, "demo-Yuki" to 0.76)
    for ((name, skill) in skills) {
        val existing = services.players.search(name).firstOrNull { it.displayName == name }
        if (existing != null) continue
        val (player, _) = services.players.register(name)
        val plays = songs.mapIndexed { i, song ->
            val level = 9 + i % 8
            val notes = 280 + level * 22
            val difficulty = 1.0 - skill + (level - 9) * 0.015
            val miss = (rnd.nextDouble() * 18 * difficulty).toInt()
            val good = (rnd.nextDouble() * 8 * difficulty).toInt()
            val great = (rnd.nextDouble() * 60 * difficulty).toInt()
            val perfect = (notes * (0.08 + difficulty * 0.6)).toInt()
            val j = Judgments(notes - miss - good - great - perfect, perfect, great, good, 20, miss)
            ScoreSubmission(
                clientId = "seed-$i", songTitle = song.title, style = "SINGLE", difficulty = "EXPERT", level = level,
                score = DdrScoring.moneyScore(j), exScore = DdrScoring.exScore(j),
                marvelous = j.marvelous, perfect = j.perfect, great = j.great, good = j.good, ok = j.ok, miss = j.miss,
                maxCombo = notes - miss * 30, failed = false,
                playedAt = Instant.now().minusSeconds(rnd.nextLong(86_400L * 60)).toEpochMilli(),
            )
        }
        val result = services.scores.submit(player.playerId, plays)
        println("Seeded $name: ${result.accepted.size} plays (${result.rejected.size} rejected)")
    }
}
