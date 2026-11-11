package com.skohscripts.kairos.core.recap

import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Bilan d'une période (docs/spec/bilan.md) : semaine du lundi 28 septembre 2026, vue le jeudi 1er octobre à 10 h UTC. */
class RecapTest {
    private val t0 = Instant.parse("2026-09-01T07:00:00Z")
    private val now = Instant.parse("2026-10-01T10:00:00Z")
    private val monday = LocalDate(2026, 9, 28)
    private val sunday = LocalDate(2026, 10, 4)

    private fun at(text: String) = Instant.parse(text)
    private fun session(id: Long, task: Long, start: String, end: String) = WorkSession(id, task, at(start), at(end), t0)

    private val snapshot = KairosSnapshot(
        tasks = listOf(
            Task(1, "Rapport", projectTag = "B", status = TaskStatus.DONE, taskType = "Dev", fibonacciPoints = 5, createdAt = t0, updatedAt = at("2026-09-29T16:00:00Z")),
            Task(2, "Audit", projectTag = "A", status = TaskStatus.DONE, fibonacciPoints = 3, createdAt = t0, updatedAt = at("2026-09-30T16:00:00Z")),
            Task(3, "Vieille", status = TaskStatus.DONE, createdAt = t0, updatedAt = at("2026-09-27T16:00:00Z")), // dimanche d'avant
            Task(4, "Migration", projectTag = "A", taskType = "Dev", deadline = LocalDate(2026, 9, 30), createdAt = t0, updatedAt = t0), // avancée, en retard
            Task(5, "Devis", deadline = LocalDate(2026, 10, 2), createdAt = t0, updatedAt = t0), // reste, pas en retard
            Task(6, "Plus tard", deadline = LocalDate(2026, 10, 5), createdAt = t0, updatedAt = t0), // semaine suivante
        ),
        workSessions = listOf(
            session(1, 1, "2026-09-29T08:00:00Z", "2026-09-29T09:30:00Z"),
            session(2, 4, "2026-09-30T08:00:00Z", "2026-09-30T08:40:00Z"),
            session(3, 1, "2026-09-21T08:00:00Z", "2026-09-21T10:00:00Z"), // semaine d'avant : hors du temps de la période
        ),
        timeBlocks = listOf(
            TimeBlock(1, "Point", LocalDateTime(2026, 9, 1, 9, 0), LocalDateTime(2026, 9, 1, 9, 30), recurrence = BlockRecurrence.WEEKDAYS, createdAt = t0),
            TimeBlock(2, "Comité", LocalDateTime(2026, 9, 29, 14, 0), LocalDateTime(2026, 9, 29, 16, 0), createdAt = t0),
            TimeBlock(3, "Focus", LocalDateTime(2026, 9, 29, 10, 0), LocalDateTime(2026, 9, 29, 12, 0), kind = BlockKind.DEEPWORK, createdAt = t0),
            TimeBlock(4, "Revue", LocalDateTime(2026, 10, 2, 14, 0), LocalDateTime(2026, 10, 2, 15, 0), createdAt = t0), // pas encore eu lieu
        ),
    )

    @Test
    fun what_was_done_advanced_met_and_left() {
        val recap = Recap.build(snapshot, monday, sunday, now, TimeZone.UTC)
        // Fait : par projet puis titre, avec le temps de la période seulement (pas les 2 h de la semaine d'avant).
        assertEquals(listOf(2L to 0, 1L to 90), recap.done.map { it.task.id to it.minutes })
        assertEquals(8, recap.points)
        assertEquals(listOf(4L to 40), recap.advanced.map { it.task.id to it.minutes })
        // Créneaux occupés terminés, regroupés : le point de lundi à jeudi (9h30 < 10h), le comité ; ni deep work ni vendredi.
        assertEquals(listOf(Recap.Slot("Comité", 1, 120), Recap.Slot("Point", 4, 120)), recap.slots)
        assertEquals(240, recap.slotMinutes)
        assertEquals(listOf(4L, 5L), recap.left.map { it.id })
        assertEquals(1, recap.late)
        assertEquals(130, recap.spentMinutes)
        assertEquals(mapOf("Dev" to 130), recap.spentByType)
    }

    @Test
    fun the_filter_keeps_matching_tasks_and_their_time_but_every_slot() {
        val recap = Recap.build(snapshot, monday, sunday, now, TimeZone.UTC, DayFilter(project = "A"))
        assertEquals(listOf(2L), recap.done.map { it.task.id })
        assertEquals(listOf(4L), recap.advanced.map { it.task.id })
        assertEquals(listOf(4L), recap.left.map { it.id })
        assertEquals(40, recap.spentMinutes)
        assertEquals(2, recap.slots.size)
    }

    @Test
    fun an_empty_period_says_so() {
        assertTrue(Recap.build(KairosSnapshot(), monday, sunday, now, TimeZone.UTC).empty)
        // Le mois d'octobre : la tâche de la semaine suivante y reste, celle de fin septembre n'y est plus.
        val october = Recap.build(snapshot, LocalDate(2026, 10, 1), LocalDate(2026, 10, 31), now, TimeZone.UTC)
        assertEquals(listOf(5L, 6L), october.left.map { it.id })
        assertEquals(emptyList(), october.done)
    }
}
