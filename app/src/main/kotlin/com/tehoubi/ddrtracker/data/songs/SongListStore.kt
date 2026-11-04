package com.tehoubi.ddrtracker.data.songs

import android.content.Context
import com.tehoubi.ddrtracker.core.songs.Song
import com.tehoubi.ddrtracker.core.songs.SongData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The downloaded song list: its songs, and when it was last checked for new ones. */
data class SongList(val songs: List<Song> = emptyList(), val checkedAt: Long? = null)

sealed interface SongListRefresh {
    data class Updated(val songs: Int) : SongListRefresh
    data object Unchanged : SongListRefresh
    data class Failed(val message: String) : SongListRefresh
}

/**
 * Keeps an up-to-date copy of the DDR song list (see SongData) in the app's
 * files, so songs released after this build are recognised. The bundled
 * list is the fallback until the first download, and if a download fails
 * or comes back in a shape the app doesn't understand, the last good copy
 * stays.
 *
 * Checks are conditional on the server's ETag, so an unchanged list costs
 * one small 304 response.
 */
@Singleton
class SongListStore @Inject constructor(@ApplicationContext context: Context) {

    internal var http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    internal var url: String = SongData.URL

    private val dir = File(context.filesDir, "songlist")
    private val dataFile = File(dir, "songdata.js")
    private val metaFile = File(dir, "songdata.properties")
    private val lock = Mutex()

    private val _list = MutableStateFlow(SongList())
    val list: StateFlow<SongList> = _list.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            lock.withLock { _list.value = SongList(load(), meta().getProperty(CHECKED_AT)?.toLongOrNull()) }
        }
    }

    suspend fun refresh(): SongListRefresh = withContext(Dispatchers.IO) {
        lock.withLock {
            val meta = meta()
            val request = Request.Builder().url(url).apply {
                // Only send the ETag if the copy it describes is still here.
                meta.getProperty(ETAG)?.takeIf { dataFile.exists() }?.let { header("If-None-Match", it) }
            }.build()
            val result = runCatching {
                http.newCall(request).execute().use { response ->
                    when {
                        response.code == 304 -> SongListRefresh.Unchanged
                        !response.isSuccessful -> SongListRefresh.Failed("HTTP ${response.code}")
                        else -> {
                            val body = response.body?.string().orEmpty()
                            val songs = SongData.parse(body)
                            // A real list has well over a thousand songs; anything less is an error page or a format change.
                            if (songs.size < MIN_SONGS) {
                                SongListRefresh.Failed("unexpected song list (${songs.size} songs)")
                            } else {
                                dir.mkdirs()
                                val tmp = File(dir, "songdata.js.tmp")
                                tmp.writeText(body)
                                tmp.renameTo(dataFile)
                                val etag = response.header("ETag")
                                if (etag != null) meta.setProperty(ETAG, etag) else meta.remove(ETAG)
                                _list.value = _list.value.copy(songs = songs)
                                SongListRefresh.Updated(songs.size)
                            }
                        }
                    }
                }
            }.getOrElse { SongListRefresh.Failed(it.message ?: it.javaClass.simpleName) }

            if (result !is SongListRefresh.Failed) {
                val now = System.currentTimeMillis()
                meta.setProperty(CHECKED_AT, now.toString())
                saveMeta(meta)
                _list.value = _list.value.copy(checkedAt = now)
            }
            result
        }
    }

    private fun load(): List<Song> =
        if (dataFile.exists()) runCatching { SongData.parse(dataFile.readText()) }.getOrDefault(emptyList()) else emptyList()

    private fun meta() = Properties().apply { if (metaFile.exists()) metaFile.inputStream().use { load(it) } }

    private fun saveMeta(meta: Properties) {
        dir.mkdirs()
        metaFile.outputStream().use { meta.store(it, null) }
    }

    private companion object {
        const val ETAG = "etag"
        const val CHECKED_AT = "checkedAt"
        const val MIN_SONGS = 1000
    }
}
