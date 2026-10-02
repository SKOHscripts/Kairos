package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.AssignmentSuggestion.Decider
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
import kotlin.test.assertTrue
import kotlin.time.Instant

class AssignmentSuggestionTest {
    private val utc = TimeZone.UTC
    private fun day(n: Int) = monday.plus(DatePeriod(days = n))

    private fun suggest(s: KairosSnapshot, ids: Set<Long>? = null, weeks: Int? = null) =
        AssignmentSuggestion.suggest(s, mondayMorning, utc, ids, weeks)

    private val pair = listOf(member(1), member(2))

    @Test
    fun isDeterministic() {
        val tasks = (1L..12L).map { task(it, null, hours = (it % 5 + 1) * 3.0, priority = (it % 3).toInt(), type = listOf("Dev", "Doc", "")[(it % 3).toInt()]) } +
            task(20, 1, hours = 10.0) + task(21, 2, hours = 14.0)
        val s = snapshot(tasks, pair + member(3, hoursPerDay = 6.0, percent = 60))
        val a = suggest(s)
        val b = suggest(s)
        assertEquals(a, b)
        assertEquals(12, a.suggestions.size)
        // L'ordre des listes d'entrée ne change pas la proposition.
        val shuffled = s.copy(tasks = s.tasks.reversed(), members = s.members.reversed())
        assertEquals(a, suggest(shuffled))
    }

    @Test
    fun anUrgentTaskDoesNotGoToAnOverloadedMemberByJumpingHisQueue() {
        // Membre 1 a 80 h de travail ; membre 2 est libre. Une P0 placée à son rang chez 1 finirait
        // tôt en repoussant tout le reste : on juge la fin de la file entière, elle va à 2.
        val busy = (10L..19L).map { task(it, 1, hours = 8.0, priority = 2) }
        val urgent = task(1, null, hours = 8.0, priority = 0)
        val r = suggest(snapshot(busy + urgent, pair))
        assertEquals(2L, r.suggestions.single().memberId)
        assertEquals(monday, r.suggestions.single().plannedEnd)
    }

    @Test
    fun neverProposesAnArchivedOrFullyAbsentMember() {
        val s = snapshot(
            tasks = (1L..4L).map { task(it, null) },
            members = listOf(member(1), member(2), member(3, archived = true), member(4)),
            // Membre 2 absent sur les 4 semaines de l'horizon ; membre 4 absent une semaine seulement.
            absences = listOf(absence(1, 2, monday, day(27)), absence(2, 4, monday, day(4))),
        )
        val r = suggest(s)
        assertTrue(r.suggestions.none { it.memberId == 2L || it.memberId == 3L })
        assertEquals(setOf(1L, 4L), r.suggestions.map { it.memberId }.toSet())
        assertEquals(1, r.archivedMembers)
    }

    @Test
    fun nobodyAvailableMeansNoSuggestion() {
        val s = snapshot(listOf(task(1, null)), listOf(member(1)), absences = listOf(absence(1, 1, monday, day(27))))
        val r = suggest(s)
        assertEquals(emptyList(), r.suggestions)
        assertEquals(listOf(1L), r.unplaceable)
        assertEquals(emptyList(), suggest(snapshot(listOf(task(1, null)), emptyList())).suggestions)
    }

    @Test
    fun equalCapacitySplitsInAlternation() {
        val s = snapshot((1L..4L).map { task(it, null) }, pair)
        val r = suggest(s)
        assertEquals(listOf(1L, 2L, 1L, 2L), r.suggestions.map { it.memberId })
        assertEquals(listOf(1L, 2L, 3L, 4L), r.suggestions.map { it.taskId })
        // Les fins prévues : lundi, lundi, mardi, mardi.
        assertEquals(listOf(day(0), day(0), day(1), day(1)), r.suggestions.map { it.plannedEnd })
        assertEquals(Decider.ID, r.suggestions[0].reason.decidedBy)
        assertEquals(Decider.LOAD, r.suggestions[1].reason.decidedBy)
    }

