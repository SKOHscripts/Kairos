package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.DaySchedule
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.ScheduleSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.day.DayView
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Horaires par jour de la semaine (docs/spec/planning-personnalise.md § Lot B1). */
class ScheduleTest {
    private val monday = LocalDate(2026, 9, 28)
    private val saturday = LocalDate(2026, 10, 3)
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")

    private fun task(id: Long) = Task(id, "T$id", priority = 1, fibonacciPoints = 3, estimatedMinutes = 60, createdAt = t0, updatedAt = t0)

    private fun settings(vararg days: Pair<Int, DaySchedule>) = Settings(schedule = ScheduleSettings(days.toMap()))

    @Test
    fun withoutAScheduleEverySevenDaysHaveTheSettingsWindow() {
        val s = Settings(workdayStartHour = 8, workdayEndHour = 17)
        for (offset in 0L..6L) {
            val day = LocalDate.fromEpochDays(monday.toEpochDays() + offset)
            assertEquals(Schedule.Window(LocalDateTime(day.year, day.month, day.day, 8, 0), LocalDateTime(day.year, day.month, day.day, 17, 0)), Schedule.windowFor(day, s))
        }
    }

    @Test
    fun aCustomDayHasItsOwnWindowAndOthersKeepTheUsualOne() {
        val s = settings(5 to DaySchedule(startHour = 10, endHour = 14)) // samedi
        assertEquals(10, Schedule.windowFor(saturday, s)!!.startHour)
        assertEquals(14, Schedule.windowFor(saturday, s)!!.endHour)
        assertEquals(9, Schedule.windowFor(monday, s)!!.startHour)
        assertEquals(18, Schedule.windowFor(monday, s)!!.endHour)
    }

    @Test
    fun aFreeDayHasNoWindowButStillShowsTheUsualOne() {
        val s = settings(6 to DaySchedule(free = true))
        val sunday = LocalDate(2026, 10, 4)
        assertNull(Schedule.windowFor(sunday, s))
        assertEquals(9, Schedule.displayWindow(sunday, s).startHour)
    }

    @Test
    fun anIncompleteOrInconsistentDayFallsBackToTheUsualWindow() {
        val s = settings(0 to DaySchedule(startHour = 10), 1 to DaySchedule(startHour = 15, endHour = 12))
        assertEquals(9, Schedule.windowFor(monday, s)!!.startHour)
        assertEquals(9, Schedule.windowFor(LocalDate(2026, 9, 29), s)!!.startHour)
    }

    @Test
    fun tasksAreOnlyPlacedInTheDaysOwnWindow() {
        val s = settings(5 to DaySchedule(startHour = 10, endHour = 12))
        val tasks = listOf(task(1), task(2), task(3))
        val day = Scheduling.buildDaySchedule(tasks, emptyList(), saturday, null, s)
        // 10 h-12 h : deux tâches d'une heure, la troisième n'a pas de créneau.
        assertEquals(listOf(10, 11), day.scheduled.map { it.start.hour })
        assertEquals(1, day.unscheduled.size)
        assertEquals(120, day.stats.availableMinutes)
    }

    @Test
    fun aFreeDayPlacesNothingAutomaticallyButKeepsPinnedTasks() {
        val s = settings(6 to DaySchedule(free = true))
        val sunday = LocalDate(2026, 10, 4)
        val pinned = task(2).copy(pinnedStart = LocalDateTime(2026, 10, 4, 15, 0))
        val day = Scheduling.buildDaySchedule(listOf(task(1), pinned), emptyList(), sunday, null, s)
        assertEquals(listOf(2L), day.scheduled.map { it.task.id })
        assertEquals(listOf(1L), day.unscheduled.map { it.id })
        assertEquals(0, day.stats.availableMinutes)
    }

    @Test
    fun theDayViewExposesTheWindowAndTheFreeDay() {
        val s = settings(5 to DaySchedule(startHour = 10, endHour = 14), 6 to DaySchedule(free = true))
        val snapshot = KairosSnapshot(tasks = listOf(task(1)), settings = s)
        val sat = DayView.build(snapshot, saturday, null, TimeZone.UTC)
        assertEquals(10, sat.workStartHour)
        assertEquals(14, sat.workEndHour)
        assertTrue(!sat.freeDay)
        val sun = DayView.build(snapshot, LocalDate(2026, 10, 4), null, TimeZone.UTC)
        assertTrue(sun.freeDay)
        assertEquals(9, sun.workStartHour) // la frise garde la journée des réglages
    }

    @Test
    fun theUsualScheduleIsUnchangedByADefaultScheduleObject() {
        // Un horaire sans aucun jour propre n'est jamais écrit (SettingsForm), mais s'il existe il ne change rien.
        val tasks = listOf(task(1), task(2))
        val plain = Scheduling.buildDaySchedule(tasks, emptyList(), monday, null, Settings())
        val withEmpty = Scheduling.buildDaySchedule(tasks, emptyList(), monday, null, Settings(schedule = ScheduleSettings()))
        assertEquals(plain, withEmpty)
    }

    @Test
    fun aNonPositiveEstimateIsNotExplicit() {
        // QA du 2026-11-04 : -30 donnait une durée de placement négative ; seule une estimation positive est explicite.
        val s = Settings()
        assertEquals(s.defaultTaskDurationMinutes, Scheduling.durationMinutes(task(1).copy(estimatedMinutes = -30), s))
        assertEquals(s.defaultTaskDurationMinutes, Scheduling.durationMinutes(task(1).copy(estimatedMinutes = 0), s))
        assertEquals(45, Scheduling.durationMinutes(task(1).copy(estimatedMinutes = -30), s, mapOf(1L to 45)))
    }
}
