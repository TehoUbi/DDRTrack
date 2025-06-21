package com.tehoubi.ddrtracker.core.stats

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Grade
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class Overview(
    val totalPlays: Int,
    val sessions: Int,
    val chartsPlayed: Int,
    val aaaCharts: Int,
    val fullComboCharts: Int,
    val averageScore: Int,
    val stepsHit: Long,
    /** Consecutive days with a play, ending today or yesterday. */
    val dayStreak: Int,
    val comfortLevel: Int?,
)

/** How the player does at one chart level, looking at personal bests. */
data class LevelSummary(
    val level: Int,
    val charts: Int,
    val plays: Int,
    val averageBest: Int,
    val clears: Int,
    val fullCombos: Int,
    val aaOrBetter: Int,
    val grades: Map<Grade, Int>,
)

data class WeekPoint(val weekStart: LocalDate, val plays: Int, val averageExRatio: Double?)

data class TimePoint(val at: Instant, val value: Double)

/** One chart's history: every play, plus the running personal best. */
data class ChartProgress<T : PlayLike>(val plays: List<T>, val best: T, val bestSteps: List<T>) {
    val improvement: Int get() = best.score - plays.first().score
}

/** Statistics over a player's whole log. Inputs need not be sorted. */
class ProgressStats<T : PlayLike>(plays: Collection<T>, private val zone: ZoneId) {

    private val plays: List<T> = plays.sortedBy { it.playedAt }

    /** Best play per chart key. Failed plays count; a fail can still be the best score. */
    val personalBests: Map<String, T> by lazy {
        this.plays.groupBy { it.chartKey }.mapValues { (_, list) -> list.sortedWith(bestFirst).first() }
    }

    fun overview(today: LocalDate = LocalDate.now(zone)): Overview {
        val bests = personalBests.values
        return Overview(
            totalPlays = plays.size,
            sessions = Sessions.group(plays).size,
            chartsPlayed = bests.size,
            aaaCharts = bests.count { it.grade == Grade.AAA },
            fullComboCharts = bests.count { it.lamp.isFullCombo },
            averageScore = if (plays.isEmpty()) 0 else plays.map { it.score }.average().toInt(),
            stepsHit = plays.sumOf { (it.judgments.steps - it.judgments.miss).toLong() },
            dayStreak = dayStreak(today),
            comfortLevel = comfortLevel(bests),
        )
    }

    fun levels(): List<LevelSummary> = personalBests.values.groupBy { it.level }.map { (level, bests) ->
        LevelSummary(
            level = level,
            charts = bests.size,
            plays = plays.count { it.level == level },
            averageBest = bests.map { it.score }.average().toInt(),
            clears = bests.count { it.lamp >= ClearLamp.CLEAR },
            fullCombos = bests.count { it.lamp.isFullCombo },
            aaOrBetter = bests.count { it.score >= Grade.AA.minScore && !it.failed },
            grades = bests.groupingBy { it.grade }.eachCount(),
        )
    }.sortedBy { it.level }

    /** Plays and average EX% per week (weeks start Monday), including empty weeks. */
    fun weekly(): List<WeekPoint> {
        if (plays.isEmpty()) return emptyList()
        val byWeek = plays.groupBy { weekStart(it.playedAt) }
        val first = byWeek.keys.min()
        val last = byWeek.keys.max()
        return generateSequence(first) { it.plusWeeks(1) }.takeWhile { it <= last }.map { week ->
            val list = byWeek[week].orEmpty()
            WeekPoint(week, list.size, list.takeIf { it.isNotEmpty() }?.map { it.exRatio }?.average())
        }.toList()
    }

    /**
     * The highest level at which the player's bests average AA (900k) or more
     * over at least [minCharts] charts: roughly, the level they're
     * comfortable at. Evaluated over time it is a simple progression curve.
     */
    fun comfortLevel(bests: Collection<T> = personalBests.values, minCharts: Int = 3): Int? =
        bests.groupBy { it.level }
            .filter { (_, list) -> list.size >= minCharts && list.map { it.score }.average() >= Grade.AA.minScore }
            .keys.maxOrNull()

    /** [comfortLevel] as it stood at the end of each month with plays. */
    fun comfortLevelHistory(): List<TimePoint> {
        val months = plays.map { it.playedAt.atZone(zone).toLocalDate().withDayOfMonth(1) }.distinct()
        return months.mapNotNull { month ->
            val end = month.plusMonths(1).atStartOfDay(zone).toInstant()
            val upTo = plays.filter { it.playedAt < end }
            val bests = upTo.groupBy { it.chartKey }.values.map { it.sortedWith(bestFirst).first() }
            comfortLevel(bests)?.let { TimePoint(end.minusMillis(1), it.toDouble()) }
        }
    }

    fun chart(chartKey: String): ChartProgress<T>? {
        val list = plays.filter { it.chartKey == chartKey }
        if (list.isEmpty()) return null
        val steps = mutableListOf<T>()
        for (p in list) if (steps.isEmpty() || bestFirst.compare(p, steps.last()) < 0) steps += p
        return ChartProgress(list, steps.last(), steps)
    }

    /** Plays that set a new personal best on their chart when they happened. */
    fun newBestIds(): Set<String> {
        val best = mutableMapOf<String, T>()
        val ids = mutableSetOf<String>()
        for (p in plays) {
            val prev = best[p.chartKey]
            if (prev == null || bestFirst.compare(p, prev) < 0) {
                if (prev != null) ids += p.id
                best[p.chartKey] = p
            }
        }
        return ids
    }

    /** Share of early (FAST) timing errors among plays that recorded FAST/SLOW, or null. */
    fun fastShare(): Double? {
        val timed = plays.filter { it.fast != null && it.slow != null }
        val fast = timed.sumOf { it.fast!! }
        val total = fast + timed.sumOf { it.slow!! }
        return if (total == 0) null else fast.toDouble() / total
    }

    fun lampCounts(): Map<ClearLamp, Int> = personalBests.values.groupingBy { it.lamp }.eachCount()

    private fun weekStart(at: Instant): LocalDate =
        at.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun dayStreak(today: LocalDate): Int {
        val days = plays.map { it.playedAt.atZone(zone).toLocalDate() }.toSortedSet()
        if (days.isEmpty()) return 0
        var day = if (today in days) today else today.minusDays(1)
        if (day !in days) return 0
        var streak = 0
        while (day in days) { streak++; day = day.minus(1, ChronoUnit.DAYS) }
        return streak
    }
}
