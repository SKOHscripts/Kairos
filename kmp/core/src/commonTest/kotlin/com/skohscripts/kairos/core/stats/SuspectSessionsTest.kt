package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Chronos à vérifier (docs/spec/previsions-taches.md § Étape G). */
class SuspectSessionsTest {
    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private fun task(id: Long, title: String = "Courrier", estimated: Int? = null, status: TaskStatus = TaskStatus.DONE, space: TaskSpace = TaskSpace.PERSONAL, origin: String? = null) =
        Task(id, title, status = status, estimatedMinutes = estimated, space = space, origin = origin, createdAt = now, updatedAt = now)
    private fun session(id: Long, taskId: Long, minutes: Int?, daysAgo: Int = 1): WorkSession {
        val start = now - daysAgo.days
        return WorkSession(id, taskId, start, minutes?.let { start + it.minutes }, start)
    }
    private fun find(tasks: List<Task>, sessions: List<WorkSession>) = SuspectSessions.find(KairosSnapshot(tasks = tasks, workSessions = sessions), now)

    @Test
    fun aSessionFarBeyondTheTypedEstimateIsProposedAndBroughtBackToIt() {
        // Estimée 30 min, chronométrée 6 h : au-delà de 3 × 30 min et de plus d'une heure.
        val found = find(listOf(task(1, estimated = 30)), listOf(session(1, 1, 360)))
        assertEquals(1, found.size)
        val s = found.single()
        assertEquals(listOf(360, 30), listOf(s.minutes, s.usualMinutes))
        assertEquals(s.session.startedAt + 30.minutes, s.proposedEnd)
    }

    @Test
    fun aLongButPlausibleSessionIsLeftAlone() {
        // 80 min pour 30 estimées : moins de 3 fois. 40 min pour 10 : 4 fois, mais 30 min d'écart seulement.
        assertEquals(emptyList(), find(listOf(task(1, estimated = 30), task(2, estimated = 10)), listOf(session(1, 1, 80), session(2, 2, 40))))
    }

    @Test
    fun withoutATypedEstimateTheOtherOccurrencesSayWhatIsUsualNeverTheTaskItself() {
        // Quatre « Courrier » de 20 à 30 min, et un cinquième de 8 h : son propre temps ne compte pas dans son habitude.
        val tasks = (1L..5L).map { task(it) }
        val sessions = listOf(session(1, 1, 20), session(2, 2, 25), session(3, 3, 30), session(4, 4, 25), session(5, 5, 480))
        val found = find(tasks, sessions)
        assertEquals(listOf(5L), found.map { it.task.id })
        assertEquals(true, found.single().usualMinutes in 25..45, "haut de la fourchette apprise : ${found.single().usualMinutes}")
        // Une tâche au titre unique, sans estimation : on ne sait pas ce qui est habituel, rien n'est proposé.
        assertEquals(emptyList(), find(listOf(task(9, title = "Unique")), listOf(session(9, 9, 480))))
    }

    @Test
    fun runningTeamAndReceivedSessionsAreNeverProposedAndTheMostRecentComeFirst() {
        val tasks = listOf(task(1, estimated = 30), task(2, estimated = 30, space = TaskSpace.TEAM), task(3, estimated = 30, origin = "manager"), task(4, estimated = 30, status = TaskStatus.TODO))
        val sessions = listOf(session(1, 1, 300, daysAgo = 9), session(2, 2, 300), session(3, 3, 300), session(4, 4, null), session(5, 4, 300, daysAgo = 2))
        assertEquals(listOf(5L, 1L), find(tasks, sessions).map { it.session.id })
    }
}
