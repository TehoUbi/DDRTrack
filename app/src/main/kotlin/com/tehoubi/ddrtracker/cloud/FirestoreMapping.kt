package com.tehoubi.ddrtracker.cloud

import com.tehoubi.ddrtracker.data.local.PlayEntity
import com.tehoubi.ddrtracker.data.local.SessionEntity

/*
 * Firestore layout (see firebase/firestore.rules):
 *
 *   users/{uid}                      profile + totals
 *   users/{uid}/plays/{playId}       one document per play, tombstones included
 *   users/{uid}/sessions/{sessionId} derived session summaries, for analysis
 *                                    without downloading every play
 *
 * Documents are flat maps of primitives so they stay readable in the console
 * and queryable (updatedAt drives incremental pulls).
 */

const val SCHEMA_VERSION = 1L

fun PlayEntity.toDocument(): Map<String, Any?> = mapOf(
    "schema" to SCHEMA_VERSION,
    "songKey" to songKey,
    "songTitle" to songTitle,
    "artist" to artist,
    "style" to style,
    "difficulty" to difficulty,
    "level" to level.toLong(),
    "score" to score.toLong(),
    "exScore" to exScore.toLong(),
    "grade" to grade,
    "lamp" to lamp,
    "marvelous" to marvelous.toLong(),
    "perfect" to perfect.toLong(),
    "great" to great.toLong(),
    "good" to good.toLong(),
    "ok" to ok.toLong(),
    "miss" to miss.toLong(),
    "maxCombo" to maxCombo.toLong(),
    "fast" to fast?.toLong(),
    "slow" to slow?.toLong(),
    "failed" to failed,
    "playedAt" to playedAt,
    "sessionId" to sessionId,
    "source" to source,
    "scanConfidence" to scanConfidence?.toDouble(),
    "note" to note,
    "updatedAt" to updatedAt,
    "deleted" to deleted,
)

/** Reads a play document back; null if it is missing required fields. */
fun playFromDocument(id: String, d: Map<String, Any?>): PlayEntity? = runCatching {
    fun long(k: String) = (d[k] as Number).toLong()
    fun int(k: String) = long(k).toInt()
    fun optInt(k: String) = (d[k] as Number?)?.toInt()
    PlayEntity(
        id = id,
        songKey = d["songKey"] as String,
        songTitle = d["songTitle"] as String,
        artist = d["artist"] as String?,
        style = d["style"] as String,
        difficulty = d["difficulty"] as String,
        level = int("level"),
        score = int("score"),
        exScore = int("exScore"),
        grade = d["grade"] as String,
        lamp = d["lamp"] as String,
        marvelous = int("marvelous"),
        perfect = int("perfect"),
        great = int("great"),
        good = int("good"),
        ok = int("ok"),
        miss = int("miss"),
        maxCombo = int("maxCombo"),
        fast = optInt("fast"),
        slow = optInt("slow"),
        failed = d["failed"] as Boolean,
        playedAt = long("playedAt"),
        sessionId = null,
        source = d["source"] as String,
        scanConfidence = (d["scanConfidence"] as Number?)?.toFloat(),
        note = d["note"] as String?,
        updatedAt = long("updatedAt"),
        deleted = d["deleted"] as Boolean? ?: false,
    )
}.getOrNull()

fun SessionEntity.toDocument(): Map<String, Any?> = mapOf(
    "schema" to SCHEMA_VERSION,
    "startedAt" to startedAt,
    "endedAt" to endedAt,
    "playCount" to playCount.toLong(),
    "averageScore" to averageScore.toLong(),
    "bestScore" to bestScore.toLong(),
    "averageExRatio" to averageExRatio,
    "stepsHit" to stepsHit.toLong(),
    "highestLevel" to highestLevel.toLong(),
)
