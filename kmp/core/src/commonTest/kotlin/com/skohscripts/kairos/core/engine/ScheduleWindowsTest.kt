package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.day.DayView
import com.skohscripts.kairos.core.model.DaySchedule
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.ScheduleSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.WorkContext
import com.skohscripts.kairos.core.model.WorkWindow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Plages multiples et contextes (docs/spec/planning-personnalise.md § Lot B2). */
class ScheduleWindowsTest {
    private val saturday = LocalDate(2026, 10, 3)
    private val monday = LocalDate(2026, 9, 28)
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")

    private fun task(id: Long, type: String = "", minutes: Int = 60, priority: Int = 1) =
        Task(id, "T$id", priority = priority, fibonacciPoints = 3, estimatedMinutes = minutes, taskType = type, createdAt = t0, updatedAt = t0)

    private val contexts = listOf(WorkContext("Work", listOf("dev", "Réunion")), WorkContext("Home", listOf("maison")))

    private fun settings(windows: List<WorkWindow>, day: Int = 5, contexts: List<WorkContext> = emptyList()) =
        Settings(taskTypes = "", schedule = ScheduleSettings(mapOf(day to DaySchedule(windows = windows)), contexts))

    private fun place(tasks: List<Task>, s: Settings, day: LocalDate = saturday) = Scheduling.buildDaySchedule(tasks, emptyList(), day, null, s)

    @Test
    fun severalWindowsFillInOrderAndSkipTheGap() {
        val s = settings(listOf(WorkWindow(9, 11), WorkWindow(14, 16)))
        val day = place((1L..5L).map { task(it) }, s)
        assertEquals(listOf(9, 10, 14, 15), day.scheduled.map { it.start.hour })
        assertEquals(listOf(5L), day.unscheduled.map { it.id })
        // Le disponible est la somme des plages, pas la journée entière.
        assertEquals(240, day.stats.availableMinutes)
    }

    @Test
    fun aSingleWindowListIsTheSameDayAsTheThreeFourRange() {
        val tasks = (1L..4L).map { task(it, minutes = 45 + it.toInt()) }
        val old = place(tasks, Settings(taskTypes = "", schedule = ScheduleSettings(mapOf(5 to DaySchedule(startHour = 10, endHour = 13)))))
        val list = place(tasks, settings(listOf(WorkWindow(10, 13))))
        assertEquals(old, list)
    }

    @Test
    fun aTaskOfAContextOnlyGoesInAWindowOfThatContext() {
        val s = settings(listOf(WorkWindow(9, 11, "Work"), WorkWindow(18, 20, "Home")), contexts = contexts)
        // La tâche « maison » est la plus urgente : elle attend sa plage, elle ne passe pas devant le travail.
        val home = task(1, "maison", priority = 0)
        val dev = task(2, "dev")
        val free = task(3) // sans contexte : n'importe quelle plage
        val day = place(listOf(home, dev, free), s)
        val byId = day.scheduled.associate { it.task.id to it.start.hour }
        assertEquals(mapOf(2L to 9, 3L to 10, 1L to 18), byId)
        assertTrue(day.noWindow.isEmpty())
    }

    @Test
    fun aContextWithoutAWindowThatDayGoesToUnscheduledWithItsReason() {
        val s = settings(listOf(WorkWindow(9, 12, "Work")), contexts = contexts)
        val day = place(listOf(task(1, "maison"), task(2, "dev")), s)
        assertEquals(listOf(2L), day.scheduled.map { it.task.id })
        assertEquals(listOf(1L), day.unscheduled.map { it.id })
        assertEquals(mapOf(1L to "Home"), day.noWindow)
    }

    @Test
    fun anOpenWindowTakesEveryContext() {
        val s = settings(listOf(WorkWindow(9, 12)), contexts = contexts)
        val day = place(listOf(task(1, "maison"), task(2, "dev")), s)
        assertEquals(setOf(1L, 2L), day.scheduled.map { it.task.id }.toSet())
        assertTrue(day.noWindow.isEmpty())
    }

