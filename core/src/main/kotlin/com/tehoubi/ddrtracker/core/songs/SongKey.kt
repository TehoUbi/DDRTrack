package com.tehoubi.ddrtracker.core.songs

import java.security.MessageDigest
import java.text.Normalizer

/**
 * Stable identifier for a song, shared by the app and the leaderboard server
 * so two players who scanned the same title land on the same chart.
 *
 * "PARANOiA Rebirth" -> "paranoia-rebirth". Titles with no Latin letters or
 * digits (Japanese-only titles) fall back to a short hash of the NFKC form.
 */
object SongKey {

    fun of(title: String): String {
        val nfkc = Normalizer.normalize(title.trim(), Normalizer.Form.NFKC)
        val ascii = Normalizer.normalize(nfkc, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
        val slug = ascii.replace(Regex("[^a-z0-9]+"), "-").trim('-')
        if (slug.isNotEmpty()) return slug.take(80)
        val digest = MessageDigest.getInstance("SHA-1").digest(nfkc.lowercase().toByteArray())
        return "u" + digest.take(6).joinToString("") { "%02x".format(it) }
    }

    /** Key for one chart of a song: "paranoia-rebirth/SINGLE/EXPERT". */
    fun chart(songKey: String, style: String, difficulty: String): String = "$songKey/$style/$difficulty"
}
