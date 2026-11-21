package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.Interruption
import com.skohscripts.kairos.core.model.InterruptionSource
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.stats.InterruptionStats
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Dérangements attendus et statistiques (docs/spec/derangements.md § Dérangements attendus, § Statistiques). Mardi 24 novembre
 * 2026 (UTC), journée de 9 h à 18 h ; historique : les 8 semaines du 28 septembre au 22 novembre.
 */
class ExpectedInterruptionsTest {
    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 11, 24)
    private val t0 = Instant.parse("2026-09-01T00:00:00Z")

    /** Deux dérangements de 10 min à 10 h sur chacun des [days] jours (du lundi 16 novembre en arrière). */
    private fun history(days: Int, perDay: Int = 2): List<Interruption> {
        var id = 0L
        return (0 until days).flatMap { k ->
            val day = LocalDate(2026, 11, 20).minus(DatePeriod(days = k))
            (0 until perDay).map { j ->
                val at = day.atTime(10, 10 + 20 * j).toInstant(tz)
                Interruption(++id, at, 10, InterruptionSource.MESSAGE, createdAt = at)
            }
        }
    }

    private fun snapshot(interruptions: List<Interruption>, blocks: List<TimeBlock> = emptyList(), tasks: List<Task> = emptyList()) =
        KairosSnapshot(tasks = tasks, timeBlocks = blocks, interruptions = interruptions, settings = Settings(taskTypes = ""))

    @Test
    fun nothingIsExpectedBelowTheHistoryThreshold() {
        assertTrue(ExpectedInterruptions.learn(snapshot(history(days = 4, perDay = 3)), today, tz).none, "4 jours actifs")
        assertTrue(ExpectedInterruptions.learn(snapshot(history(days = 9, perDay = 1)), today, tz).none, "9 dérangements")
    }

    @Test
    fun theRateOfAnHourIsItsLostMinutesOverItsExposure() {
        val expected = ExpectedInterruptions.learn(snapshot(history(days = 6)), today, tz)
        // 6 jours × 60 min d'exposition à 10 h ; 12 × 10 min perdues : un tiers.
        assertEquals(1.0 / 3, expected.rate(10), 1e-9)
        assertEquals(0.0, expected.rate(11))
        assertEquals(0.0, expected.rate(9))
    }

    @Test
    fun deepWorkIsNeverInterrupted() {
        // Un créneau de deep work de 10 h à 11 h chaque jour : l'heure n'est plus exposée, ses dérangements ne comptent pas.
        val blocks = (0 until 6).map { k ->
            val day = LocalDate(2026, 11, 20).minus(DatePeriod(days = k))
            TimeBlock(100L + k, "Deep work", day.atTime(10, 0), day.atTime(11, 0), BlockKind.DEEPWORK, createdAt = t0)
        }
        val expected = ExpectedInterruptions.learn(snapshot(history(days = 6), blocks), today, tz)
        assertEquals(0.0, expected.rate(10))
        // Et l'attendu d'une plage ignore le deep work.
        val rates = ExpectedInterruptions(List(24) { if (it == 10) 0.5 else 0.0 })
        assertEquals(30.0, rates.expected(today.atTime(9, 0), today.atTime(12, 0)), 1e-9)
        assertEquals(15.0, rates.expected(today.atTime(9, 0), today.atTime(12, 0), listOf(today.atTime(10, 0) to today.atTime(10, 30))), 1e-9)
    }

    @Test
    fun aTaskStretchesByTheRateOfItsHours() {
        val rates = ExpectedInterruptions(List(24) { if (it == 10) 1.0 / 3 else 0.0 })
        assertEquals(45, rates.stretched(30, today.atTime(10, 0)))
        // 10 min à 10 h font 6,67 min de travail, le reste à 11 h sans dérangement.
        assertEquals(34, rates.stretched(30, today.atTime(10, 50)))
        assertEquals(30, ExpectedInterruptions.NONE.stretched(30, today.atTime(10, 0)))
    }

    @Test
    fun theTimelineStretchesOutsideDeepWorkAndAvailabilityShrinks() {
        val now = today.atTime(10, 0)
        val task = Task(1, "Rapport", priority = 1, fibonacciPoints = 3, estimatedMinutes = 30, createdAt = t0, updatedAt = t0)
        val rates = ExpectedInterruptions(List(24) { if (it == 10) 1.0 / 3 else 0.0 })
        val plain = Scheduling.buildDaySchedule(listOf(task), emptyList(), today, now, Settings(taskTypes = ""))
        val withRates = Scheduling.buildDaySchedule(listOf(task), emptyList(), today, now, Settings(taskTypes = ""), expected = rates)
        assertEquals(30, plain.scheduled.single().durationMinutes)
        assertEquals(45, withRates.scheduled.single().durationMinutes)
        assertEquals(plain.stats.availableMinutes - 20, withRates.stats.availableMinutes)
        assertEquals(20, withRates.stats.expectedInterruptionMinutes)
        // Sans dérangement attendu, exactement le placement d'avant.
        assertEquals(plain, Scheduling.buildDaySchedule(listOf(task), emptyList(), today, now, Settings(taskTypes = ""), expected = ExpectedInterruptions.NONE))
        // Dans un créneau de deep work, la tâche garde sa durée.
        val deep = TimeBlock(9, "Deep work", today.atTime(10, 0), today.atTime(12, 0), BlockKind.DEEPWORK, createdAt = t0)
        val inDeep = Scheduling.buildDaySchedule(listOf(task), listOf(deep), today, now, Settings(taskTypes = ""), expected = rates)
        assertEquals(30, inDeep.scheduled.single().durationMinutes)
        assertEquals(0, inDeep.stats.expectedInterruptionMinutes)
    }

    @Test
    fun theStatisticsSummariseCountTimeMomentsSourcesAndCalm() {
        val start = LocalDate(2026, 11, 16)
        val end = LocalDate(2026, 11, 22)
        val interruptions = history(days = 3) + Interruption(99, LocalDateTime(2026, 11, 17, 15, 0).toInstant(tz), 30, null, createdAt = t0)
        val deep = TimeBlock(50, "Deep work", LocalDateTime(2026, 11, 20, 10, 0), LocalDateTime(2026, 11, 20, 12, 0), BlockKind.DEEPWORK, createdAt = t0)
        val calm = TimeBlock(51, "Deep work", LocalDateTime(2026, 11, 19, 14, 0), LocalDateTime(2026, 11, 19, 16, 0), BlockKind.DEEPWORK, createdAt = t0)
        val s = InterruptionStats.summary(snapshot(interruptions, listOf(deep, calm)), start, end, Instant.parse("2026-11-24T12:00:00Z"), tz)
        assertEquals(7, s.count)
        assertEquals(90, s.minutes)
        // Aucun temps chronométré : tout le temps mesuré est dérangé.
        assertEquals(1.0, s.share)
        assertEquals(6, s.byHour[10])
        assertEquals(1, s.byHour[14])
        assertEquals(mapOf(InterruptionSource.MESSAGE to 6, null to 1), s.bySource)
        assertEquals(InterruptionSource.MESSAGE, s.mainSource)
        assertEquals(2, s.deepWork)
        assertEquals(1, s.deepWorkInterrupted)
        // Mercredi 18, jeudi 19, vendredi 20 : de 10 h 30 à 18 h, 450 min ; mardi 17 : de 9 h à 14 h 30, 330 min. Médiane : 450.
        assertEquals(450, s.longestCalmMedian)
        assertEquals(InterruptionStats.Moment(4, afternoon = false), InterruptionStats.mainMoment(interruptions.filter { it.id <= 2 }, tz))
        assertNull(InterruptionStats.mainMoment(emptyList(), tz))
    }
}
