package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.DurationEstimator.Basis
import com.skohscripts.kairos.core.engine.DurationEstimator.Source
import com.skohscripts.kairos.core.engine.EstimatorChain
import com.skohscripts.kairos.core.engine.SeriesEstimator
import com.skohscripts.kairos.core.engine.TitleEstimator
import com.skohscripts.kairos.core.engine.model.SimulatedHistory.Profile
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Le niveau modèle (docs/spec/estimateur-modele.md § Le modèle). */
class ModelEstimatorTest {
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")
    private val settings = Settings()

    private fun task(title: String, type: String = "", points: Int? = null, space: TaskSpace = TaskSpace.PERSONAL) =
        Task(1000, title, taskType = type, fibonacciPoints = points, space = space, createdAt = t0, updatedAt = t0)

    private fun sample(id: Long, title: String, minutes: Int, type: String = "", points: Int? = null, space: TaskSpace = TaskSpace.PERSONAL) =
        DurationEstimator.Sample(id, null, title, space, minutes, taskType = type, points = points)

    @Test
    fun everyEstimateCarriesItsEvidence() {
        for (profile in listOf(Profile.NOVEL, Profile.RECURRING, Profile.NOISE)) {
            val history = SimulatedHistory.generate(3, profile, n = 120)
            val bt = Backtest(history)
            for (s in history.takeLast(15)) {
                val e = ModelEstimator.estimate(bt.task(s).copy(id = 5000 + s.taskId), DurationEstimator.History(history), settings) ?: continue
                assertEquals(Source.MODEL, e.source)
                val evidence = assertNotNull(e.evidence)
                when (evidence.basis) {
                    Basis.NEIGHBOURS -> assertTrue(evidence.neighbours.isNotEmpty())
                    Basis.REGRESSION -> assertTrue(evidence.taskType != null || evidence.points != null)
                }
                assertTrue(e.low!! <= e.minutes && e.minutes <= e.high!!, "$e")
            }
        }
    }

    @Test
    fun similarTitlesGiveTheirWeightedMedianWithTheClosestFirst() {
        val history = listOf(
            sample(1, "Rapport trimestriel", 50), sample(2, "Rapport mensuel", 40), sample(3, "Rapport T3", 45),
            sample(4, "Rapports annuels", 55), sample(5, "Arroser les plantes", 5),
        )
        val e = assertNotNull(ModelEstimator.estimate(task("Rédiger le rapport trimestriel"), DurationEstimator.History(history), settings))
        val evidence = e.evidence!!
        assertEquals(Basis.NEIGHBOURS, evidence.basis)
        assertEquals("Rapport trimestriel", evidence.neighbours.first().title)
        assertTrue(evidence.neighbours.none { it.title == "Arroser les plantes" })
        assertTrue(e.minutes in 40..55, "$e")
        // Corpus de 5 tâches : affichée, jamais appliquée.
        assertEquals(false, e.reliable)
    }

    @Test
    fun withoutNeighboursTheRegressionOnTypeAndPointsTakesOver() {
        // 30 revues titrées sans rapport avec la tâche, sans points ; la tâche a des points : ressemblance
        // (0,2 type + 0 points) / 0,9 < 0,25, aucun voisin, la régression sur le type répond.
        val history = (1L..30L).map { sample(it, "zq${it}x vbn${it * 7}", 40 + (it % 3).toInt() * 5, type = "Revue") }
        val e = assertNotNull(ModelEstimator.estimate(task("Relire le module de paie", type = "Revue", points = 5), DurationEstimator.History(history), settings))
        assertEquals(Basis.REGRESSION, e.evidence!!.basis)
        assertEquals("Revue", e.evidence!!.taskType)
        assertEquals(30, e.evidence!!.corpus)
        assertTrue(e.minutes in 35..50, "$e")
        // Ni type connu ni points : pas de régression, rien à dire.
        assertNull(ModelEstimator.estimate(task("Relire le module de paie"), DurationEstimator.History(history), settings))
    }

    @Test
    fun theSwitchAndTheLearningSettingTurnTheModelOff() {
        val history = SimulatedHistory.generate(1, Profile.NOVEL, n = 60)
        val t = Backtest(history).task(history.last()).copy(id = 9999)
        assertNotNull(ModelEstimator.estimate(t, DurationEstimator.History(history), settings))
        assertNull(ModelEstimator.estimate(t, DurationEstimator.History(history), Settings(learning = LearningSettings(model = false))))
        assertNull(ModelEstimator.estimate(t, DurationEstimator.History(history), Settings(learning = LearningSettings(enabled = false))))
    }

    @Test
    fun spacesNeverFeedEachOtherThroughTheChain() {
        val chain = EstimatorChain(listOf(SeriesEstimator, TitleEstimator, ModelEstimator))
        val team = (1L..40L).map { sample(it, "Rapport client $it", 90, type = "Rédaction", space = TaskSpace.TEAM) }
        assertNull(chain.estimate(task("Rapport client", type = "Rédaction"), DurationEstimator.History(team), settings))
        val e = assertNotNull(chain.estimate(task("Rapport client", type = "Rédaction", space = TaskSpace.TEAM), DurationEstimator.History(team), settings))
        assertEquals(90, e.minutes)
    }

    @Test
    fun theOrderOfTheHistoryChangesNothing() {
        val history = SimulatedHistory.generate(5, Profile.NOVEL, n = 90)
        val t = Backtest(history).task(history[40]).copy(id = 777)
        val a = ModelEstimator.estimate(t, DurationEstimator.History(history), settings)
        val b = ModelEstimator.estimate(t, DurationEstimator.History(history.reversed()), settings)
        val c = ModelEstimator.estimate(t, DurationEstimator.History(history.shuffled(kotlin.random.Random(3))), settings)
        assertEquals(a, b)
        assertEquals(a, c)
    }

    @Test
    fun theCacheGivesTheSameResultAndIsKeptPerSpace() {
        val cache = ModelCache.memo()
        val personal = SimulatedHistory.generate(2, Profile.NOVEL, n = 60)
        val team = personal.map { it.copy(taskId = it.taskId + 10_000, space = TaskSpace.TEAM) }
        val t = Backtest(personal).task(personal.last()).copy(id = 9999)
        val plain = ModelEstimator.estimate(t, DurationEstimator.History(personal), settings)
        repeat(2) {
            assertEquals(plain, ModelEstimator.estimate(t, DurationEstimator.History(personal, cache), settings))
            ModelEstimator.estimate(t.copy(space = TaskSpace.TEAM), DurationEstimator.History(team, cache), settings)
        }
        // Un réglage que lit la garde change la clé : la garde est recalculée.
        val other = Settings(defaultTaskDurationMinutes = 45)
        assertEquals(ModelEstimator.estimate(t, DurationEstimator.History(personal), other), ModelEstimator.estimate(t, DurationEstimator.History(personal, cache), other))
    }
}
