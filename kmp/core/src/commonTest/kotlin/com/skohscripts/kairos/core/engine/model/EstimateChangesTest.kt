package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.model.EstimateChanges.Change
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Estimations qui changent et estimations saisies à revoir (docs/spec/estimateur-modele.md, besoins 12 et 13). */
class EstimateChangesTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")

    @Test
    fun aChangeIsSignificantFromFifteenMinutesOrAQuarter() {
        assertTrue(EstimateChanges.significant(60, 75))
        assertTrue(EstimateChanges.significant(20, 25))
        assertFalse(EstimateChanges.significant(60, 70))
        assertFalse(EstimateChanges.significant(45, 45))
        assertTrue(EstimateChanges.significant(120, 90))
    }

    @Test
    fun theDiffKeepsTasksInBothReadingsThatMovedNetly() {
        val before = mapOf(3L to 60, 1L to 30, 2L to 45, 9L to 20)
        val after = mapOf(1L to 45, 2L to 50, 3L to 30, 4L to 90)
        assertEquals(listOf(Change(1, 30, 45), Change(3, 60, 30)), EstimateChanges.diff(before, after))
    }

    /** 40 rapports « Writing » à 3 points faits en 45 min, et une tâche à faire de même allure saisie à [typed] minutes. */
    private fun snapshot(typed: Int?, learning: LearningSettings? = null, seriesId: Long? = null): KairosSnapshot {
        val words = listOf("apple", "birch", "cedar", "dune", "ember", "fjord", "grove", "heath", "inlet", "jade")
        val done = (1L..40L).map { i ->
            if (i % 2 == 1L) {
                Task(i, "Quarterly report ${words[(i % 10).toInt()]}$i", status = TaskStatus.DONE, fibonacciPoints = 3, taskType = "Writing", manualTimeSpentMinutes = 45, createdAt = now, updatedAt = now)
            } else {
                Task(i, "Mail to the bank ${words[(i % 10).toInt()]}$i", status = TaskStatus.DONE, fibonacciPoints = 1, taskType = "Admin", manualTimeSpentMinutes = 10, createdAt = now, updatedAt = now)
            }
        }
        val open = Task(100, "Quarterly report for the board", fibonacciPoints = 3, taskType = "Writing", estimatedMinutes = typed, seriesId = seriesId, createdAt = now, updatedAt = now)
        return KairosSnapshot(tasks = done + open, settings = Settings(learning = learning))
    }

    @Test
    fun aTypedEstimateFarFromTheModelIsProposedNeverAClosedOne() {
        val far = EstimateChanges.proposals(snapshot(typed = 120), now, null)
        assertEquals(1, far.size)
        assertEquals(120, far.single().typed)
        assertEquals(45, far.single().estimate.minutes)
        assertEquals(far.single(), EstimateChanges.proposalFor(snapshot(typed = 120).tasks.last(), snapshot(typed = 120), now, null))
        // Proche du modèle, ou sans durée saisie : rien à proposer.
        assertEquals(emptyList(), EstimateChanges.proposals(snapshot(typed = 50), now, null))
        assertEquals(emptyList(), EstimateChanges.proposals(snapshot(typed = null), now, null))
        // Modèle coupé : rien.
        assertEquals(emptyList(), EstimateChanges.proposals(snapshot(typed = 120, learning = LearningSettings(model = false)), now, null))
    }

    @Test
    fun theAppliedReadingFollowsTheSwitch() {
        assertEquals(mapOf(100L to 45), EstimateChanges.applied(snapshot(typed = null), now, null))
        assertEquals(emptyMap(), EstimateChanges.applied(snapshot(typed = null, learning = LearningSettings(notifyChanges = false)), now, null))
        assertNull(EstimateChanges.proposalFor(snapshot(typed = 120).tasks.first(), snapshot(typed = 120), now, null), "une tâche faite n'est jamais proposée")
    }

    @Test
    fun aTeamTaskIsNeverProposedInThePersonalSpace() {
        val base = snapshot(typed = 120)
        val team = base.copy(tasks = base.tasks.map { if (it.id == 100L) it.copy(space = com.skohscripts.kairos.core.model.TaskSpace.TEAM) else it })
        assertEquals(emptyList(), EstimateChanges.proposals(team, now, null))
        assertNull(EstimateChanges.proposalFor(team.tasks.last(), team, now, null))
    }

    @Test
    fun theHistoryKeyMovesWithFinishedTasksOnly() {
        val base = snapshot(typed = null)
        val key = EstimateChanges.historyKey(base, now)
        // Une tâche à faire retouchée, un réglage changé : même historique.
        val edited = base.copy(tasks = base.tasks.map { if (it.id == 100L) it.copy(title = "Something else", fibonacciPoints = 8) else it })
        assertEquals(key, EstimateChanges.historyKey(edited.copy(settings = Settings(defaultTaskDurationMinutes = 60)), now))
        // Une tâche faite de plus : l'historique a bougé.
        val more = base.copy(tasks = base.tasks + Task(200, "Quarterly report zeta", status = TaskStatus.DONE, fibonacciPoints = 3, taskType = "Writing", manualTimeSpentMinutes = 90, createdAt = now, updatedAt = now))
        assertTrue(key != EstimateChanges.historyKey(more, now))
    }

    @Test
    fun onlyChangesOfTasksStillToDoAreKept() {
        val changes = listOf(Change(1, 30, 60), Change(2, 30, 60), Change(3, 30, 60))
        val tasks = listOf(
            Task(1, "a", createdAt = now, updatedAt = now),
            Task(2, "b", status = TaskStatus.DONE, createdAt = now, updatedAt = now),
        )
        assertEquals(listOf(Change(1, 30, 60)), EstimateChanges.stillOpen(changes, tasks))
    }
}
