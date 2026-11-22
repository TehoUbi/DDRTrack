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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LeaderboardSyncTest {

    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer().apply { start() }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DdrDatabase::class.java)
        .allowMainThreadQueries().build()
    private val clock = MovableClock(Instant.parse("2026-05-13T04:00:00Z"))
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
        var posts = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (++posts == 1) {
                    // The player fixes the max combo while the first upload is in flight.
                    clock.advance(Duration.ofSeconds(1))
                    runBlocking { repo.savePlay(play("a").copy(maxCombo = 400)) }
                }
                return MockResponse().setBody("""{"accepted":["a"],"rejected":[]}""")
            }
        }

        // The server accepted the old version, but the row changed under the
        // upload, so it stays pending instead of being marked submitted.
        assertEquals(1, sync.run().uploaded)
        assertEquals(LeaderboardStatus.PENDING, db.playDao().getById("a")!!.leaderboardStatus)

        // The next run sends the edited play, and only then is it submitted.
        assertEquals(1, sync.run().uploaded)
        assertEquals(2, posts)
        server.takeRequest()
        assertTrue(server.takeRequest().body.readUtf8().contains("\"maxCombo\":400"))
        assertEquals(LeaderboardStatus.SUBMITTED, db.playDao().getById("a")!!.leaderboardStatus)
    }

    /** A clock the test can move forward, so an edit gets a later updated_at than the upload read. */
    private class MovableClock(private var now: Instant) : Clock() {
        fun advance(by: Duration) { now = now.plus(by) }
        override fun instant(): Instant = now
        override fun millis(): Long = now.toEpochMilli()
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }
}
