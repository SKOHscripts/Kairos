package com.skohscripts.kairos.core.forecast

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.TimeSource

/**
 * Budget (docs/spec/previsions-taches.md § Étape C) : 200 tâches à faire, 500 tirages, à froid, en moins d'une seconde sur la
 * JVM. Le calcul est fait à la première ouverture d'un « Pourquoi ? », sur le fil de l'interface : il doit rester bref.
 */
class SoloForecastPerformanceTest {
    @Test
    fun twoHundredOpenTasksFiveHundredRunsTakeUnderOneSecond() {
        val now = Instant.parse("2026-11-16T07:00:00Z")
        val start = Instant.parse("2026-09-21T12:00:00Z")
        // 90 min par jour ouvré : la file de 200 tâches (15 à 120 min) court sur plusieurs mois.
        val sessions = (0 until 56).filter { it % 7 < 5 }.mapIndexed { k, d -> WorkSession(k + 1L, 1000L + k % 8, start + d.days, start + d.days + 90.minutes, start) }
        val done = (0 until 8).map { Task(1000L + it, "Faite $it", status = TaskStatus.DONE, estimatedMinutes = 400 + 60 * it, createdAt = now, updatedAt = now) }
        val open = (1L..200L).map { Task(it, "Ouverte $it", priority = (it % 3).toInt(), estimatedMinutes = 15 * (1 + (it % 8).toInt()), createdAt = now, updatedAt = now) }
        val mark = TimeSource.Monotonic.markNow()
        val result = SoloForecast.run(KairosSnapshot(tasks = done + open, workSessions = sessions), LocalDate(2026, 11, 16), now, TimeZone.UTC, seed = 1L)!!
        val elapsed = mark.elapsedNow()
        println("File solo 200 tâches x 500 tirages : $elapsed")
        assertEquals(200, result.outlooks.size)
        assertTrue(result.outlooks.values.all { it.p85 != null }, "une tâche hors horizon")
        assertTrue(elapsed.inWholeMilliseconds < 1000, "500 tirages en $elapsed (budget : 1 s)")
    }
}
