package com.tehoubi.ddrtracker.core.stats

import java.time.Duration
import java.time.Instant

/** Plays in one visit to the arcade. */
data class Session<T : PlayLike>(val plays: List<T>) {
    init { require(plays.isNotEmpty()) }

    /** Stable as long as the session's first play is: that play's id. */
    val id: String get() = plays.first().id
    val startedAt: Instant get() = plays.first().playedAt
    val endedAt: Instant get() = plays.last().playedAt
    val duration: Duration get() = Duration.between(startedAt, endedAt)
    val playCount: Int get() = plays.size
    val averageScore: Int get() = plays.map { it.score }.average().toInt()
    val bestScore: Int get() = plays.maxOf { it.score }
    val averageExRatio: Double get() = plays.map { it.exRatio }.average()

    /** Arrows stepped on (everything but Misses), a rough measure of effort. */
    val stepsHit: Int get() = plays.sumOf { it.judgments.steps - it.judgments.miss }
    val highestLevel: Int get() = plays.maxOf { it.level }
}

object Sessions {

    /** A break longer than this starts a new session. */
    val DEFAULT_GAP: Duration = Duration.ofMinutes(45)

    /** Groups plays into sessions, oldest first. Input order doesn't matter. */
    fun <T : PlayLike> group(plays: Collection<T>, gap: Duration = DEFAULT_GAP): List<Session<T>> {
        if (plays.isEmpty()) return emptyList()
        val sorted = plays.sortedWith(compareBy<T> { it.playedAt }.thenBy { it.id })
        val sessions = mutableListOf<Session<T>>()
        var current = mutableListOf(sorted.first())
        for (play in sorted.drop(1)) {
            if (Duration.between(current.last().playedAt, play.playedAt) > gap) {
                sessions += Session(current)
                current = mutableListOf()
            }
            current += play
        }
        sessions += Session(current)
        return sessions
    }

    /** Play id -> id of the session it belongs to. */
    fun <T : PlayLike> assign(plays: Collection<T>, gap: Duration = DEFAULT_GAP): Map<String, String> =
        group(plays, gap).flatMap { s -> s.plays.map { it.id to s.id } }.toMap()
}
