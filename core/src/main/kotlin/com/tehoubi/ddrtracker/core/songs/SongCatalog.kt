package com.tehoubi.ddrtracker.core.songs

import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.core.ocr.TextMatching
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

data class Song(
    val title: String,
    val artist: String? = null,
    /** Romanized and alternate names, for finding the song by typing. */
    val aliases: List<String> = emptyList(),
    /**
     * Chart levels in the order SP Beginner, Basic, Difficult, Expert,
     * Challenge, DP Basic, Difficult, Expert, Challenge; 0 where the chart
     * doesn't exist. Empty when unknown.
     */
    val levels: List<Int> = emptyList(),
) {
    val key: String = SongKey.of(title)

    /** The chart's level, or null if unknown or the song has no such chart. */
    fun level(style: PlayStyle, difficulty: Difficulty): Int? {
        val index = when (style) {
            PlayStyle.SINGLE -> difficulty.ordinal
            PlayStyle.DOUBLE -> if (difficulty == Difficulty.BEGINNER) return null else 4 + difficulty.ordinal
        }
        return levels.getOrNull(index)?.takeIf { it > 0 }
    }
}

data class SongMatch(val song: Song, val similarity: Float, val matchedText: String)

/** What was read of the chart, to tell apart songs with similar titles. */
data class ChartHint(val style: PlayStyle?, val difficulty: Difficulty, val level: Int)

/**
 * Fuzzy title lookup for OCR output.
 *
 * Titles are compared on a key that ignores case, punctuation and spacing and
 * folds characters OCR mixes up (0/O, 1/l/I, 5/S, 8/B) to one form, scored by
 * the best of edit-distance similarity, trigram overlap and common
 * subsequence: edit distance handles a misread letter, trigrams a dropped or
 * extra word, and the subsequence a few dropped characters, which is how
 * small kana and kanji usually fail.
 */
class SongCatalog(songs: Collection<Song>) {

    private val entries: List<Pair<Song, String>> =
        songs.distinctBy { it.key }.map { it to matchKey(it.title) }.filter { it.second.isNotEmpty() }
    private val aliasKeys: Map<Song, List<String>> =
        entries.associate { (song, _) -> song to song.aliases.map(::matchKey).filter { it.isNotEmpty() } }

    val songs: List<Song> get() = entries.map { it.first }
    val size: Int get() = entries.size

    operator fun plus(more: Collection<Song>) = SongCatalog(songs + more)

    fun find(title: String): Song? = entries.firstOrNull { it.first.key == SongKey.of(title) }?.first

    /**
     * The best match for any of [texts] (candidate title lines, most likely
     * first), or null if nothing reaches [threshold].
     *
     * With a [chart] read off the same screen, a song that has that chart at
     * that level is preferred and one that doesn't is held back, so a
     * misread title lands on the song that was actually played.
     */
    fun bestMatch(texts: List<String>, threshold: Float = DEFAULT_THRESHOLD, chart: ChartHint? = null): SongMatch? {
        var best: SongMatch? = null
        var bestScore = 0f
        for (text in texts) {
            val key = matchKey(text)
            if (key.length < 2) continue
            for ((song, songKey) in entries) {
                val sim = similarity(key, songKey)
                if (sim < threshold) continue
                val score = sim * fit(song, chart)
                if (best == null || score > bestScore) {
                    best = SongMatch(song, sim, text)
                    bestScore = score
                }
            }
        }
        return best
    }

    /** 1 with no evidence either way; a little more when the chart fits, less when it can't be this song. */
    private fun fit(song: Song, chart: ChartHint?): Float {
        if (chart == null || song.levels.isEmpty()) return 1f
        val styles = chart.style?.let { listOf(it) } ?: PlayStyle.entries
        val levels = styles.mapNotNull { song.level(it, chart.difficulty) }
        return when {
            chart.level in levels -> 1.05f
            levels.isEmpty() -> 0.85f
            else -> 0.9f
        }
    }

    /** Titles starting with or containing [query], for the manual-entry field. */
    fun suggest(query: String, limit: Int = 8): List<Song> {
        val key = matchKey(query)
        if (key.isEmpty()) return emptyList()
        return entries
            .map { (song, songKey) ->
                // Romanized names count too, so "himawari" finds 向日葵サンセット.
                val rank = (listOf(songKey) + aliasKeys.getValue(song)).maxOf { k ->
                    when {
                        k.startsWith(key) -> 2f
                        k.contains(key) -> 1f
                        else -> similarity(key, k) - 0.5f
                    }
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

        /** The catalog shipped in this module's resources (see tools/make_songlist.py). */
        fun bundled(): SongCatalog {
            val stream = SongCatalog::class.java.getResourceAsStream("songs.tsv")
                ?: error("songs.tsv missing from the classpath")
            val songs = stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                    val cols = line.split('\t')
                    Song(
                        title = cols[0].trim(),
                        aliases = cols.getOrNull(1).orEmpty().split('|').map { it.trim() }.filter { it.isNotEmpty() },
                        levels = cols.getOrNull(2).orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() },
                    )
                }.toList()
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
            return maxOf(edit, dice(a, b), subsequence(a, b))
        }

        /**
         * Characters in common, in order, against the total: "向日葵サセ"
         * still shares five of "向日葵サンセット"'s eight. Only for keys of
         * four or more, where a chance overlap is unlikely.
         */
        private fun subsequence(a: String, b: String): Float {
            if (min(a.length, b.length) < 4) return 0f
            var prev = IntArray(b.length + 1)
            var curr = IntArray(b.length + 1)
            for (i in 1..a.length) {
                for (j in 1..b.length) {
                    curr[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1 else max(prev[j], curr[j - 1])
                }
                val t = prev; prev = curr; curr = t
            }
            return 2f * prev[b.length] / (a.length + b.length)
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
