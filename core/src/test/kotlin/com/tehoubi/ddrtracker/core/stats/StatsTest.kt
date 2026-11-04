package com.tehoubi.ddrtracker.core.stats

import com.tehoubi.ddrtracker.core.model.Grade
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatsTest {

    private val zone = ZoneOffset.UTC

    private val log = listOf(
        // Session 1: Saturday evening
        TestPlay("a", "PARANOiA", 14, 880_000, at("2026-04-04T19:00:00Z")),
        TestPlay("b", "MAX 300", 15, 820_000, at("2026-04-04T19:05:00Z")),
        TestPlay("c", "PARANOiA", 14, 905_000, at("2026-04-04T19:40:00Z")),
        // Session 2: next day, after a long break
        TestPlay("d", "PARANOiA", 14, 899_990, at("2026-04-05T18:00:00Z")),
        TestPlay("e", "Butterfly", 12, 992_000, at("2026-04-05T18:04:00Z"), fc = true),
        TestPlay("f", "B4U", 12, 950_000, at("2026-04-05T18:09:00Z"), fast = 10, slow = 30),
        TestPlay("g", "AM-3P", 12, 930_000, at("2026-04-05T18:14:00Z"), fast = 5, slow = 5),
    )

    @Test
    fun `sessions split on long gaps`() {
        val sessions = Sessions.group(log.shuffled())
        assertEquals(listOf(listOf("a", "b", "c"), listOf("d", "e", "f", "g")), sessions.map { s -> s.plays.map { it.id } })
        assertEquals("a", sessions[0].id)
        assertEquals(40, sessions[0].duration.toMinutes())
        assertEquals("d", Sessions.assign(log)["g"])
    }

    @Test
    fun `personal bests keep the top score per chart`() {
        val stats = ProgressStats(log, zone)
        assertEquals(5, stats.personalBests.size)
        assertEquals("c", stats.personalBests.values.first { it.songTitle == "PARANOiA" }.id)
        assertEquals(setOf("c"), stats.newBestIds())
    }

    @Test
    fun `chart progress tracks each improvement`() {
        val p = ProgressStats(log, zone).chart(log.first().chartKey)!!
        assertEquals(listOf("a", "c"), p.bestSteps.map { it.id })
        assertEquals(25_000, p.improvement)
    }

    @Test
    fun `overview counts`() {
        val o = ProgressStats(log, zone).overview(today = LocalDate.parse("2026-04-06"))
        assertEquals(7, o.totalPlays)
        assertEquals(2, o.sessions)
        assertEquals(5, o.chartsPlayed)
        assertEquals(1, o.aaaCharts)
        assertEquals(1, o.fullComboCharts)
        assertEquals(2, o.dayStreak)
        assertEquals(12, o.comfortLevel)
    }

    @Test
    fun `streak breaks after a missed day`() {
        assertEquals(0, ProgressStats(log, zone).overview(today = LocalDate.parse("2026-04-08")).dayStreak)
    }

    @Test
    fun `level summaries use bests`() {
        val levels = ProgressStats(log, zone).levels().associateBy { it.level }
        assertEquals(3, levels.getValue(12).charts)
        assertEquals(3, levels.getValue(14).plays)
        assertEquals(905_000, levels.getValue(14).averageBest)
        assertEquals(1, levels.getValue(12).grades[Grade.AAA])
    }

    @Test
    fun `comfort level needs enough charts`() {
        val stats = ProgressStats(log.filter { it.level != 12 }, zone)
        assertNull(stats.overview().comfortLevel)
    }

    @Test
    fun `weekly series fills gaps`() {
        val later = log + TestPlay("z", "PARANOiA", 14, 910_000, at("2026-04-21T18:00:00Z"))
        val weeks = ProgressStats(later, zone).weekly()
        assertEquals(listOf(LocalDate.parse("2026-03-30"), LocalDate.parse("2026-04-06"), LocalDate.parse("2026-04-13"), LocalDate.parse("2026-04-20")), weeks.map { it.weekStart })
        assertEquals(listOf(7, 0, 0, 1), weeks.map { it.plays })
        assertNull(weeks[1].averageExRatio)
    }

    @Test
    fun `timing bias covers the recent plays that recorded it`() {
        val bias = ProgressStats(log, zone).timingBias()!!
        assertEquals(15, bias.fast)
        assertEquals(35, bias.slow)
        assertEquals(TimingBias.Lean.LATE, bias.lean)
        assertNull(ProgressStats(log.map { it.copy(fast = null, slow = null) }, zone).timingBias())

        // Only the last few count: an old early habit doesn't outweigh recent late plays.
        val old = (1..20).map { TestPlay("e$it", "B4U", 12, 950_000, at("2026-01-01T18:00:00Z").plusSeconds(it * 300L), fast = 30, slow = 2) }
        val recent = (1..10).map { TestPlay("l$it", "B4U", 12, 950_000, at("2026-03-01T18:00:00Z").plusSeconds(it * 300L), fast = 3, slow = 9) }
        assertEquals(TimingBias.Lean.LATE, ProgressStats(old + recent, zone).timingBias()!!.lean)
    }

    @Test
    fun `timing only leans with enough judgments and a clear split`() {
        assertEquals(TimingBias.Lean.EVEN, TimingBias(fast = 20, slow = 5, plays = 3).lean) // too few to tell
        assertEquals(TimingBias.Lean.EARLY, TimingBias(fast = 33, slow = 17, plays = 5).lean)
        assertEquals(TimingBias.Lean.EVEN, TimingBias(fast = 30, slow = 20, plays = 5).lean)
        assertEquals(TimingBias.Lean.LATE, TimingBias(fast = 10, slow = 30, plays = 5).lean)
    }

    @Test
    fun `ex ratio`() {
        val p = log.first()
        assertEquals(p.exScore / (3.0 * p.judgments.total), p.exRatio)
    }
}
