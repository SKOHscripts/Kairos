package com.skohscripts.kairos.core.reminders

import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.ReminderSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Récapitulatif du jour (docs/spec/rappels.md § Récapitulatif du jour, issue #151) le mardi 29 septembre 2026, journée de 9 h à 18 h. */
class DailySummaryTest {
    private val day = LocalDate(2026, 9, 29)
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")
    private val tz = TimeZone.of("Europe/Paris")
    private val on = ReminderSettings(dailySummary = true)

    private fun at(hour: Int, minute: Int = 0) = LocalDateTime(2026, 9, 29, hour, minute)

    private fun task(id: Long, p: Int = 1, minutes: Int = 30, pinned: LocalDateTime? = null, status: TaskStatus = TaskStatus.TODO, scheduled: LocalDate? = null) =
        Task(id, "T$id", priority = p, fibonacciPoints = 3, estimatedMinutes = minutes, pinnedStart = pinned, status = status, scheduledDate = scheduled, createdAt = t0, updatedAt = t0)

    private fun busy(id: Long, from: Int, to: Int, kind: BlockKind = BlockKind.BUSY) = TimeBlock(id, "B$id", at(from), at(to), kind, createdAt = t0)

    private fun snapshot(reminders: ReminderSettings?, tasks: List<Task> = emptyList(), blocks: List<TimeBlock> = emptyList(), deps: List<TaskDependency> = emptyList()) =
        KairosSnapshot(tasks = tasks, timeBlocks = blocks, dependencies = deps, settings = Settings(reminders = reminders))

    @Test
    fun nothing_when_disabled_or_never_set() {
        val tasks = listOf(task(1))
        assertNull(DailySummary.plan(snapshot(null, tasks), day, null, tz))
        assertNull(DailySummary.plan(snapshot(ReminderSettings(enabled = true), tasks), day, null, tz))
    }

    @Test
    fun at_the_chosen_time_with_counts_and_the_first_three_tasks() {
        val tasks = listOf(
            task(1, p = 0), task(2), task(3), task(4, p = 2),
            task(5, status = TaskStatus.DONE),
            task(6, scheduled = LocalDate(2026, 9, 30)),
            task(7), // bloquée par 1
        )
        val deps = listOf(TaskDependency(1, taskId = 7, blockerId = 1, createdAt = t0))
        val blocks = listOf(busy(1, 11, 12), busy(2, 15, 16, BlockKind.DEEPWORK))
        val summary = DailySummary.plan(snapshot(on, tasks, blocks, deps), day, at(7), tz)!!
        assertEquals(LocalDateTime(2026, 9, 29, 8, 30), summary.at)
        assertEquals(4, summary.tasks)
        // Seul le créneau occupé compte ; le deep work n'est pas un créneau occupé.
        assertEquals(1, summary.slots)
        assertEquals(3, summary.first.size)
        assertEquals(summary.first.sortedBy { it.start }, summary.first)
        // Les premières du plan de la vue Jour à l'heure du récapitulatif ; ni la faite, ni celle de demain, ni la bloquée.
        val plan = com.skohscripts.kairos.core.day.DayView.build(snapshot(on, tasks, blocks, deps), day, at(8, 30), tz).schedule.scheduled
        assertEquals(plan.take(3).map { it.task.title }, summary.first.map { it.title })
        assertEquals(setOf("T1", "T2", "T3", "T4"), plan.map { it.task.title }.toSet())
        assertEquals("summary-2026-09-29", summary.key)
    }

    @Test
    fun a_slot_alone_is_enough_and_an_empty_day_sends_nothing() {
        assertEquals(1, DailySummary.plan(snapshot(on, blocks = listOf(busy(1, 10, 11))), day, null, tz)?.slots)
        assertNull(DailySummary.plan(snapshot(on), day, null, tz))
        assertNull(DailySummary.plan(snapshot(on, listOf(task(1, status = TaskStatus.DONE))), day, null, tz))
    }

    @Test
    fun a_passed_time_is_not_caught_up_and_the_time_is_read_from_the_settings() {
        val tasks = listOf(task(1))
        assertNull(DailySummary.plan(snapshot(on, tasks), day, at(8, 30), tz))
        assertNull(DailySummary.plan(snapshot(on, tasks), day, at(9), tz))
        val early = DailySummary.plan(snapshot(on.copy(dailySummaryTime = "07:15"), tasks), day, at(6), tz)!!
        assertEquals(at(7, 15), early.at)
        // Valeur illisible (base modifiée à la main) : l'heure par défaut.
        assertEquals(at(8, 30), DailySummary.plan(snapshot(on.copy(dailySummaryTime = "plus tard"), tasks), day, null, tz)!!.at)
    }
}
