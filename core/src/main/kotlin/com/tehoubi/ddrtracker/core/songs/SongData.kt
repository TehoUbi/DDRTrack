package com.tehoubi.ddrtracker.core.songs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads 3icecream.com's DDR song data, the source of the bundled song list
 * (tools/make_songlist.py) and of the app's weekly refresh, so songs
 * released after a build are still recognised.
 *
 * The file is JavaScript: `var ALL_SONG_DATA=[{...}, ...];` followed by
 * other declarations. Each song has its title as the game shows it,
 * optional romanized / alternate / searchable names ("/"-separated), nine
 * chart levels in [Song.levels] order, and an id that names its jacket image.
 */
object SongData {
    const val URL = "https://3icecream.com/js/songdata.js"

    /** The song's jacket: 120 px square (about 7 KB), or 452 px when [large] (about 55 KB). */
    fun jacketUrl(id: String, large: Boolean = false): String =
        "https://3icecream.com/img/banners/${if (large) "f/" else ""}$id.jpg"

    private val json = Json { ignoreUnknownKeys = true }

    /** The songs in [js], or an empty list if it isn't in the expected shape. */
    fun parse(js: String): List<Song> {
        val array = runCatching { json.parseToJsonElement(songArray(js) ?: return emptyList()).jsonArray }
            .getOrElse { return emptyList() }
        return array.mapNotNull { (it as? JsonObject)?.toSong() }
    }

    private fun JsonObject.toSong(): Song? {
        fun text(key: String) = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        val title = text("song_name")?.let(::clean)?.takeIf { it.isNotEmpty() } ?: return null
        val aliases = listOf("romanized_name", "alternate_name", "searchable_name")
            .flatMap { text(it).orEmpty().split('/') }
            .map(::clean)
            .filter { it.isNotEmpty() && it != title }
            .distinct()
        val levels = (this["ratings"] as? JsonArray)?.map { it.jsonPrimitive.intOrNull ?: 0 }.orEmpty()
        return Song(title, aliases = aliases, levels = levels, id = text("song_id")?.takeIf { it.isNotBlank() })
    }

    private fun clean(s: String) = s.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    /** The `[...]` assigned to ALL_SONG_DATA, found by matching brackets outside strings. */
    private fun songArray(js: String): String? {
        val start = js.indexOf('[', js.indexOf("ALL_SONG_DATA").takeIf { it >= 0 } ?: return null)
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until js.length) {
            val c = js[i]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '[' || c == '{' -> depth++
                c == ']' || c == '}' -> if (--depth == 0) return js.substring(start, i + 1)
            }
        }
        return null
    }
}
