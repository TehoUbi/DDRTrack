package com.tehoubi.ddrtracker.data.songs

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class SongListStoreTest {

    private val server = MockWebServer().apply { start() }

    @After fun stop() = server.shutdown()

    private fun store() = SongListStore(ApplicationProvider.getApplicationContext()).apply {
        url = server.url("/js/songdata.js").toString()
    }

    private fun songData(count: Int) = "var ALL_SONG_DATA=[" +
        (1..count).joinToString(",") { """{"song_name":"Song $it","ratings":[1,2,3,4,0,2,3,4,0]}""" } +
        "];const EVENT_EXCLUSIONS=[];"

    @Test
    fun `downloads the list and then asks only whether it changed`() = runTest {
        server.enqueue(MockResponse().setBody(songData(1200)).setHeader("ETag", "W/\"v1\""))
        val store = store()
        assertEquals(SongListRefresh.Updated(1200), store.refresh())
        assertEquals(1200, store.list.value.songs.size)
        assertNotNull(store.list.value.checkedAt)
        assertNull(server.takeRequest().getHeader("If-None-Match"))

        server.enqueue(MockResponse().setResponseCode(304))
        assertEquals(SongListRefresh.Unchanged, store.refresh())
        assertEquals("W/\"v1\"", server.takeRequest().getHeader("If-None-Match"))
        assertEquals(1200, store.list.value.songs.size)

        // The next app start loads the saved copy without a download.
        val reopened = store().list.first { it.songs.isNotEmpty() }
        assertEquals(1200, reopened.songs.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `an error page or a changed format keeps the last good list`() = runTest {
        server.enqueue(MockResponse().setBody(songData(1200)).setHeader("ETag", "a"))
        val store = store()
        store.refresh()

        server.enqueue(MockResponse().setBody("<html>Maintenance</html>"))
        assertIs<SongListRefresh.Failed>(store.refresh())
        server.enqueue(MockResponse().setResponseCode(500))
        assertIs<SongListRefresh.Failed>(store.refresh())
        assertEquals(1200, store.list.value.songs.size)
    }
}
