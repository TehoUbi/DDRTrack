package com.tehoubi.ddrtracker.data

import androidx.room.withTransaction
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.songs.Song
import com.tehoubi.ddrtracker.core.songs.SongCatalog
import com.tehoubi.ddrtracker.data.local.DdrDatabase
import com.tehoubi.ddrtracker.data.local.UserSongEntity
import com.tehoubi.ddrtracker.data.local.toDomain
import com.tehoubi.ddrtracker.data.local.toEntity
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
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
) : PlayRepository {

    private val plays = db.playDao()
    private val songs = db.userSongDao()

    override fun observePlays(): Flow<List<Play>> =
        plays.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observePlay(id: String): Flow<Play?> = plays.observeById(id).map { it?.toDomain() }

    override fun observeChart(songKey: String, style: PlayStyle, difficulty: Difficulty): Flow<List<Play>> =
        plays.observeChart(songKey, style.name, difficulty.name).map { list -> list.map { it.toDomain() } }

    override fun observeUserSongs(): Flow<List<Song>> =
        songs.observeAll().map { list -> list.map { Song(it.title, it.artist) } }

    override suspend fun getPlay(id: String): Play? = plays.getById(id)?.takeUnless { it.deleted }?.toDomain()

    override suspend fun savePlay(play: Play): Result<Play> = runCatching {
        val now = clock.millis()
        db.withTransaction {
            plays.upsert(play.toEntity(updatedAt = now))
            if (bundledCatalog.find(play.songTitle) == null) {
                songs.upsert(UserSongEntity(play.songKey, play.songTitle, play.artist, now))
            }
        }
        play
    }

    override suspend fun deletePlay(id: String): Result<Unit> = runCatching {
        plays.markDeleted(id, clock.millis())
    }
}
