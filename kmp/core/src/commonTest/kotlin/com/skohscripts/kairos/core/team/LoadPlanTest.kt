package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.LoadPlan.Placement
import com.skohscripts.kairos.core.team.TeamFixtures.absence
import com.skohscripts.kairos.core.team.TeamFixtures.dep
import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.monday
import com.skohscripts.kairos.core.team.TeamFixtures.settings
import com.skohscripts.kairos.core.team.TeamFixtures.snapshot
import com.skohscripts.kairos.core.team.TeamFixtures.task
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadPlanTest {
    private fun day(n: Int) = monday.plus(DatePeriod(days = n))
    private fun plan(s: KairosSnapshot, weeks: Int = 4) = LoadPlan.build(s, monday, horizonWeeks = weeks)
    private fun LoadPlan.Result.t(id: Long) = checkNotNull(task(id)) { "tâche $id absente du plan" }

    @Test
    fun twoEightHourTasksFinishOnJAndJPlusOne() {
        val p = plan(snapshot(listOf(task(1, 1), task(2, 1)), listOf(member(1))))
        assertEquals(monday, p.t(1).start)
        assertEquals(monday, p.t(1).end)
        assertEquals(day(1), p.t(2).start)
        assertEquals(day(1), p.t(2).end)
        assertEquals(Placement.PLANNED, p.t(1).placement)
        assertEquals(16.0, p.of(1)!!.loadHours, 1e-9)
        assertEquals(0.0, p.of(1)!!.waitHours, 0.0)
    }

    @Test
    fun theMemberTakesTasksInSortKeyOrder() {
        // P0 passe avant P2 malgré l'identifiant.
        val s = snapshot(listOf(task(1, 1, priority = 2), task(2, 1, priority = 0)), listOf(member(1)))
        val p = plan(s)
        assertEquals(monday, p.t(2).end)
        assertEquals(day(1), p.t(1).end)
        assertEquals(listOf(2L, 1L), p.of(1)!!.tasks.map { it.taskId })
    }

    @Test
    fun aTaskBlockedByAnotherMembersTaskStartsAfterItsEndAndTheWaitIsCounted() {
        // Membre 2 : X de 24 h, fini mercredi (J+2). Membre 1 : Y de 4 h bloquée par X.
        val s = snapshot(
            tasks = listOf(task(10, 2, hours = 24.0), task(11, 1, hours = 4.0)),
            members = listOf(member(1), member(2)),
            deps = listOf(dep(blocked = 11, blocker = 10)),
        )
        val p = plan(s)
        assertEquals(day(2), p.t(10).end)
        assertEquals(day(3), p.t(11).start)
        assertEquals(day(3), p.t(11).end)
        // Trois jours perdus (lundi, mardi, mercredi), 8 h chacun.
        assertEquals(24.0, p.of(1)!!.waitHours, 1e-9)
        assertEquals(0.0, p.of(2)!!.waitHours, 0.0)
    }

    @Test
    fun aBlockedFirstTaskLetsTheMemberTakeTheNextOneThenWaits() {
        // Y (P0) est bloquée par X (membre 2, 16 h : lundi et mardi). Z (P2, 8 h) du membre 1 passe en premier.
        val s = snapshot(
            tasks = listOf(task(10, 2, hours = 16.0), task(11, 1, priority = 0), task(12, 1, priority = 2)),
            members = listOf(member(1), member(2)),
            deps = listOf(dep(blocked = 11, blocker = 10)),
        )
        val p = plan(s)
        assertEquals(monday, p.t(12).end) // Z lundi
        assertEquals(day(1), p.t(10).end)
        assertEquals(day(2), p.t(11).start) // Y après la fin de X
        assertEquals(8.0, p.of(1)!!.waitHours, 1e-9) // mardi seulement
    }

    @Test
    fun sameMemberChainContinuesTheSameDayButAnotherMembersBlockerFreesTheNextDay() {
        val sameMember = snapshot(
            listOf(task(1, 1, hours = 4.0), task(2, 1, hours = 4.0)), listOf(member(1)), deps = listOf(dep(2, 1)),
        )
        val p = plan(sameMember)
        assertEquals(monday, p.t(1).end)
        assertEquals(monday, p.t(2).end)

        val twoMembers = snapshot(
            listOf(task(1, 1, hours = 4.0), task(2, 2, hours = 4.0)), listOf(member(1), member(2)), deps = listOf(dep(2, 1)),
        )
        val q = plan(twoMembers)
        assertEquals(monday, q.t(1).end)
        assertEquals(day(1), q.t(2).start)
    }

    @Test
    fun anAbsenceShiftsTheEndDates() {
        val s = snapshot(
            listOf(task(1, 1), task(2, 1)), listOf(member(1)),
            absences = listOf(absence(1, 1, day(1), day(1))),
        )
        val p = plan(s)
        assertEquals(monday, p.t(1).end)
        assertEquals(day(2), p.t(2).end) // mardi absent -> mercredi
        // Capacité de la semaine : 4 jours x 8 h.
        assertEquals(32.0, p.of(1)!!.weeks.first().capacityHours, 1e-9)
    }

    @Test
    fun weekendsAndHolidaysAreSkipped() {
        // Vendredi 9 : 8 h ; la tâche suivante saute le week-end.
        val s = snapshot(
            List(5) { task(it + 1L, 1) } + task(6, 1), listOf(member(1)),
            settings = settings().copy(extraHolidays = "2026-10-12"),
        )
        val p = plan(s)
        assertEquals(day(4), p.t(5).end)
        assertEquals(day(8), p.t(6).end) // lundi 12 férié -> mardi 13
    }

    @Test
    fun aTaskPastItsDeadlineIsLate() {
        val s = snapshot(
            listOf(task(1, 1, deadline = day(10)), task(2, 1, deadline = day(0)), task(3, 1, deadline = monday)),
            listOf(member(1)),
        )
        val p = plan(s)
        // Échéance lundi, en retard : posée en premier (palier dur), fin lundi, pas en danger.
        val ontime = p.of(1)!!.tasks.first { it.taskId == 2L }
        assertEquals(monday, ontime.end)
        assertFalse(ontime.late)
        // La tâche 3 a la même échéance (lundi) : elle finit mardi -> 1 jour ouvré de retard.
        assertTrue(p.t(3).late)
        assertEquals(1, p.t(3).lateDays)
        assertEquals(day(2), p.t(1).end)
        assertFalse(p.t(1).late)
        assertEquals(0, p.t(1).lateDays)
    }

    @Test
    fun lateDaysCountBusinessDays() {
        // Quatre P0 (lundi à jeudi) passent devant ; la 5e finit vendredi pour une échéance mercredi : 2 jours ouvrés.
        val tasks = List(4) { task(it + 1L, 1, priority = 0) } + task(5, 1, deadline = day(2), priority = 2)
        val p = plan(snapshot(tasks, listOf(member(1))))
        assertEquals(day(4), p.t(5).end)
        assertTrue(p.t(5).late)
        assertEquals(2, p.t(5).lateDays)
        // Échéance un samedi, fin le lundi suivant : au moins 1 jour de retard.
        val weekend = List(5) { task(it + 1L, 1, priority = 0) } + task(6, 1, deadline = day(5), priority = 2)
        val q = plan(snapshot(weekend, listOf(member(1))))
        assertEquals(day(7), q.t(6).end)
        assertEquals(1, q.t(6).lateDays)
    }

    @Test
    fun dependencyCyclesTerminate() {
        val s = snapshot(
            listOf(task(1, 1), task(2, 1), task(3, 2)),
            listOf(member(1), member(2)),
            deps = listOf(dep(1, 2), dep(2, 1), dep(3, 1)),
        )
        val p = plan(s)
        // Les arêtes du cycle sont ignorées (`Dependencies.acyclicEdges`, comme la vue Jour, y compris
        // celle qui part de la tâche 3 en aval du cycle) : tout est posé, rien ne se bloque.
        assertTrue(p.tasks.all { it.placement == Placement.PLANNED })
        assertEquals(monday, p.t(1).end)
        assertEquals(day(1), p.t(2).end)
        assertEquals(monday, p.t(3).end)
        assertEquals(0.0, p.members.sumOf { it.waitHours }, 0.0)
    }

    @Test
    fun theTwoYearGuardMakesATaskOutOfHorizon() {
        val s = snapshot(
            listOf(task(1, 1, deadline = day(30)), task(2, 2)),
            listOf(member(1), member(2)),
            absences = listOf(absence(1, 1, monday, day(3 * 365))),
        )
        val p = plan(s)
        val stuck = p.t(1)
        assertEquals(Placement.OUT_OF_HORIZON, stuck.placement)
        assertNull(stuck.start)
        assertNull(stuck.end)
        assertTrue(stuck.late)
        assertEquals(8.0, p.of(1)!!.loadHours, 1e-9) // sa charge compte quand même
        assertEquals(0.0, p.of(1)!!.capacityHours, 0.0)
        assertEquals(monday, p.t(2).end) // le reste du plan n'en souffre pas
    }

    @Test
    fun aTaskBlockedByAnUnassignedTaskHasNoDate() {
        val s = snapshot(
            listOf(task(1, 1), task(2, null), task(3, 1)),
            listOf(member(1)),
            deps = listOf(dep(1, 2)),
        )
        val p = plan(s)
        assertEquals(Placement.BLOCKED_BY_BACKLOG, p.t(1).placement)
        assertNull(p.t(1).end)
        assertNull(p.task(2)) // le backlog n'est pas dans le plan
        assertEquals(monday, p.t(3).end) // sans attente comptée
        assertEquals(0.0, p.of(1)!!.waitHours, 0.0)
    }

    @Test
    fun aDoneOrOtherSpaceBlockerDoesNotBlock() {
        val s = snapshot(
            listOf(task(1, 1), task(2, null, status = com.skohscripts.kairos.core.model.TaskStatus.DONE)),
            listOf(member(1)),
            deps = listOf(dep(1, 2), dep(1, 99)),
        )
        val p = plan(s)
        assertEquals(monday, p.t(1).end)
    }

    @Test
    fun anUnestimatedTaskIsPlacedWithTheDefaultEffortAndMarked() {
        // Points par défaut 3 x 2 h/point = 6 h, comptés à part de la charge.
        val s = snapshot(
            listOf(task(1, 1, hours = null, points = null), task(2, 1, hours = 4.0)),
            listOf(member(1)),
            settings = settings(TeamSettings(focusFactor = 1.0, hoursPerPoint = 2.0)),
        )
        val p = plan(s)
        val unestimated = p.t(1)
        assertTrue(unestimated.unestimated)
        assertEquals(EffortSource.NONE, unestimated.effortSource)
        assertEquals(6.0, unestimated.hours, 1e-9)
        assertFalse(p.t(2).unestimated)
        val m = p.of(1)!!
        assertEquals(4.0, m.loadHours, 1e-9)
        assertEquals(6.0, m.assumedHours, 1e-9)
        assertEquals(10.0, m.plannedHours, 1e-9)
        assertEquals(1, m.unestimatedCount)
        assertEquals(6.0, m.weeks.first().assumedHours, 1e-9)
        assertEquals(4.0, m.weeks.first().hours, 1e-9)
    }

    @Test
    fun progressShrinksTheRemainingHours() {
        val p = plan(snapshot(listOf(task(1, 1, hours = 16.0, progress = 50)), listOf(member(1))))
        assertEquals(8.0, p.t(1).hours, 1e-9)
        assertEquals(monday, p.t(1).end)
    }

    @Test
    fun todayFractionShortensTheSelfMembersFirstDay() {
        val s = snapshot(listOf(task(1, 1)), listOf(member(1, self = true)))
        val half = LoadPlan.build(s, monday, horizonWeeks = 1, todayFraction = 0.5)
        assertEquals(day(1), half.t(1).end) // 4 h lundi, 4 h mardi
        assertEquals(36.0, half.of(1)!!.capacityHours, 1e-9)
        val notSelf = LoadPlan.build(snapshot(listOf(task(1, 1)), listOf(member(1))), monday, horizonWeeks = 1, todayFraction = 0.5)
        assertEquals(monday, notSelf.t(1).end)
    }

    @Test
    fun weeksAndCategoriesBreakTheLoadDown() {
        // 48 h pour un membre à 40 h/semaine : 40 h la 1re semaine, 8 h la 2e.
        val s = snapshot(
            listOf(task(1, 1, hours = 30.0, type = "Dev"), task(2, 1, hours = 10.0, type = ""), task(3, 1, hours = 8.0, type = "Dev")),
            listOf(member(1)),
        )
        val m = plan(s, weeks = 2).of(1)!!
        assertEquals(2, m.weeks.size)
        assertEquals(monday, m.weeks[0].start)
        assertEquals(day(6), m.weeks[0].end)
        assertEquals(day(7), m.weeks[1].start)
        assertEquals(40.0, m.weeks[0].capacityHours, 1e-9)
        assertEquals(40.0, m.weeks[1].capacityHours, 1e-9)
        assertEquals(40.0, m.weeks[0].hours, 1e-9)
        assertEquals(8.0, m.weeks[1].hours, 1e-9)
        assertEquals(mapOf("Dev" to 38.0, "" to 10.0), m.byCategory)
        assertEquals(48.0, m.loadHours, 1e-9)
        assertEquals(80.0, m.capacityHours, 1e-9)
    }

    @Test
    fun theFirstWeekOfTheHorizonStartsToday() {
        val thursday = day(3)
        val p = LoadPlan.build(snapshot(listOf(task(1, 1)), listOf(member(1))), thursday, horizonWeeks = 2)
        val m = p.of(1)!!
        assertEquals(thursday, m.weeks[0].start)
        assertEquals(day(6), m.weeks[0].end)
        assertEquals(16.0, m.weeks[0].capacityHours, 1e-9) // jeudi et vendredi
        assertEquals(day(7), m.weeks[1].start)
        assertEquals(day(13), p.horizonEnd)
    }

    @Test
    fun archivedMembersAndTheirTasksAreNotPlanned() {
        val s = snapshot(listOf(task(1, 1), task(2, 2)), listOf(member(1), member(2, archived = true)))
        val p = plan(s)
        assertNull(p.task(2))
        assertNull(p.of(2))
        assertEquals(1, p.members.size)
    }

    @Test
    fun overriddenEffortsAndCapacityGiveTheExpectedPlan() {
        val s = snapshot(listOf(task(1, 1), task(2, 1)), listOf(member(1)))
        // Efforts : 16 h pour la 1re, la 2e garde ses 8 h -> mardi, puis mercredi.
        val longer = LoadPlan.build(s, monday, efforts = mapOf(1L to 16.0))
        assertEquals(day(1), longer.t(1).end)
        assertEquals(day(2), longer.t(2).end)
        assertEquals(16.0, longer.t(1).hours, 1e-9)
        // Capacité : 4 h par jour ouvré -> 8 h sur deux jours.
        val half = LoadPlan.build(s, monday, capacity = { _, d -> if (Workdays.isWorkday(d)) 4.0 else 0.0 })
        assertEquals(day(1), half.t(1).end)
        assertEquals(day(3), half.t(2).end)
        assertEquals(40.0 - 20.0 + 0.0, half.of(1)!!.weeks.first().capacityHours, 1e-9)
        // Tirage faible : une tâche à 1 h finit le premier jour avec la suivante.
        val tiny = LoadPlan.build(s, monday, efforts = mapOf(1L to 1.0, 2L to 7.0))
        assertEquals(monday, tiny.t(1).end)
        assertEquals(monday, tiny.t(2).end)
        // Un effort tiré peut remplacer celui d'une tâche non estimée.
        val unestimated = snapshot(listOf(task(1, 1, hours = null, points = null)), listOf(member(1)))
        val drawn = LoadPlan.build(unestimated, monday, efforts = mapOf(1L to 20.0))
        assertEquals(day(2), drawn.t(1).end)
        assertTrue(drawn.t(1).unestimated)
    }

    @Test
    fun withoutOverridesTheResultEqualsTheDefaultCall() {
        val s = snapshot(
            listOf(task(1, 1, hours = 12.0), task(2, 2, hours = 20.0), task(3, 1, hours = 3.0, deadline = day(2)), task(4, 2, hours = null, points = 5)),
            listOf(member(1, percent = 50), member(2, self = true)),
            absences = listOf(absence(1, 2, day(1), day(2))),
            deps = listOf(dep(3, 2)),
        )
        val default = LoadPlan.build(s, monday, horizonWeeks = 3, todayFraction = 0.75)
        val holidays = Workdays.holidaysFor(monday, s.settings.holidaysFr, s.settings.extraHolidays)
        val explicitEfforts = default.tasks.associate { it.taskId to it.hours }
        val explicit = LoadPlan.build(
            s, monday, horizonWeeks = 3, todayFraction = 0.75, efforts = explicitEfforts,
            capacity = { m, d ->
                Capacity.dailyHours(m, d, holidays, s.absences, s.settings, if (m.isSelf && d == monday) 0.75 else 1.0)
            },
        )
        assertEquals(default, explicit)
        assertEquals(default, LoadPlan.build(s, monday, horizonWeeks = 3, todayFraction = 0.75))
    }
}
