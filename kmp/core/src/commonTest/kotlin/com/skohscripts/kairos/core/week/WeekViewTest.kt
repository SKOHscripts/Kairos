package com.skohscripts.kairos.core.week

import kotlinx.datetime.LocalTime
import com.skohscripts.kairos.core.model.BlockException
import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.DaySchedule
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.ScheduleSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class WeekViewTest {
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")

    private fun task(id: Long, deadline: LocalDate? = null, p: Int? = null, status: TaskStatus = TaskStatus.TODO, updated: Instant = t0, type: String = "") =
        Task(id, "T$id", priority = p, deadline = deadline, status = status, taskType = type, createdAt = t0, updatedAt = updated)

    @Test
    fun scheduled_tasks_appear_on_their_day_once() {
        val wed = LocalDate(2026, 9, 30)
        val thu = LocalDate(2026, 10, 1)
        val snapshot = KairosSnapshot(
            tasks = listOf(
                task(1).copy(scheduledDate = wed), // programmée seulement : ce jour-là
                task(2, deadline = wed).copy(scheduledDate = wed), // échéance le même jour : une seule ligne (l'échéance)
                task(3, deadline = thu).copy(scheduledDate = wed), // programmée avant son échéance : seulement au jour programmé
                task(4, status = TaskStatus.DONE).copy(scheduledDate = wed), // faite : pas programmée
                task(5).copy(scheduledDate = LocalDate(2026, 10, 7)), // semaine suivante
            ),
        )
        val view = WeekView.build(snapshot, wed, t0, TimeZone.UTC)
        val day = view.days.single { it.date == wed }
        assertEquals(listOf(1L, 3L), day.scheduled.map { it.id })
        assertEquals(listOf(2L), day.tasks.map { it.id })
        // Le jeudi de son échéance ne la montre plus (issue #106) : une tâche, une seule ligne dans la semaine.
        assertEquals(emptyList(), view.days.single { it.date == thu }.tasks.map { it.id })
        // Programmée après son échéance (déplacée du mercredi au vendredi) : seulement le vendredi.
        val fri = LocalDate(2026, 10, 2)
        val moved = WeekView.build(KairosSnapshot(tasks = listOf(task(6, deadline = wed).copy(scheduledDate = fri))), wed, t0, TimeZone.UTC)
        assertEquals(emptyList(), moved.days.single { it.date == wed }.tasks + moved.days.single { it.date == wed }.scheduled)
        assertEquals(listOf(6L), moved.days.single { it.date == fri }.scheduled.map { it.id })
    }

    @Test
    fun holidays_and_days_off_are_flagged() {
        // Semaine du 9 novembre 2026 : Armistice le mercredi ; le samedi est un jour libre des horaires par jour.
        val settings = Settings(holidaysFr = true, schedule = ScheduleSettings(mapOf(5 to DaySchedule(free = true))))
        val week = WeekView.build(KairosSnapshot(settings = settings), LocalDate(2026, 11, 11), t0, TimeZone.UTC)
        assertEquals(listOf(false, false, true, false, false, false, false), week.days.map { it.holiday })
        assertEquals(listOf(false, false, false, false, false, true, false), week.days.map { it.dayOff })
    }

    @Test
    fun seven_days_with_deadlines_done_and_blocks() {
        val wed = LocalDate(2026, 9, 30)
        val snapshot = KairosSnapshot(
            tasks = listOf(
                task(1, wed, p = 2), task(2, wed), task(3, wed, p = 0), task(4, wed, status = TaskStatus.ARCHIVED),
                task(5, status = TaskStatus.DONE, updated = Instant.parse("2026-09-30T10:00:00Z")),
                task(6, LocalDate(2026, 10, 5)), // semaine suivante
            ),
            timeBlocks = listOf(
                TimeBlock(1, "Déjeuner", LocalDateTime(2026, 9, 1, 12, 0), LocalDateTime(2026, 9, 1, 13, 0), recurrence = BlockRecurrence.WEEKDAYS, createdAt = t0),
                TimeBlock(2, "Point", LocalDateTime(2026, 9, 30, 9, 0), LocalDateTime(2026, 9, 30, 9, 30), createdAt = t0),
            ),
            workSessions = listOf(
                WorkSession(1, 1, Instant.parse("2026-09-29T08:00:00Z"), Instant.parse("2026-09-29T09:30:00Z"), t0),
                WorkSession(2, 5, Instant.parse("2026-09-21T08:00:00Z"), Instant.parse("2026-09-21T09:00:00Z"), t0),
            ),
        )
        val week = WeekView.build(snapshot, LocalDate(2026, 10, 2), t0, TimeZone.UTC)
        assertEquals(LocalDate(2026, 9, 28), week.monday)
        assertEquals(7, week.days.size)
        val wednesday = week.days[2]
        assertEquals(listOf(3L, 1L, 2L), wednesday.tasks.map { it.id })
        assertEquals(listOf(5L), wednesday.done.map { it.id })
        assertEquals(listOf("Point", "Déjeuner"), wednesday.blocks.map { it.title })
        assertEquals(emptyList(), week.days[5].blocks) // samedi : pas de déjeuner « jours ouvrés »
        assertEquals(listOf(1L), WeekView.build(snapshot, wed, t0, TimeZone.UTC, DayFilter(priority = 2)).days[2].tasks.map { it.id })
    }

    @Test
    fun a_weekday_slot_skips_the_holidays_of_the_week() {
        // Semaine du 9 novembre 2026 : Armistice le mercredi, jour de pont déclaré le vendredi.
        val snapshot = KairosSnapshot(
            timeBlocks = listOf(
                TimeBlock(1, "Stand-up", LocalDateTime(2026, 11, 2, 9, 30), LocalDateTime(2026, 11, 2, 9, 45), recurrence = BlockRecurrence.WEEKDAYS, createdAt = t0),
            ),
            settings = Settings(holidaysFr = true, extraHolidays = "2026-11-13"),
        )
        val week = WeekView.build(snapshot, LocalDate(2026, 11, 11), t0, TimeZone.UTC)
        assertEquals(listOf(1, 1, 0, 1, 0, 0, 0), week.days.map { it.blocks.size })
    }

    @Test
    fun a_skipped_or_changed_occurrence_is_reflected_in_the_week() {
        val standUp = TimeBlock(1, "Stand-up", LocalDateTime(2026, 11, 2, 9, 30), LocalDateTime(2026, 11, 2, 9, 45), recurrence = BlockRecurrence.WEEKDAYS, createdAt = t0)
        val snapshot = KairosSnapshot(
            timeBlocks = listOf(standUp),
            blockExceptions = listOf(
                BlockException(1, 1, LocalDate(2026, 11, 17), skipped = true, createdAt = t0),
                BlockException(2, 1, LocalDate(2026, 11, 18), start = LocalTime(10, 0), end = LocalTime(10, 30), createdAt = t0),
            ),
        )
        val week = WeekView.build(snapshot, LocalDate(2026, 11, 16), t0, TimeZone.UTC)
        assertEquals(listOf(1, 0, 1, 1, 1, 0, 0), week.days.map { it.blocks.size })
        assertEquals(LocalDateTime(2026, 11, 18, 10, 0), week.days[2].blocks.single().start)
    }

    @Test
    fun a_monthly_slot_appears_once_in_its_week() {
        // « 3e mardi » posé le 20 octobre 2026 : en novembre, le mardi 17.
        val review = TimeBlock(1, "Revue", LocalDateTime(2026, 10, 20, 14, 0), LocalDateTime(2026, 10, 20, 15, 0), recurrence = BlockRecurrence.MONTHLY_WEEKDAY, createdAt = t0)
        val snapshot = KairosSnapshot(timeBlocks = listOf(review))
        assertEquals(listOf(0, 1, 0, 0, 0, 0, 0), WeekView.build(snapshot, LocalDate(2026, 11, 16), t0, TimeZone.UTC).days.map { it.blocks.size })
        assertEquals(List(7) { 0 }, WeekView.build(snapshot, LocalDate(2026, 11, 9), t0, TimeZone.UTC).days.map { it.blocks.size })
    }
}
