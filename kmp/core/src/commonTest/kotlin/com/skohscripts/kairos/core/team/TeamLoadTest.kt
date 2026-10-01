package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.TeamFixtures.absence
import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.monday
import com.skohscripts.kairos.core.team.TeamFixtures.mondayMorning
import com.skohscripts.kairos.core.team.TeamFixtures.settings
import com.skohscripts.kairos.core.team.TeamFixtures.snapshot
import com.skohscripts.kairos.core.team.TeamFixtures.task
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class TeamLoadTest {
    private val utc = TimeZone.UTC

    // Un horizon d'une semaine, sans focus : A 40 h, B 20 h (4 h/j), C 40 h.
    private val members = listOf(member(1, "A"), member(2, "B", hoursPerDay = 4.0), member(3, "C"))
    private val oneWeek = settings(TeamSettings(focusFactor = 1.0, horizonWeeks = 1))

    private fun scenario(): KairosSnapshot = snapshot(
        tasks = listOf(
            // A : 44 h = 110 % (surchargé), dont une tâche en danger (échéance mardi, finie mercredi).
            task(1, 1, hours = 24.0, type = "Dev", deadline = monday.plus(DatePeriod(days = 1))),
            task(2, 1, hours = 20.0, type = "Dev"),
            // B : 19 h = 95 % (à surveiller) + une tâche non estimée (hors charge).
            task(3, 2, hours = 19.0, type = ""),
            task(4, 2, hours = null, points = null, type = ""),
            // C : 20 h = 50 %.
            task(5, 3, hours = 20.0, type = "Doc"),
            // Backlog non assigné : 30 h Dev, 10 h sans catégorie, une non estimée Dev.
            task(6, null, hours = 30.0, type = "Dev"),
            task(7, null, hours = 10.0, type = ""),
            task(8, null, hours = null, points = null, type = "Dev"),
        ),
        members = members + member(9, "Z", archived = true),
        absences = listOf(absence(1, 2, monday.plus(DatePeriod(days = 7)), monday.plus(DatePeriod(days = 11)))),
        settings = oneWeek,
    )

    private fun build(s: KairosSnapshot = scenario(), horizonWeeks: Int? = null, now: Instant = mondayMorning) =
        TeamLoad.build(s, now, utc, horizonWeeks)

    @Test
    fun ratesAndThresholds() {
        val load = build()
        val byName = load.members.associateBy { it.member.name }
        assertEquals(listOf("A", "B", "C"), load.members.map { it.member.name })
        assertEquals(40.0, byName.getValue("A").capacityHours, 1e-9)
        assertEquals(44.0, byName.getValue("A").loadHours, 1e-9)
        assertEquals(110.0, byName.getValue("A").ratePercent!!, 1e-9)
        assertEquals(LoadLevel.OVERLOADED, byName.getValue("A").level)
        assertEquals(20.0, byName.getValue("B").capacityHours, 1e-9)
        assertEquals(95.0, byName.getValue("B").ratePercent!!, 1e-9)
        assertEquals(LoadLevel.WATCH, byName.getValue("B").level)
        assertEquals(50.0, byName.getValue("C").ratePercent!!, 1e-9)
        assertEquals(LoadLevel.OK, byName.getValue("C").level)
        // Équipe : 100 h de capacité, 83 h assignées (membre archivé exclu).
        assertEquals(100.0, load.capacityHours, 1e-9)
        assertEquals(83.0, load.assignedHours, 1e-9)
        assertEquals(83.0, load.ratePercent!!, 1e-9)
        assertEquals(LoadLevel.OK, load.level)
        assertEquals(1, load.horizonWeeks)
    }

    @Test
    fun thresholdsAreStrict() {
        fun level(rate: Double?, load: Double = 1.0, warn: Int = 90) = TeamLoad.level(rate, load, warn)
        assertEquals(LoadLevel.OK, level(90.0))
        assertEquals(LoadLevel.WATCH, level(90.5))
        assertEquals(LoadLevel.WATCH, level(100.0))
        assertEquals(LoadLevel.OVERLOADED, level(100.5))
        assertEquals(LoadLevel.OK, level(50.0, warn = 50))
        // Capacité nulle : surchargé s'il y a de la charge, sinon rien à dire.
        assertEquals(LoadLevel.OVERLOADED, level(null, load = 2.0))
        assertEquals(LoadLevel.OK, level(null, load = 0.0))
    }

    @Test
    fun theWarnPercentComesFromTheSettings() {
        val s = scenario().copy(settings = settings(TeamSettings(focusFactor = 1.0, horizonWeeks = 1, loadWarnPercent = 96)))
        assertEquals(LoadLevel.OK, build(s).members.first { it.member.name == "B" }.level)
    }

    @Test
    fun teamWeeksAreBacklogHoursOverWeeklyTeamCapacity() {
        val load = build()
        // 30 h + 10 h = 40 h estimées ; capacité hebdomadaire nominale 40 + 20 + 40 = 100 h.
        assertEquals(40.0, load.backlogHours, 1e-9)
        assertEquals(0.4, load.backlogTeamWeeks!!, 1e-9)
        assertEquals(3, load.backlogTaskCount)
        // Le focus entre dans la capacité hebdomadaire.
        val focus = scenario().copy(settings = settings(TeamSettings(focusFactor = 0.5, horizonWeeks = 1)))
        assertEquals(0.8, build(focus).backlogTeamWeeks!!, 1e-9)
        assertNull(build(snapshot(listOf(task(1, null)), emptyList())).backlogTeamWeeks)
    }

    @Test
    fun unestimatedTasksAreCountedApartNeverAsZeroHours() {
        val load = build()
        assertEquals(1, load.unestimatedAssigned)
        assertEquals(1, load.unestimatedBacklog)
        assertEquals(1, load.members.first { it.member.name == "B" }.unestimatedCount)
        // La charge de B ne compte que ses 19 h estimées.
        assertEquals(19.0, load.members.first { it.member.name == "B" }.loadHours, 1e-9)
        assertEquals(40.0, load.backlogHours, 1e-9)
    }

    @Test
    fun categoriesIncludeNoCategoryAndAreSortedHeaviestFirst() {
        val cats = build().categories
        assertEquals(listOf("Dev", "", "Doc"), cats.map { it.category })
        val dev = cats[0]
        assertEquals(44.0, dev.assignedHours, 1e-9)
        assertEquals(30.0, dev.backlogHours, 1e-9)
        assertEquals(74.0, dev.totalHours, 1e-9)
        assertEquals(0.74, dev.capacityShare!!, 1e-9)
        assertEquals(1, dev.unestimatedCount) // la tâche 8
        val none = cats[1]
        assertEquals(19.0, none.assignedHours, 1e-9)
        assertEquals(10.0, none.backlogHours, 1e-9)
        assertEquals(0.29, none.capacityShare!!, 1e-9)
        assertEquals(1, none.unestimatedCount) // la tâche 4
        assertEquals(0.2, cats[2].capacityShare!!, 1e-9)
        assertEquals(0, cats[2].unestimatedCount)
    }

    @Test
    fun spreadIsTheLowestAndHighestRate() {
        val spread = assertNotNull(build().spread)
        assertEquals("C", spread.lowest.member.name)
        assertEquals("A", spread.highest.member.name)
        assertEquals(60.0, spread.gapPercent, 1e-9)
        assertNull(build(snapshot(listOf(task(1, 1)), listOf(member(1)), settings = oneWeek)).spread)
    }

    @Test
    fun nextAbsenceAndEndangeredDeadlines() {
        val load = build(horizonWeeks = 2)
        val b = load.members.first { it.member.name == "B" }
        assertEquals(monday.plus(DatePeriod(days = 7)), b.nextAbsence!!.start)
        assertNull(load.members.first { it.member.name == "A" }.nextAbsence)
        // 24 h pour une échéance mardi : fin mercredi -> en danger.
        assertEquals(1, load.members.first { it.member.name == "A" }.atRiskCount)
        assertEquals(0, b.atRiskCount)
        assertEquals(2, load.horizonWeeks)
    }

    @Test
    fun openTaskCountsSplitInProgressAndTodo() {
        val s = snapshot(
            listOf(task(1, 1, startedOn = monday), task(2, 1), task(3, 1), task(4, null)),
            listOf(member(1)), settings = oneWeek,
        )
        val a = build(s).members.single()
        assertEquals(1, a.inProgressCount)
        assertEquals(2, a.todoCount)
    }

    @Test
    fun theHorizonScalesTheCapacity() {
        assertEquals(100.0, build(horizonWeeks = 1).capacityHours, 1e-9)
        // B est absent la 2e semaine : 40 + 0 + 40 de plus.
        assertEquals(100.0 + 80.0, build(horizonWeeks = 2).capacityHours, 1e-9)
    }

    @Test
    fun selfCapacityLosesTheElapsedPartOfToday() {
        val s = snapshot(listOf(task(1, 1)), listOf(member(1, self = true)), settings = oneWeek)
        // 13 h 30 : la moitié de la journée de 9 h à 18 h reste.
        val afternoon = Instant.parse("2026-10-05T13:30:00Z")
        assertEquals(36.0, build(s, now = afternoon).capacityHours, 1e-9)
        assertEquals(40.0, build(s).capacityHours, 1e-9)
        // Les autres membres comptent en entier.
        val other = snapshot(listOf(task(1, 1)), listOf(member(1)), settings = oneWeek)
        assertEquals(40.0, build(other, now = afternoon).capacityHours, 1e-9)
    }

    @Test
    fun anEmptyTeamIsHandled() {
        val load = build(snapshot(emptyList(), emptyList(), settings = oneWeek))
        assertEquals(0.0, load.capacityHours, 0.0)
        assertNull(load.ratePercent)
        assertEquals(LoadLevel.OK, load.level)
        assertEquals(emptyList(), load.categories)
    }

    @Test
    fun aBacklogTaskOfAnArchivedMemberCountsAsBacklog() {
        val s = snapshot(listOf(task(1, 9, hours = 5.0)), listOf(member(1), member(9, archived = true)), settings = oneWeek)
        val load = build(s)
        assertEquals(5.0, load.backlogHours, 1e-9)
        assertEquals(0.0, load.assignedHours, 0.0)
    }
}
