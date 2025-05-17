package com.tehoubi.ddrtracker.core.songs

import com.tehoubi.ddrtracker.core.ocr.TextMatching
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

data class Song(val title: String, val artist: String? = null) {
    val key: String = SongKey.of(title)
}

data class SongMatch(val song: Song, val similarity: Float, val matchedText: String)

/**
 * Fuzzy title lookup for OCR output.
 *
 * Titles are compared on a key that ignores case, punctuation and spacing and
 * folds characters OCR mixes up (0/O, 1/l/I, 5/S, 8/B) to one form, scored by
 * the better of edit-distance similarity and trigram overlap: edit distance
 * handles a misread letter, trigrams handle a dropped or extra word.
 */
class SongCatalog(songs: Collection<Song>) {

    private val entries: List<Pair<Song, String>> =
        songs.distinctBy { it.key }.map { it to matchKey(it.title) }.filter { it.second.isNotEmpty() }

    val songs: List<Song> get() = entries.map { it.first }
    val size: Int get() = entries.size

    operator fun plus(more: Collection<Song>) = SongCatalog(songs + more)

    fun find(title: String): Song? = entries.firstOrNull { it.first.key == SongKey.of(title) }?.first

    /**
     * The best match for any of [texts] (candidate title lines, most likely
     * first), or null if nothing reaches [threshold].
     */
    fun bestMatch(texts: List<String>, threshold: Float = DEFAULT_THRESHOLD): SongMatch? {
        var best: SongMatch? = null
        for (text in texts) {
            val key = matchKey(text)
            if (key.length < 2) continue
            for ((song, songKey) in entries) {
                val sim = similarity(key, songKey)
                if (sim >= threshold && (best == null || sim > best.similarity)) {
                    best = SongMatch(song, sim, text)
                    if (sim == 1f) return best
                }
            }
        }
        return best
    }

    /** Titles starting with or containing [query], for the manual-entry field. */
    fun suggest(query: String, limit: Int = 8): List<Song> {
        val key = matchKey(query)
        if (key.isEmpty()) return emptyList()
        return entries
            .map { (song, songKey) ->
                val rank = when {
                    songKey.startsWith(key) -> 2f
                    songKey.contains(key) -> 1f
                    else -> similarity(key, songKey) - 0.5f
                }
                song to rank
            }
            .filter { it.second > 0.2f }
            .sortedWith(compareByDescending<Pair<Song, Float>> { it.second }.thenBy { it.first.title.length })
            .take(limit)
            .map { it.first }
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.74f

        /** The catalog shipped in this module's resources. */
        fun bundled(): SongCatalog {
            val stream = SongCatalog::class.java.getResourceAsStream("songs.txt")
                ?: error("songs.txt missing from the classpath")
            val songs = stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { Song(it) }.toList()
            }
            return SongCatalog(songs)
        }

        internal fun matchKey(raw: String): String {
            val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase()
            return buildString {
                for (c in nfkc) {
                    when (c) {
                        '0' -> append('o')
                        '1', 'l', '|', '!' -> append('i')
                        '5' -> append('s')
                        '8' -> append('b')
                        else -> if (c.isLetterOrDigit()) append(c)
                    }
                }
            }
        }

        internal fun similarity(a: String, b: String): Float {
            if (a == b) return 1f
            val edit = 1f - TextMatching.levenshtein(a, b) / max(a.length, b.length).toFloat()
            return max(edit, dice(a, b))
        }

        private fun trigrams(s: String): List<String> {
            val padded = "  $s "
            return (0..padded.length - 3).map { padded.substring(it, it + 3) }
        }

        private fun dice(a: String, b: String): Float {
            val ta = trigrams(a).groupingBy { it }.eachCount()
            val tb = trigrams(b).groupingBy { it }.eachCount()
            val shared = ta.entries.sumOf { (g, n) -> min(n, tb[g] ?: 0) }
            return 2f * shared / (ta.values.sum() + tb.values.sum())
        }
    }
}