    @Test
    fun theUsualDayAndALegacyRangeStayOpenToEveryContext() {
        // « Tout doit fonctionner comme avant » : définir des contextes ne prive aucun jour sans plages propres de ses tâches.
        val s = Settings(taskTypes = "", schedule = ScheduleSettings(mapOf(5 to DaySchedule(startHour = 10, endHour = 12)), contexts))
        assertEquals(1, place(listOf(task(1, "maison")), s, monday).scheduled.size)
        assertEquals(1, place(listOf(task(1, "maison")), s, saturday).scheduled.size)
    }

    @Test
    fun aTaskTooLongForAWindowTakesTheNextOneThatFitsIt() {
        val s = settings(listOf(WorkWindow(9, 10), WorkWindow(14, 16)))
        val day = place(listOf(task(1, minutes = 90), task(2, minutes = 90)), s)
        // La première commence à 9 h (elle déborde, comme avant) ; la seconde ne commence pas dans la plage de 9 h : elle passe à 14 h.
        assertEquals(listOf(9, 14), day.scheduled.map { it.start.hour })
        assertTrue(day.unscheduled.isEmpty())
    }

    @Test
    fun theContextFilterNeverChangesTheOrderOrTheScore() {
        val plain = task(1, "dev")
        val without = Settings(taskTypes = "")
        val with = Settings(taskTypes = "", schedule = ScheduleSettings(contexts = contexts))
        assertEquals(Scheduling.sortKey(plain, saturday, without), Scheduling.sortKey(plain, saturday, with))
        assertEquals(Scheduling.wsjfScore(plain, saturday, without), Scheduling.wsjfScore(plain, saturday, with))
    }

    @Test
    fun everyOpenTaskIsInExactlyOneList() {
        val s = settings(listOf(WorkWindow(9, 10, "Work"), WorkWindow(18, 19, "Home")), contexts = contexts)
        val tasks = listOf(task(1, "dev"), task(2, "dev"), task(3, "maison"), task(4, "maison"), task(5), task(6).copy(priority = null))
        val day = place(tasks, s)
        val all = day.scheduled.map { it.task.id } + day.unscheduled.map { it.id } + day.later.map { it.id } + day.toProcess.map { it.id }
        assertEquals(tasks.map { it.id }.sorted(), all.sorted())
    }

    @Test
    fun windowsAreResolvedSortedAndInconsistentOnesAreIgnored() {
        val s = settings(listOf(WorkWindow(14, 16), WorkWindow(9, 11), WorkWindow(10, 12), WorkWindow(15, 15), WorkWindow(20, 25)))
        val windows = Schedule.windowsFor(saturday, s)
        // Triées ; celle qui chevauche la précédente et les incohérentes sont ignorées.
        assertEquals(listOf(9 to 11, 14 to 16), windows.map { it.startHour to it.endHour })
        assertEquals(9, Schedule.windowFor(saturday, s)!!.startHour)
        assertEquals(16, Schedule.windowFor(saturday, s)!!.endHour)
        // Aucune plage valable : la journée des réglages.
        assertEquals(listOf(9 to 18), Schedule.windowsFor(saturday, settings(listOf(WorkWindow(15, 15)))).map { it.startHour to it.endHour })
        // Un jour libre n'a aucune plage.
        assertEquals(emptyList(), Schedule.windowsFor(saturday, Settings(taskTypes = "", schedule = ScheduleSettings(mapOf(5 to DaySchedule(free = true))))))
    }