    @Test
    fun theEarliestEndWinsOverTheLighterLoadBeyondTheTolerance() {
        // Membre 1 : 5 jours de travail déjà assignés ; membre 2 libre. Nouvelle tâche d'un jour.
        val s = snapshot(listOf(task(1, 1, hours = 40.0), task(2, null)), pair)
        val r = suggest(s)
        assertEquals(2L, r.suggestions.single().memberId)
        assertEquals(Decider.EARLIEST, r.suggestions.single().reason.decidedBy)
    }

    private fun doneTasks(member: Long, count: Int, type: String, idFrom: Long, updatedAt: Instant) =
        (0 until count).map { task(idFrom + it, member, status = TaskStatus.DONE, type = type, updatedAt = updatedAt) }

    @Test
    fun affinityBreaksTiesWithinTheTolerance() {
        val recent = Instant.parse("2026-09-15T10:00:00Z")
        val old = Instant.parse("2026-06-01T10:00:00Z") // plus de 12 semaines
        val tasks = doneTasks(2, 3, "Dev", 100, recent) + doneTasks(1, 5, "Dev", 200, old) + task(1, null, type = "Dev")
        val r = suggest(snapshot(tasks, pair))
        val sg = r.suggestions.single()
        assertEquals(2L, sg.memberId)
        assertEquals(3, sg.reason.affinityTasks) // les 5 anciennes de l'autre ne comptent pas
        assertEquals("Dev", sg.reason.category)
        assertEquals(Decider.AFFINITY, sg.reason.decidedBy)
        assertEquals(day(0), sg.reason.plannedEnd)
        assertEquals(sg.plannedEnd, sg.reason.plannedEnd)
    }

    @Test
    fun affinityDoesNotOverrideAMuchEarlierEnd() {
        val recent = Instant.parse("2026-09-15T10:00:00Z")
        // Le membre 2 a l'affinité mais 5 jours de travail déjà assignés : fin à J+5 > J + 2 jours ouvrés.
        val tasks = doneTasks(2, 3, "Dev", 100, recent) + task(1, 2, hours = 40.0) + task(2, null, type = "Dev")
        val sg = suggest(snapshot(tasks, pair)).suggestions.single()
        assertEquals(1L, sg.memberId)
        assertEquals(0, sg.reason.affinityTasks)
        // Avec une tolérance de 5 jours ouvrés, l'affinité reprend la main.
        val tolerant = settings(TeamSettings(focusFactor = 1.0, affinityDays = 5))
        assertEquals(2L, suggest(snapshot(tasks, pair, settings = tolerant)).suggestions.single().memberId)
    }

    @Test
    fun noCategoryNeverCountsAffinity() {
        val recent = Instant.parse("2026-09-15T10:00:00Z")
        val tasks = doneTasks(2, 3, "", 100, recent) + task(1, null, type = "")
        val sg = suggest(snapshot(tasks, pair)).suggestions.single()
        assertEquals(0, sg.reason.affinityTasks)
        assertEquals(1L, sg.memberId)
    }

    @Test
    fun affinityUsesTheDoneEventDate() {
        // updatedAt est récent mais l'événement `done` date de plus de 12 semaines : pas d'affinité.
        val recent = Instant.parse("2026-09-30T10:00:00Z")
        val tasks = doneTasks(2, 3, "Dev", 100, recent) + task(1, null, type = "Dev")
        val events = (100L..102L).map {
            TeamEvent(it, it, "T$it", 2, TeamEventKind.DONE, null, "100", TeamEventSource.MANUAL, Instant.parse("2026-05-01T10:00:00Z"))
        }
        val sg = suggest(snapshot(tasks, pair, events = events)).suggestions.single()
        assertEquals(0, sg.reason.affinityTasks)
        assertEquals(1L, sg.memberId)
    }

