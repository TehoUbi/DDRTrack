package com.tehoubi.ddrtracker.scan

import com.tehoubi.ddrtracker.core.songs.SongCatalog
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The bundled catalog plus every title the player has saved. */
@Singleton
class SongCatalogProvider @Inject constructor(
    private val bundled: SongCatalog,
    private val plays: PlayRepository,
) {
    val catalog: Flow<SongCatalog> = plays.observeUserSongs().map { bundled + it }
}
