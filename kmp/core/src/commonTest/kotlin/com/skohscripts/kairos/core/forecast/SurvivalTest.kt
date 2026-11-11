package com.skohscripts.kairos.core.forecast

import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Ce qui finit par se faire (docs/spec/previsions-taches.md § Étape E). */
class SurvivalTest {
    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private val zone = TimeZone.UTC
    private var next = 1L

    private fun done(afterDays: Int, priority: Int? = 2) =
        Task(next++, "Faite", status = TaskStatus.DONE, priority = priority, createdAt = now - 400.days, updatedAt = now - 400.days + afterDays.days)
    private fun open(ageDays: Int, priority: Int? = 2) = Task(next++, "Ouverte", priority = priority, createdAt = now - ageDays.days, updatedAt = now)
    private fun model(tasks: List<Task>, sessions: List<WorkSession> = emptyList()) = Survival.prepare(tasks, sessions, now, zone)

    @Test
    fun openTasksCountAsNotYetDoneInsteadOfBeingIgnored() {
        // 20 tâches faites en 5 jours, 20 ouvertes depuis 200 jours. Une tâche ouverte depuis 30 jours : aucune de celles
        // encore ouvertes à cet âge n'a jamais été faite. Ignorer les ouvertes dirait « tout se fait en 5 jours ».
        val target = open(30)
        val out = assertNotNull(model(List(20) { done(5) } + List(20) { open(200) } + target).of(target))
        assertEquals(Survival.Outlook(ageDays = 30, donePerTen = 0, atRisk = 21, samePriority = true), out)
    }

    @Test
    fun theShareIsTheKaplanMeierRatioOverTheNextNinetyDays() {
        // 40 tâches : 10 faites à 10 j, 10 à 60 j, 10 à 100 j, 10 ouvertes depuis 300 j. Ouverte depuis 20 j : 30 encore
        // ouvertes à cet âge (plus elle), dont 20 faites avant 110 j → S(110) / S(20) = (10/40) / (30/40) = 1/3, soit 6 sur 10.
        val target = open(20)
        val tasks = List(10) { done(10) } + List(10) { done(60) } + List(10) { done(100) } + List(10) { open(300) } + target
        val out = model(tasks).of(target)!!
        assertEquals(6, out.donePerTen)
        assertEquals(31, out.atRisk)
    }

    @Test
    fun aSmallPriorityGroupFallsBackToAllTasksAndTooFewAtRiskSaysNothing() {
        val target = open(20, priority = 0)
        val tasks = List(10) { done(10) } + List(10) { done(60) } + List(10) { done(100) } + List(10) { open(300) } + target
        val out = model(tasks).of(target)!!
        assertEquals(false, out.samePriority)
        assertEquals(6, out.donePerTen)
        // Ouverte depuis 350 jours : plus personne n'a cet âge.
        val old = open(350)
        assertNull(model(tasks + old).of(old))
    }

    @Test
    fun theLastSessionDatesTheEndRatherThanALaterEdit() {
        // Faites au bout de 5 jours (dernière session), mais retouchées 95 jours plus tard : leur vie reste de 5 jours.
        val edited = List(30) { done(100) }
        val sessions = edited.map { WorkSession(it.id, it.id, it.createdAt + 5.days, it.createdAt + 5.days + 1.hours, it.createdAt) }
        val target = open(30)
        val waiting = List(15) { open(200) }
        assertEquals(0, model(edited + waiting + target, sessions).of(target)!!.donePerTen)
        // Sans les sessions, la date de modification ferait croire qu'elles ont été faites à 100 jours, donc après 30 jours d'attente.
        assertEquals(10, model(edited + List(15) { open(40) } + target).of(target)!!.donePerTen)
    }

    @Test
    fun recurringTeamAndDoneTasksAreOutOfScope() {
        val base = List(20) { done(5) } + List(20) { open(200) }
        val recurring = open(30).copy(recurrence = TaskRecurrence.entries.first { it != TaskRecurrence.NONE })
        val team = open(30).copy(space = TaskSpace.TEAM)
        val m = model(base + recurring + team)
        assertNull(m.of(recurring))
        assertNull(m.of(team))
        assertNull(m.of(base.first()))
    }
}
