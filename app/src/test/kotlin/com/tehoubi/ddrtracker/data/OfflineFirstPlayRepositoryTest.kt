package com.tehoubi.ddrtracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.songs.SongCatalog
import com.tehoubi.ddrtracker.data.local.DdrDatabase
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.model.PlaySource
import com.tehoubi.ddrtracker.sync.SyncTrigger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineFirstPlayRepositoryTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), DdrDatabase::class.java,
    ).allowMainThreadQueries().build()

    private val clock = Clock.fixed(Instant.parse("2026-04-22T04:00:00Z"), ZoneOffset.UTC)
    private var syncRequests = 0
    private val repo = OfflineFirstPlayRepository(db, SongCatalog.bundled(), clock, object : SyncTrigger {
        override fun playsChanged() { syncRequests++ }
    })

    @After fun close() = db.close()

    private fun play(id: String, title: String = "PARANOiA", score: Int = 951_230, at: String = "2026-04-20T03:00:00Z") = Play(
        id = id, songTitle = title, style = PlayStyle.SINGLE, difficulty = Difficulty.EXPERT, level = 14,
        score = score, exScore = 1200, grade = Grade.AA_PLUS, lamp = ClearLamp.CLEAR,
        judgments = Judgments(300, 80, 10, 2, 30, 3), maxCombo = 200, playedAt = Instant.parse(at),
        source = PlaySource.MANUAL,
    )

    @Test
    fun `saved plays round-trip and list newest first`() = runTest {
        repo.savePlay(play("a", at = "2026-04-20T03:00:00Z"))
        repo.savePlay(play("b", at = "2026-04-21T03:00:00Z"))
        val all = repo.observePlays().first()
        assertEquals(listOf("b", "a"), all.map { it.id })
        assertEquals(play("a").copy(sessionId = "a"), repo.getPlay("a"))
        assertEquals(2, syncRequests)
    }

    @Test
    fun `chart history is chronological and per chart`() = runTest {
        repo.savePlay(play("new", at = "2026-04-21T03:00:00Z"))
        repo.savePlay(play("old", at = "2026-04-19T03:00:00Z"))
        repo.savePlay(play("other", title = "MAX 300"))
        val chart = repo.observeChart("paranoia", PlayStyle.SINGLE, Difficulty.EXPERT).first()
        assertEquals(listOf("old", "new"), chart.map { it.id })
    }

    @Test
    fun `deleting hides the play but keeps a tombstone`() = runTest {
        repo.savePlay(play("a"))
        repo.deletePlay("a")
        assertNull(repo.getPlay("a"))
        assertEquals(emptyList(), repo.observePlays().first())
        assertEquals(true, db.playDao().getById("a")?.deleted)
    }

    @Test
    fun `plays are grouped into sessions`() = runTest {
        repo.savePlay(play("a", at = "2026-04-20T03:00:00Z"))
        repo.savePlay(play("b", at = "2026-04-20T03:10:00Z"))
        repo.savePlay(play("c", at = "2026-04-21T03:00:00Z"))
        val sessions = repo.observeSessions().first()
        assertEquals(listOf("c", "a"), sessions.map { it.id })
        assertEquals(2, sessions.last().playCount)
        assertEquals("a", repo.getPlay("b")?.sessionId)

        // Back-dating a play before "a" makes it the session's first play.
        repo.savePlay(play("z", at = "2026-04-20T02:50:00Z"))
        assertEquals("z", repo.getPlay("b")?.sessionId)
        assertEquals(listOf("c", "z"), repo.observeSessions().first().map { it.id })

        repo.deletePlay("c")
        assertEquals(listOf("z"), repo.observeSessions().first().map { it.id })
    }

    @Test
    fun `unknown titles are remembered as user songs`() = runTest {
        repo.savePlay(play("a", title = "PARANOiA"))
        repo.savePlay(play("b", title = "Some Custom Song"))
        assertEquals(listOf("Some Custom Song"), repo.observeUserSongs().first().map { it.title })
    }
}
