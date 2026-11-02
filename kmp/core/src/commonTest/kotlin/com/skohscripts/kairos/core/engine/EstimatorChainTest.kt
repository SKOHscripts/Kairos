package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Instant

/** Interface extraite et chaîne de repli (docs/spec/estimateur-modele.md § Interface extraite et chaîne de repli). */
class EstimatorChainTest {
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")
    private val settings = Settings()

    private fun task(id: Long = 100, space: TaskSpace = TaskSpace.PERSONAL) =
        Task(id, "Rapport", space = space, createdAt = t0, updatedAt = t0)

    private fun sample(id: Long, space: TaskSpace = TaskSpace.PERSONAL) =
        DurationEstimator.Sample(id, null, "Rapport", space, 30)

    /** Un niveau qui rend toujours [answer] et retient l'historique qu'il a reçu. */
    private class Fixed(val answer: DurationEstimator.Estimate?) : DurationEstimator {
        var seen: List<DurationEstimator.Sample>? = null
        override fun estimate(task: Task, history: DurationEstimator.History, settings: Settings): DurationEstimator.Estimate? {
            seen = history.samples
            return answer
        }
    }

    private fun e(minutes: Int, reliable: Boolean, source: DurationEstimator.Source = DurationEstimator.Source.TITLE) =
        DurationEstimator.Estimate(minutes, source, 3, reliable)

    @Test
    fun theFirstReliableLevelWinsOtherwiseTheFirstWeakOne() {
        val weakSeries = e(40, reliable = false, DurationEstimator.Source.SERIES)
        val reliableTitle = e(50, reliable = true)
        assertSame(reliableTitle, EstimatorChain(listOf(Fixed(weakSeries), Fixed(reliableTitle))).estimate(task(), DurationEstimator.History(emptyList()), settings))
        val weakTitle = e(60, reliable = false)
        assertSame(weakSeries, EstimatorChain(listOf(Fixed(weakSeries), Fixed(weakTitle))).estimate(task(), DurationEstimator.History(emptyList()), settings))
        assertSame(weakTitle, EstimatorChain(listOf(Fixed(null), Fixed(weakTitle))).estimate(task(), DurationEstimator.History(emptyList()), settings))
        assertNull(EstimatorChain(listOf(Fixed(null), Fixed(null))).estimate(task(), DurationEstimator.History(emptyList()), settings))
    }

    @Test
    fun levelsOnlySeeTheSameSpaceWithoutTheTaskItself() {
        val level = Fixed(null)
        val history = DurationEstimator.History(listOf(sample(1), sample(2, TaskSpace.TEAM), sample(100)))
        EstimatorChain(listOf(level)).estimate(task(), history, settings)
        assertEquals(listOf(1L), level.seen!!.map { it.taskId })
    }

    @Test
    fun theCommonGuardsComeBeforeAnyLevel() {
        val level = Fixed(e(50, reliable = true))
        val chain = EstimatorChain(listOf(level))
        val none = DurationEstimator.History(emptyList())
        assertNull(chain.estimate(task(), none, Settings(learning = LearningSettings(enabled = false))))
        assertNull(chain.estimate(task().copy(estimatedMinutes = 20), none, settings))
        assertNull(chain.estimate(task().copy(origin = "manager-1"), none, settings))
        assertNull(level.seen, "aucun niveau consulté")
    }

    @Test
    fun samplesCarryTheFeaturesTheModelCompares() {
        val done = Task(
            7, "Revue de code", status = TaskStatus.DONE, taskType = "Dev", fibonacciPoints = 5, priority = 1,
            projectTag = "kairos", recurrence = TaskRecurrence.WEEKLY, createdAt = t0, updatedAt = t0,
        )
        val s = DurationEstimator.samples(listOf(done), mapOf(7L to 45)).single()
        assertEquals(DurationEstimator.Sample(7, null, "Revue de code", TaskSpace.PERSONAL, 45, "Dev", 5, 1, "kairos", recurring = true), s)
    }
}
