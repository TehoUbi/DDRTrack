package com.tehoubi.ddrtracker.data

import androidx.room.withTransaction
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.songs.Song
import com.tehoubi.ddrtracker.core.songs.SongCatalog
import com.tehoubi.ddrtracker.core.stats.SyncMerge
import com.tehoubi.ddrtracker.data.local.DdrDatabase
import com.tehoubi.ddrtracker.data.local.PlayEntity
import com.tehoubi.ddrtracker.data.local.UserSongEntity
import com.tehoubi.ddrtracker.data.local.toDomain
import com.tehoubi.ddrtracker.data.local.toEntity
import com.tehoubi.ddrtracker.core.stats.Sessions
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.model.PlaySession
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
import com.tehoubi.ddrtracker.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Room is the source of truth; everything else syncs from it. */
@Singleton
class OfflineFirstPlayRepository @Inject constructor(
    private val db: DdrDatabase,
    private val bundledCatalog: SongCatalog,
    private val clock: Clock,
    private val sync: SyncTrigger,
) : PlayRepository {

    private val plays = db.playDao()
    private val songs = db.userSongDao()
    private val sessions = db.sessionDao()

    override fun observePlays(): Flow<List<Play>> =
        plays.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observePlay(id: String): Flow<Play?> = plays.observeById(id).map { it?.toDomain() }

    override fun observeChart(songKey: String, style: PlayStyle, difficulty: Difficulty): Flow<List<Play>> =
        plays.observeChart(songKey, style.name, difficulty.name).map { list -> list.map { it.toDomain() } }

    override fun observeSessions(): Flow<List<PlaySession>> =
        sessions.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeUserSongs(): Flow<List<Song>> =
        songs.observeAll().map { list -> list.map { Song(it.title, it.artist) } }

    override suspend fun getPlay(id: String): Play? = plays.getById(id)?.takeUnless { it.deleted }?.toDomain()

    override suspend fun savePlay(play: Play): Result<Play> = runCatching {
        val now = clock.millis()
        db.withTransaction {
            plays.upsert(play.toEntity(updatedAt = now))
            rememberSong(play, now)
            rebuildSessions()
        }
        sync.playsChanged()
        play
    }

    override suspend fun savePlays(plays: List<Play>): Result<Int> = runCatching {
        val now = clock.millis()
        db.withTransaction {
            this.plays.upsertAll(plays.map { it.toEntity(updatedAt = now) })
            plays.distinctBy { it.songKey }.forEach { rememberSong(it, now) }
            rebuildSessions()
        }
        sync.playsChanged()
        plays.size
    }

    override suspend fun deletePlay(id: String): Result<Unit> = runCatching {
        db.withTransaction {
            plays.markDeleted(id, clock.millis())
            rebuildSessions()
        }
        sync.playsChanged()
    }

    /**
     * Applies plays pulled from the cloud. A remote row wins only if it is
     * newer than the local one; applied rows are already in the cloud, so
     * they are not marked dirty again. Returns how many were applied.
     */
    suspend fun mergeFromCloud(remote: List<PlayEntity>): Int {
        if (remote.isEmpty()) return 0
        var applied = 0
        db.withTransaction {
            val local = remote.chunked(500).flatMap { chunk -> plays.versions(chunk.map { it.id }) }.associate { it.id to it.updatedAt }
            val winners = remote.filter { SyncMerge.remoteWins(local[it.id], it.updatedAt) }
            plays.upsertAll(winners.map { it.copy(cloudDirty = false, sessionId = null) })
            winners.filter { !it.deleted }.distinctBy { it.songKey }.forEach { rememberSong(it.toDomain(), clock.millis()) }
            applied = winners.size
            if (applied > 0) rebuildSessions()
        }
        if (applied > 0) sync.playsChanged()
        return applied
    }

    private suspend fun rememberSong(play: Play, now: Long) {
        if (bundledCatalog.find(play.songTitle) == null) {
            songs.upsert(UserSongEntity(play.songKey, play.songTitle, play.artist, now))
        }
    }

    /**
     * Regroups every play into sessions. Cheap enough to do on each write
     * (a few thousand plays group in milliseconds), and it keeps sessions
     * right when a play is back-dated, edited or deleted.
     */
    private suspend fun rebuildSessions() {
        val all = plays.getAllChronological()
        val grouped = Sessions.group(all.map { it.toDomain() })
        val assignment = grouped.flatMap { s -> s.plays.map { it.id to s.id } }.toMap()
        all.forEach { entity ->
            val sessionId = assignment.getValue(entity.id)
            if (entity.sessionId != sessionId) plays.setSession(entity.id, sessionId)
        }
        sessions.clear()
        sessions.upsertAll(grouped.map { it.toEntity() })
    }
}