    @Test
    fun aTaskContextComesFromItsTypeOnly() {
        val s = Settings(taskTypes = "", schedule = ScheduleSettings(contexts = contexts))
        assertEquals("Work", Schedule.contextOf("DEV", s))
        assertEquals("Work", Schedule.contextOf(" Réunion ", s))
        assertEquals("Home", Schedule.contextOf("maison", s))
        assertNull(Schedule.contextOf("", s))
        assertNull(Schedule.contextOf("inconnu", s))
        assertNull(Schedule.contextOf("dev", Settings(taskTypes = "")))
    }

    @Test
    fun theDayViewExposesTheWindowsAndTheReason() {
        val s = settings(listOf(WorkWindow(9, 11, "Work"), WorkWindow(14, 16, "Work")), contexts = contexts)
        val snapshot = KairosSnapshot(tasks = listOf(task(1, "maison"), task(2, "dev")), settings = s)
        val view = DayView.build(snapshot, saturday, null, TimeZone.UTC)
        assertEquals(listOf(9 to 11, 14 to 16), view.windows.map { it.startHour to it.endHour })
        assertEquals(9, view.workStartHour)
        assertEquals(16, view.workEndHour)
        assertEquals(mapOf(1L to "Home"), view.noWindow)
        assertEquals(listOf(1L), view.unscheduled.map { it.id })
    }

    @Test
    fun withoutAScheduleNothingChangesForTheEngine() {
        val tasks = (1L..4L).map { task(it, "dev", minutes = 30 + it.toInt()) }
        assertEquals(
            Scheduling.buildDaySchedule(tasks, emptyList(), monday, null, Settings(taskTypes = "")),
            Scheduling.buildDaySchedule(tasks, emptyList(), monday, null, Settings(taskTypes = "", schedule = ScheduleSettings(contexts = contexts))),
        )
    }

    @Test
    fun aTaskMustEndBeforeTheEndOfItsWindow() {
        // Issue #194 : la fin de la plage borne la tâche entière, comme un créneau occupé.
        val s = settings(listOf(WorkWindow(9, 11), WorkWindow(14, 17)))
        val day = place(listOf(task(1, minutes = 90), task(2, minutes = 60), task(3, minutes = 30), task(4, minutes = 150)), s)
        val start = day.scheduled.associate { it.task.id to it.start.time.toString() }
        // 2 ne tient plus à 10 h 30 : la plus courte prend la place et 2 attend l'après-midi. Après 2, 4 (2 h 30) ne tient plus
        // avant 17 h : « Sans créneau », sans être « trop longue » (elle tiendrait dans la plage de l'après-midi entière).
        assertEquals(mapOf(1L to "09:00", 3L to "10:30", 2L to "14:00"), start - 4L)
        assertEquals(listOf(4L), day.unscheduled.map { it.id })
        assertTrue(day.scheduled.none { it.oversized })

        // Une seule plage : à 17 h 45 dans une journée qui finit à 18 h, une tâche de 2 h part « Sans créneau ».
        val late = Scheduling.buildDaySchedule(
            listOf(task(1, minutes = 120), task(2, minutes = 15)), emptyList(), saturday, saturday.atTime(17, 45), settings(listOf(WorkWindow(9, 18))),
        )
        assertEquals(listOf(2L), late.scheduled.map { it.task.id })
        assertEquals(listOf(1L), late.unscheduled.map { it.id })
    }

    @Test
    fun aTaskLongerThanEveryWindowIsStillPlaced() {
        // Issue #194 : une grosse tâche n'est pas reléguée pour sa taille ; elle déborde et la vue Jour le dit.
        val s = settings(listOf(WorkWindow(9, 11), WorkWindow(14, 16)))
        val day = place(listOf(task(1, minutes = 180), task(2, minutes = 60)), s)
        val big = day.scheduled.single { it.task.id == 1L }
        assertEquals(9, big.start.hour)
        assertTrue(big.oversized)
        val small = day.scheduled.single { it.task.id == 2L }
        assertEquals(14, small.start.hour)
        assertTrue(!small.oversized)
        assertTrue(day.unscheduled.isEmpty())
    }
}
