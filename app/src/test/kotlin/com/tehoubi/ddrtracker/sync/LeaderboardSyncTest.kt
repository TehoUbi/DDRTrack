package com.tehoubi.ddrtracker.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.scoring.DdrScoring
import com.tehoubi.ddrtracker.core.songs.SongCatalog
import com.tehoubi.ddrtracker.data.OfflineFirstPlayRepository
import com.tehoubi.ddrtracker.data.local.DdrDatabase
import com.tehoubi.ddrtracker.data.local.LeaderboardStatus
import com.tehoubi.ddrtracker.data.remote.LeaderboardClient
import com.tehoubi.ddrtracker.data.settings.LeaderboardAccount
import com.tehoubi.ddrtracker.data.settings.SettingsRepository
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.model.PlaySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LeaderboardSyncTest {

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
    private val sync by lazy { LeaderboardSync(db, LeaderboardClient(), settings, clock) }

    @After fun tearDown() { server.shutdown(); db.close() }

    private fun play(id: String, consistent: Boolean = true): Play {
        val j = Judgments(marvelous = 400, perfect = 20, ok = 10)
        return Play(
            id = id, songTitle = "PARANOiA", style = PlayStyle.SINGLE, difficulty = Difficulty.EXPERT, level = 14,
            score = DdrScoring.moneyScore(j), exScore = DdrScoring.exScore(j) + if (consistent) 0 else 5,
            grade = Grade.AAA, lamp = ClearLamp.PERFECT_FULL_COMBO, judgments = j, maxCombo = 420,
            playedAt = Instant.parse("2026-05-12T03:00:00Z"), source = PlaySource.MANUAL,
        )
    }

    private suspend fun signIn() {
        val url = server.url("/").toString().trimEnd('/')
        settings.setServerUrl(url)
        settings.saveAccount(LeaderboardAccount(url, "me", "Teho", "tok"))
    }

    @Test
    fun `nothing happens without an account`() = runTest {
        repo.savePlay(play("a"))
        assertEquals(LeaderboardSync.Result.NOT_CONFIGURED, sync.run().result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `uploads pending plays and records the outcome`() = runTest {
        signIn()
        repo.savePlay(play("a"))
        repo.savePlay(play("b"))
        repo.savePlay(play("bad", consistent = false))
        server.enqueue(MockResponse().setBody("""{"accepted":["a"],"rejected":[{"clientId":"b","reason":"nope"}]}"""))

        val report = sync.run()
        assertEquals(LeaderboardSync.Result.DONE, report.result)
        assertEquals(1, report.uploaded)
        assertEquals(2, report.rejected)
        // The locally invalid play never went over the wire.
        val body = server.takeRequest().body.readUtf8()
        assertEquals(false, body.contains("\"bad\""))

        val dao = db.playDao()
        assertEquals(LeaderboardStatus.SUBMITTED, dao.getById("a")!!.leaderboardStatus)
        assertEquals("nope", dao.getById("b")!!.leaderboardError)
        assertEquals("EX score doesn't match the judgments", dao.getById("bad")!!.leaderboardError)
        assertEquals(0, dao.pendingUpload(10).size)
    }

    @Test
    fun `deleting a submitted play removes it from the server`() = runTest {
        signIn()
        repo.savePlay(play("a"))
        server.enqueue(MockResponse().setBody("""{"accepted":["a"],"rejected":[]}"""))
        sync.run()
        repo.deletePlay("a")
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(1, sync.run().removed)
        assertEquals("DELETE", server.let { it.takeRequest(); it.takeRequest() }.method)
        assertEquals(LeaderboardStatus.REMOVED, db.playDao().getById("a")!!.leaderboardStatus)
    }

    @Test
    fun `server trouble asks for a retry and keeps plays pending`() = runTest {
        signIn()
        repo.savePlay(play("a"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"down"}"""))
        assertEquals(LeaderboardSync.Result.RETRY, sync.run().result)
        assertEquals(1, db.playDao().pendingUpload(10).size)
    }

    @Test
    fun `an edit during upload is uploaded again`() = runTest {
        signIn()
        repo.savePlay(play("a"))
        server.enqueue(MockResponse().setBody("""{"accepted":["a"],"rejected":[]}""").setBodyDelay(0, java.util.concurrent.TimeUnit.MILLISECONDS))
        // Simulate the edit landing between read and status update by bumping updated_at first.
        val entity = db.playDao().getById("a")!!
        db.playDao().upsert(entity.copy(updatedAt = entity.updatedAt + 1))
        db.playDao().setLeaderboardStatus("a", entity.updatedAt, LeaderboardStatus.SUBMITTED, null)
        assertEquals(LeaderboardStatus.PENDING, db.playDao().getById("a")!!.leaderboardStatus)
    }
}
