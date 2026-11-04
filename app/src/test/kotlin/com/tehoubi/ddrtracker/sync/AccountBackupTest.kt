package com.tehoubi.ddrtracker.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tehoubi.ddrtracker.core.api.LogEntry
import com.tehoubi.ddrtracker.core.api.LogPage
import com.tehoubi.ddrtracker.core.api.LogUpload
import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.scoring.DdrScoring
import com.tehoubi.ddrtracker.core.songs.SongCatalog
import com.tehoubi.ddrtracker.data.OfflineFirstPlayRepository
import com.tehoubi.ddrtracker.data.local.DdrDatabase
import com.tehoubi.ddrtracker.data.remote.LeaderboardClient
import com.tehoubi.ddrtracker.data.settings.LeaderboardAccount
import com.tehoubi.ddrtracker.data.settings.SettingsRepository
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.model.PlaySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountBackupTest {

    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer().apply { start() }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DdrDatabase::class.java)
        .allowMainThreadQueries().build()
    private val clock = Clock.fixed(Instant.parse("2026-05-13T04:00:00Z"), ZoneOffset.UTC)
    private val settings by lazy {
        SettingsRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
                File(tmp.root, "settings.preferences_pb")
            }
        )
    }
    private val repo = OfflineFirstPlayRepository(db, SongCatalog.bundled(), clock, object : SyncTrigger {
        override fun playsChanged() = Unit
    })
    private val backup by lazy { AccountBackup(db, LeaderboardClient(), repo, settings, clock) }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @After fun tearDown() { server.shutdown(); db.close() }

    private fun play(id: String, consistent: Boolean = true, note: String? = null): Play {
        val j = Judgments(marvelous = 400, perfect = 20, ok = 10)
        return Play(
            id = id, songTitle = "ブタサンダー", style = PlayStyle.SINGLE, difficulty = Difficulty.EXPERT, level = 13,
            score = DdrScoring.moneyScore(j), exScore = DdrScoring.exScore(j) + if (consistent) 0 else 5,
            grade = Grade.AAA, lamp = ClearLamp.PERFECT_FULL_COMBO, judgments = j, maxCombo = 420, fast = 12, slow = 8,
            playedAt = Instant.parse("2026-05-12T03:00:00Z"), source = PlaySource.CAMERA, note = note,
        )
    }

    private suspend fun signIn() {
        val url = server.url("/").toString().trimEnd('/')
        settings.setServerUrl(url)
        settings.saveAccount(LeaderboardAccount(url, "me", "Teho", "tok"))
    }

    private fun page(entries: List<LogEntry>, revision: Long, more: Boolean = false) =
        MockResponse().setBody(json.encodeToString(LogPage.serializer(), LogPage(entries, revision, more)))

    @Test
    fun `nothing happens without an account`() = runTest {
        repo.savePlay(play("a"))
        assertEquals(AccountBackup.Result.NOT_CONFIGURED, backup.run())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `backs up every play, even ones the leaderboard would reject`() = runTest {
        signIn()
        repo.savePlay(play("a", note = "new shoes"))
        repo.savePlay(play("bad", consistent = false))
        server.enqueue(MockResponse().setBody("""{"stored":2}"""))
        server.enqueue(page(emptyList(), revision = 7))

        assertEquals(AccountBackup.Result.DONE, backup.run())
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("Bearer tok", put.getHeader("Authorization"))
        val sent = json.decodeFromString(LogUpload.serializer(), put.body.readUtf8()).entries.associateBy { it.id }
        assertEquals(setOf("a", "bad"), sent.keys)
        assertEquals("new shoes", sent.getValue("a").note)
        assertEquals(12, sent.getValue("a").fast)
        assertEquals("/v1/me/log?after=0", server.takeRequest().path)
        assertEquals(7, settings.current().backupRevision)

        // Nothing changed since: only the pull runs, from where the last one stopped.
        server.enqueue(page(emptyList(), revision = 7))
        backup.run()
        assertEquals("/v1/me/log?after=7", server.takeRequest().path)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `restores a log onto a fresh phone, page by page`() = runTest {
        signIn()
        val remote = (1..3).map { i ->
            LogEntry(
                id = "p$i", songTitle = "ブタサンダー", style = "SINGLE", difficulty = "EXPERT", level = 13,
                score = 999_910, exScore = 1359, grade = "AAA", lamp = "PERFECT_FULL_COMBO",
                marvelous = 434, perfect = 9, great = 0, good = 0, ok = 13, miss = 0, maxCombo = 443,
                fast = 2, slow = 7, failed = false, playedAt = 1_778_000_000_000L + i, source = "CAMERA",
                note = "from my old phone", updatedAt = 1_778_000_000_000L + i,
            )
        }
        server.enqueue(page(remote.take(2), revision = 2, more = true))
        server.enqueue(page(remote.drop(2), revision = 3))

        assertEquals(AccountBackup.Result.DONE, backup.run())
        assertEquals(listOf("/v1/me/log?after=0", "/v1/me/log?after=2"), listOf(server.takeRequest().path, server.takeRequest().path))
        val restored = (1..3).map { repo.getPlay("p$it")!! }
        assertTrue(restored.all { it.note == "from my old phone" && it.fast == 2 })
        // Restored plays aren't sent straight back.
        assertEquals(0, db.playDao().backupPending(10).size)
    }

    @Test
    fun `a deletion from another phone removes the play here`() = runTest {
        signIn()
        repo.savePlay(play("a"))
        val local = db.playDao().getById("a")!!
        server.enqueue(MockResponse().setBody("""{"stored":1}"""))
        val tombstone = local.toLogEntry().copy(deleted = true, updatedAt = local.updatedAt + 1_000)
        server.enqueue(page(listOf(tombstone), revision = 9))
        backup.run()
        assertNull(repo.getPlay("a"))
    }

    @Test
    fun `server trouble keeps everything queued for the next try`() = runTest {
        signIn()
        repo.savePlay(play("a"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"down"}"""))
        assertEquals(AccountBackup.Result.RETRY, backup.run())
        assertEquals(1, db.playDao().backupPending(10).size)
        assertTrue(backup.status.value is BackupStatus.Failed)
    }
}
