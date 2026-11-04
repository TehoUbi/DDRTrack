package com.tehoubi.ddrtracker.core.songs

import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.PlayStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongDataTest {

    // The shape of https://3icecream.com/js/songdata.js, cut down.
    private val js = """var ALL_SONG_DATA=[{"song_id":"I6Dd","song_name":"向日葵サンセット","searchable_name":"ひまわりサンセット",""" +
        """"romanized_name":"Himawari Sunset","alternate_name":"Touhouhimawari","alphabet":"は","version_num":16,""" +
        """"ratings":[4,5,10,13,0,5,11,13,0],"tiers":[0.45,0.4,0.6,0.35,0,0.35,0.45,0.5,0]},""" +
        """{"song_id":"x","song_name":"A  \"Song\" ];  ","ratings":[1,2,3,4,5,6,7,8,9]},""" +
        """{"song_id":"y","song_name":"IX","alternate_name":"nine/ナイン","ratings":[5,11,14,17,18,10,14,17,18],"deleted":1}];""" +
        """const EVENT_EXCLUSIONS=[30,40];const SONG_DATA_LAST_UPDATED_unixms=1793343245477;"""

    @Test
    fun `reads titles, names to search by and levels`() {
        val songs = SongData.parse(js)
        assertEquals(listOf("向日葵サンセット", "A \"Song\" ];", "IX"), songs.map { it.title })
        val sunset = songs.first()
        assertEquals(listOf("Himawari Sunset", "Touhouhimawari", "ひまわりサンセット"), sunset.aliases)
        assertEquals(13, sunset.level(PlayStyle.SINGLE, Difficulty.EXPERT))
        assertEquals(listOf("nine", "ナイン"), songs.last().aliases)
    }

    @Test
    fun `anything else gives no songs rather than an error`() {
        assertTrue(SongData.parse("<html>Not found</html>").isEmpty())
        assertTrue(SongData.parse("var ALL_SONG_DATA=[{\"song_name\":").isEmpty())
    }
}
