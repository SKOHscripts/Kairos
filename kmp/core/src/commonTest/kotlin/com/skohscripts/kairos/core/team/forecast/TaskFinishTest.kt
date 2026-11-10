package com.skohscripts.kairos.core.team.forecast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Date de fin par tâche (docs/spec/previsions-taches.md § Étape B). */
class TaskFinishTest {
    private val snap = ForecastFixtures.team(members = 3, tasksPerMember = 5, deadlines = true)
    private val data = ForecastData(errorFactors = listOf(0.6, 0.8, 0.9, 1.0, 1.0, 1.1, 1.3, 1.6, 2.0, 2.5))
    private val result = ForecastFixtures.run(snap, data, seed = 7L, runs = 1000)

    @Test
    fun everyTaskOfTheScopeHasAnOrderedRangeNoLaterThanTheScope() {
        val scopeP50 = result.finishDate(50)!!
        val scopeP85 = result.finishDate(85)!!
        var spread = 0
        for (task in snap.tasks) {
            val f = assertNotNull(result.taskFinish(task.id), "tâche ${task.id}")
            assertTrue(f.p50!! <= f.p85!!, "tâche ${task.id} : ${f.p50} après ${f.p85}")
            // La fin du périmètre est le maximum des fins : ses percentiles dominent ceux de chaque tâche.
            assertTrue(f.p50 <= scopeP50 && f.p85 <= scopeP85, "tâche ${task.id}")
            if (f.p50 < f.p85) spread++
        }
        assertTrue(spread > 0, "aucune fourchette : la propriété serait vide")
        assertTrue(snap.tasks.any { result.taskFinish(it.id)!!.p85 == scopeP85 }, "aucune tâche ne porte la fin du périmètre")
    }

    @Test
    fun theMarginAgreesWithTheDeadlineProbability() {
        assertTrue(result.deadlines.isNotEmpty())
        for (d in result.deadlines) {
            val f = result.taskFinish(d.taskId)!!
            // P85 ≤ échéance ⟺ au moins ⌈0,85 n⌉ tirages à l'heure.
            val onTime = d.draws - d.lateDraws >= Percentiles.rank(d.draws, 85)
            assertEquals(onTime, f.marginDays!! >= 0, "tâche ${d.taskId} : marge ${f.marginDays}, ${d.lateDraws} retards sur ${d.draws}")
        }
        assertNull(result.taskFinish(snap.tasks.first { it.deadline == null }.id)!!.marginDays)
    }

    @Test
    fun withoutRandomnessTheRangeIsTheDeterministicDate() {
        val exact = ForecastFixtures.run(snap, runs = 50)
        for (task in snap.tasks) exact.taskFinish(task.id)!!.let { assertEquals(it.p50, it.p85) }
    }

    @Test
    fun noDateOutsideTheScopeNorFromTheThroughputModel() {
        assertNull(result.taskFinish(-1L))
        val flow = ForecastData(teamThroughput = listOf(2, 0, 3, 1, 4, 2, 0, 3, 2, 1, 2, 3))
        assertNull(ForecastFixtures.run(snap, flow, model = ForecastModel.THROUGHPUT).taskFinish(snap.tasks.first().id))
    }

    @Test
    fun slicingTheRunDoesNotChangeTheDates() {
        val mc = MonteCarlo.prepare(ForecastFixtures.request(snap, data, seed = 7L, runs = 1000))
        val acc = mc.accumulator()
        for (from in 750 downTo 0 step 250) acc.add(mc.runBatch(from, 250))
        for (task in snap.tasks) assertEquals(result.taskFinish(task.id), acc.result().taskFinish(task.id))
    }
}
