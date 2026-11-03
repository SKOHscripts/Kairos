package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.model.SimulatedHistory.Profile
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Passe de robustesse (docs/spec/estimateur-modele.md § Décisions, relectures du 2026-11-03). */
class RobustnessTest {
    private val now = Instant.parse("2026-09-28T07:00:00Z")

    /** Un cache qui compte les constructions du modèle. */
    private class Counting : ModelCache {
        val inner = ModelCache.memo()
        var builds = 0
        override fun prepare(samples: List<DurationEstimator.Sample>) = inner.prepare(samples)
        override fun state(prepared: ModelCache.Prepared, settings: Settings, build: () -> ModelEstimator.State) =
            inner.state(prepared, settings) { builds++; build() }
    }

    @Test
    fun theCacheBuildsOncePerSpaceAndHistoryAndRebuildsWhenTheHistoryChanges() {
        val history = SimulatedHistory.generate(2, Profile.NOVEL, n = 60)
        val bt = Backtest(history)
        val cache = Counting()
        val tasks = history.takeLast(20).map { bt.task(it).copy(id = 10_000 + it.taskId) }
        repeat(3) { for (t in tasks) ModelEstimator.estimate(t, DurationEstimator.History(history, cache), Settings()) }
        assertEquals(1, cache.builds, "un seul modèle pour 60 estimations sur le même historique")
        val team = history.map { it.copy(taskId = it.taskId + 50_000, space = TaskSpace.TEAM) }
        ModelEstimator.estimate(tasks[0].copy(space = TaskSpace.TEAM), DurationEstimator.History(team, cache), Settings())
        ModelEstimator.estimate(tasks[0], DurationEstimator.History(history, cache), Settings())
        assertEquals(2, cache.builds, "une case par espace : le perso n'est pas chassé par l'équipe")
        ModelEstimator.estimate(tasks[0], DurationEstimator.History(history.dropLast(1), cache), Settings())
        assertEquals(3, cache.builds, "un historique changé reconstruit le modèle")
    }

    @Test
    fun manyRequiredSamplesNeverSilenceTheModel() {
        // minSamples = 8 ≥ NEIGHBOURS_MAX : sans plafond, aucune base de voisins ne serait suffisante.
        val history = SimulatedHistory.generate(5, Profile.NOVEL, n = 120)
        val t = Backtest(history).task(history.last()).copy(id = 9999)
        val settings = Settings(learning = LearningSettings(history = 10, minSamples = 8))
        val e = assertNotNull(ModelEstimator.estimate(t, DurationEstimator.History(history), settings))
        assertEquals(DurationEstimator.Basis.NEIGHBOURS, e.evidence!!.basis)
        assertTrue(e.reliable, "garde ${e.evidence!!.gate}")
    }

    @Test
    fun theMostFrequentTypesGetARegressionVariable() {
        // Huit types rares en tête d'alphabet (3 tâches chacun), un type lourd en fin (« Zeta », 20 tâches).
        val rare = ('A'..'H').flatMapIndexed { k, c -> (0 until 3).map { i -> sample(100L + k * 10 + i, "$c-type", 30) } }
        val heavy = (1L..20L).map { sample(it, "Zeta", 90) }
        val ridge = assertNotNull(RidgePrior.fit((rare + heavy).sortedBy { it.taskId }))
        assertEquals("zeta", ridge.knownType("Zeta"))
    }

    private fun sample(id: Long, type: String, minutes: Int) =
        DurationEstimator.Sample(id, null, "t$id", TaskSpace.PERSONAL, minutes, taskType = type)

    @Test
    fun aDecomposedAccentDoesNotSplitTheWord() {
        assertEquals(listOf("rediger"), TextFeatures.words("Rédiger"))
        assertEquals(TextFeatures.of("Rédiger"), TextFeatures.of("Rédiger"))
    }

    @Test
    fun theRhythmProductStaysWithinItsBounds() {
        val applied = Rhythm.applied(Rhythm.Factors(mapOf(1 to 2.0), mapOf(Rhythm.Band.MORNING to 2.0)), 1, Rhythm.Band.MORNING)
        assertEquals(2.0, applied.factor)
        assertEquals(0.5, Rhythm.applied(Rhythm.Factors(mapOf(1 to 0.5), mapOf(Rhythm.Band.EVENING to 0.5)), 1, Rhythm.Band.EVENING).factor)
    }

    @Test
    fun theGateSortsAnUnsortedCorpusItself() {
        val history = SimulatedHistory.generate(6, Profile.NOVEL, n = 80)
        assertEquals(ModelGate.evaluate(history, Settings()), ModelGate.evaluate(history.reversed(), Settings()))
    }

    @Test
    fun theFacadeShareOneCorpusPerSpaceForAllTasks() {
        // 30 tâches à faire sans estimation sur un même historique : la façade ne construit qu'un modèle (cache partagé).
        val done = SimulatedHistory.generate(8, Profile.NOVEL, n = 60).map {
            Task(it.taskId, it.title, status = TaskStatus.DONE, taskType = it.taskType, fibonacciPoints = it.points, manualTimeSpentMinutes = it.minutes, createdAt = now, updatedAt = now)
        }
        val open = (1L..30L).map { Task(1000 + it, "rapport bilan x$it", taskType = "Rédaction", fibonacciPoints = 3, createdAt = now, updatedAt = now) }
        val cache = Counting()
        val snapshot = KairosSnapshot(tasks = done + open)
        val past = DurationEstimator.samples(snapshot.tasks, done.associate { it.id to (it.manualTimeSpentMinutes ?: 0) })
        for (t in open) DurationEstimator.estimate(t, past, snapshot.settings, cache)
        assertEquals(1, cache.builds)
    }
}
