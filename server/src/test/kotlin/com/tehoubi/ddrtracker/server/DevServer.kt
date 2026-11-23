package com.tehoubi.ddrtracker.server

import com.tehoubi.ddrtracker.core.api.ScoreSubmission
import com.tehoubi.ddrtracker.core.api.SubmissionRules
import com.tehoubi.ddrtracker.server.db.Database
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

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
    println("Leaderboard server listening on port 8080 (embedded PostgreSQL on :54329, data in ${dir.absolutePath})")
    println("  In the app, use http://10.0.2.2:8080 from an emulator, or http://<this computer's Wi-Fi address>:8080 from a phone.")
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") { module(services) }.start(wait = true)
}

@Serializable
private data class DemoPlayer(val name: String, val plays: List<ScoreSubmission>)

@Serializable
private data class DemoPlayers(val players: List<DemoPlayer>)

/**
 * Demo rivals from samples/demo-players.json (tools/make_samples.py), so the
 * leaderboard and compare screens have something to show. They have bests
 * on many of the charts in samples/sample-backup.json, so restoring that
 * backup in the app and signing up gives real head-to-heads. Players
 * already on the server are left alone; ones seeded under the old "demo-"
 * names are renamed, keeping everything else on the server as it is.
 */
private suspend fun seedDemo(services: Services) {
    val file = File("../samples/demo-players.json")
    require(file.exists()) { "${file.canonicalPath} is missing; run python3 tools/make_samples.py" }
    val demo = Json { ignoreUnknownKeys = true }.decodeFromString(DemoPlayers.serializer(), file.readText())
    for ((name, plays) in demo.players) {
        if (services.players.search(name).any { it.displayName == name }) {
            println("$name is already here")
            continue
        }
        val old = services.players.search("demo-$name").firstOrNull { it.displayName == "demo-$name" }
        if (old != null) {
            services.players.rename(old.playerId, name)
            println("Renamed demo-$name to $name")
            continue
        }
        val (player, _) = services.players.register(name)
        val accepted = plays.chunked(SubmissionRules.MAX_BATCH).sumOf { services.scores.submit(player.playerId, it).accepted.size }
        println("Seeded $name: $accepted of ${plays.size} plays")
    }
}
