package com.skohscripts.kairos.core.reminders

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.ReminderSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Rappels (docs/spec/rappels.md, issue #103) sur le mardi 29 septembre 2026, 9 h, journée de 9 h à 18 h. */
class RemindersTest {
    private val day = LocalDate(2026, 9, 29)
    private val now = LocalDateTime(2026, 9, 29, 9, 0)
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")
    private val tz = TimeZone.of("Europe/Paris")

    private fun task(id: Long, pinned: LocalDateTime? = null, status: TaskStatus = TaskStatus.TODO, p: Int = 1, minutes: Int = 30, scheduled: LocalDate? = null) =
        Task(id, "T$id", priority = p, fibonacciPoints = 3, estimatedMinutes = minutes, pinnedStart = pinned, status = status, scheduledDate = scheduled, createdAt = t0, updatedAt = t0)

    private val tasks = listOf(
        task(1, pinned = LocalDateTime(2026, 9, 29, 14, 0)),
        task(2, pinned = LocalDateTime(2026, 9, 29, 8, 30)), // déjà passée
        task(3, pinned = LocalDateTime(2026, 9, 29, 15, 0), status = TaskStatus.DONE),
        task(4, pinned = LocalDateTime(2026, 9, 29, 16, 0)), // bloquée par 5
        task(5, p = 0, minutes = 60), // la première du plan : en cours à 9 h
        task(6, minutes = 30),
        task(7, pinned = LocalDateTime(2026, 9, 30, 17, 0), scheduled = LocalDate(2026, 9, 30)), // demain
    )
    private val deps = listOf(TaskDependency(1, taskId = 4, blockerId = 5, createdAt = t0))

    private fun snapshot(reminders: ReminderSettings?) = KairosSnapshot(tasks = tasks, dependencies = deps, settings = Settings(taskTypes = "", reminders = reminders))

    @Test
    fun nothing_when_disabled_or_never_set() {
        assertEquals(emptyList(), Reminders.plan(snapshot(null), day, now, tz))
        assertEquals(emptyList(), Reminders.plan(snapshot(ReminderSettings(enabled = false, nextTask = true)), day, now, tz))
    }

    @Test
    fun a_fixed_time_task_is_reminded_before_it_starts_and_only_it() {
        val got = Reminders.plan(snapshot(ReminderSettings(enabled = true, leadMinutes = 10)), day, now, tz)
        assertEquals(listOf(1L), got.map { it.taskId })
        val r = got.single()
        assertEquals(ReminderKind.FIXED, r.kind)
        assertEquals(LocalDateTime(2026, 9, 29, 13, 50), r.at)
        assertEquals(LocalDateTime(2026, 9, 29, 14, 0), r.start)
        assertEquals(LocalDateTime(2026, 9, 29, 14, 30), r.end)
        assertEquals("fixed-1-2026-09-29T14:00", r.key)
        // Avance 0 : à l'heure même.
        assertEquals(LocalDateTime(2026, 9, 29, 14, 0), Reminders.plan(snapshot(ReminderSettings(enabled = true, leadMinutes = 0)), day, now, tz).single().at)
        // À 13 h 55, le rappel de 13 h 50 est passé : il n'est pas rattrapé.
        assertEquals(emptyList(), Reminders.plan(snapshot(ReminderSettings(enabled = true)), day, LocalDateTime(2026, 9, 29, 13, 55), tz))
    }

    @Test
    fun the_next_task_of_the_plan_is_signalled_at_its_start_but_not_the_current_one() {
        val got = Reminders.plan(snapshot(ReminderSettings(enabled = true, nextTask = true)), day, now, tz)
        // La 5 commence à 9 h (maintenant) : pas de rappel ; la 6 vient après ; la 1 garde son rappel d'heure fixe.
        assertTrue(got.none { it.taskId == 5L })
        val next = got.single { it.taskId == 6L }
        assertEquals(ReminderKind.NEXT, next.kind)
        assertEquals(next.start, next.at)
        assertTrue(next.at > now)
        assertEquals(ReminderKind.FIXED, got.single { it.taskId == 1L }.kind)
        assertEquals(got.sortedBy { it.at }, got)
        // Ni faite, ni bloquée, ni demain.
        assertTrue(got.none { it.taskId in setOf(3L, 4L, 7L) })
    }

    @Test
    fun tomorrow_is_planned_from_the_start_of_the_day() {
        val tomorrow = Reminders.plan(snapshot(ReminderSettings(enabled = true)), LocalDate(2026, 9, 30), null, tz)
        assertEquals(listOf(7L), tomorrow.map { it.taskId })
        assertEquals(LocalDateTime(2026, 9, 30, 16, 50), tomorrow.single().at)
    }
}