    @Test
    fun theWipLimitSendsAnOverloadedMemberAfterTheOthers() {
        // Membre 1 : deux tâches en cours (1 h) > limite 1, charge basse. Membre 2 : une tâche à faire de 5 h.
        val base = listOf(
            task(10, 1, hours = 1.0, startedOn = monday), task(11, 1, hours = 1.0, startedOn = monday),
            task(12, 2, hours = 5.0), task(1, null, hours = 1.0),
        )
        val limited = settings(TeamSettings(focusFactor = 1.0, wipLimit = 1))
        val sg = suggest(snapshot(base, pair, settings = limited)).suggestions.single()
        assertEquals(2L, sg.memberId)
        assertEquals(Decider.WIP_LIMIT, sg.reason.decidedBy)
        assertEquals(false, sg.reason.overWipLimit)
        assertEquals(1, sg.reason.wipLimit)
        // Sans limite (0), la charge la plus faible l'emporte.
        val unlimited = settings(TeamSettings(focusFactor = 1.0, wipLimit = 0))
        val free = suggest(snapshot(base, pair, settings = unlimited)).suggestions.single()
        assertEquals(1L, free.memberId)
        assertEquals(Decider.LOAD, free.reason.decidedBy)
    }

    @Test
    fun whenEveryoneIsOverTheLimitTheReasonSaysSo() {
        val base = listOf(
            task(10, 1, hours = 1.0, startedOn = monday), task(11, 1, hours = 1.0, startedOn = monday),
            task(12, 2, hours = 1.0, startedOn = monday), task(13, 2, hours = 1.0, startedOn = monday),
            task(1, null, hours = 1.0),
        )
        val limited = settings(TeamSettings(focusFactor = 1.0, wipLimit = 1))
        val sg = suggest(snapshot(base, pair, settings = limited)).suggestions.single()
        assertTrue(sg.reason.overWipLimit)
    }

    @Test
    fun toQualifyTasksAndAssignedTasksAreExcluded() {
        val s = snapshot(
            listOf(task(1, null), task(2, null, priority = null), task(3, null, points = null, hours = 2.0), task(4, 1), task(5, null, status = TaskStatus.DONE)),
            pair,
        )
        val r = suggest(s)
        assertEquals(listOf(1L), r.suggestions.map { it.taskId })
        assertEquals(listOf(2L, 3L), r.toQualify)
    }

    @Test
    fun aSelectionRestrictsTheTasks() {
        val s = snapshot((1L..5L).map { task(it, null) }, pair)
        val r = suggest(s, ids = setOf(2L, 4L, 99L, 2L))
        assertEquals(listOf(2L, 4L), r.suggestions.map { it.taskId })
        // Une sélection de tâches à qualifier les signale.
        assertEquals(listOf(7L), suggest(snapshot(listOf(task(7, null, priority = null)), pair), ids = setOf(7L)).toQualify)
    }

    @Test
    fun tasksAreTakenInScoreOrder() {
        val s = snapshot(listOf(task(1, null, priority = 2), task(2, null, priority = 0), task(3, null, priority = 1)), pair)
        assertEquals(listOf(2L, 3L, 1L), suggest(s).suggestions.map { it.taskId })
    }

    @Test
    fun aBlockerIsSuggestedBeforeAndGivesTheBlockedTaskItsDate() {
        // 2 (P0) est bloquée par 1 (P2) : 1 hérite de l'urgence de 2 et passe avant.
        val s = snapshot(
            listOf(task(1, null, priority = 2), task(2, null, priority = 0)),
            listOf(member(1)),
            deps = listOf(TeamFixtures.dep(blocked = 2, blocker = 1)),
        )
        val r = suggest(s)
        assertEquals(listOf(1L, 2L), r.suggestions.map { it.taskId })
        assertEquals(day(0), r.suggestions[0].plannedEnd)
        assertEquals(day(1), r.suggestions[1].plannedEnd)
    }

    @Test
    fun aTaskBlockedByAnUnsuggestedBacklogTaskHasNoDate() {
        val s = snapshot(
            listOf(task(1, null, priority = null), task(2, null)),
            listOf(member(1)),
            deps = listOf(TeamFixtures.dep(blocked = 2, blocker = 1)),
        )
        val sg = suggest(s).suggestions.single()
        assertEquals(2L, sg.taskId)
        assertEquals(null, sg.plannedEnd)
    }

    @Test
    fun theHorizonSelectsWhoIsAvailable() {
        // Absent une semaine : disponible sur 2 semaines, pas sur 1.
        val s = snapshot(listOf(task(1, null)), listOf(member(1)), absences = listOf(absence(1, 1, monday, day(6))))
        assertEquals(emptyList(), suggest(s, weeks = 1).suggestions)
        assertEquals(1, suggest(s, weeks = 2).suggestions.size)
    }
}
