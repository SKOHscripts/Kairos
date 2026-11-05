package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.stats.SplitAdvice.Reason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Conseil de découpage selon l'historique (docs/spec/sous-taches.md § Conseil de découpage, issue #201). */
class SplitAdviceTest {
    private val t0 = Instant.parse("2026-11-05T08:00:00Z")
    private var next = 1L

    private fun done(points: Int, actual: Int, estimate: Int? = null, space: TaskSpace = TaskSpace.PERSONAL) =
        Task(next++, "T", fibonacciPoints = points, estimatedMinutes = estimate, status = TaskStatus.DONE, space = space, createdAt = t0, updatedAt = t0) to actual

    private fun history(vararg samples: Pair<Task, Int>, space: TaskSpace = TaskSpace.PERSONAL) =
        SplitAdvice.history(samples.map { it.first }, samples.associate { it.first.id to it.second }, space)

    @Test
    fun theThresholdIsPersonal() {
        // Critère de succès de la spec : à 8 points, de 2 h à 9 h ; à 5 points, de 1 h à 1 h 30.
        val h = history(
            done(8, 120), done(8, 200), done(8, 300), done(8, 420), done(8, 540),
            done(5, 60), done(5, 70), done(5, 75), done(5, 80), done(5, 90),
        )
        val eight = SplitAdvice.advise(8, false, h)!!
        assertEquals(Reason.SPREAD, eight.reason)
        assertEquals(120 to 420, eight.shortest to eight.longest) // 20ᵉ et 80ᵉ centiles : ×3,5
        assertNull(SplitAdvice.advise(5, false, h))
    }

    @Test
    fun aBiasOutsideTheRangeAdvisesToSplit() {
        val h = history(*Array(5) { done(5, 120, estimate = 60) })
        val a = SplitAdvice.advise(5, false, h)!!
        assertEquals(Reason.BIAS, a.reason)
        assertEquals(2.0, a.bias)
    }

    @Test
    fun tooFewFinishedTasksAdviseToSplitButNeverAtOneOrTwoPoints() {
        val h = history(done(13, 600), done(13, 620))
        assertEquals(SplitAdvice.Advice(Reason.NOT_ENOUGH_HISTORY, 13, 2), SplitAdvice.advise(13, false, h))
        assertNull(SplitAdvice.advise(2, false, h))
        assertNull(SplitAdvice.advise(1, false, h))
        assertNull(SplitAdvice.advise(null, false, h))
        assertNull(SplitAdvice.advise(13, hasSubtasks = true, history = h)) // déjà découpée
    }

    @Test
    fun theHistoryIsTheSpaceOfTheTask() {
        val team = Array(5) { done(5, 60, space = TaskSpace.TEAM) }
        assertEquals(Reason.NOT_ENOUGH_HISTORY, SplitAdvice.advise(5, false, history(*team))?.reason)
        assertNull(SplitAdvice.advise(5, false, history(*team, space = TaskSpace.TEAM)))
    }
}
