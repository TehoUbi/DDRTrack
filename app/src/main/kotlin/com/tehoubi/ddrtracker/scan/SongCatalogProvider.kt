package com.tehoubi.ddrtracker.scan

import com.tehoubi.ddrtracker.core.songs.SongCatalog
import com.tehoubi.ddrtracker.data.songs.SongListStore
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every song the scanner knows: the downloaded list (newest, so its
 * spellings and levels win), the list bundled with this build, and every
 * title the player has saved.
 */
@Singleton
class SongCatalogProvider @Inject constructor(
    private val bundled: SongCatalog,
    private val plays: PlayRepository,
    private val downloaded: SongListStore,
) {
    val catalog: Flow<SongCatalog> = combine(downloaded.list, plays.observeUserSongs()) { list, user ->
        SongCatalog(list.songs + bundled.songs + user)
    }
}
