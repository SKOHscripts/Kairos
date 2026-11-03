package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Reproductible au bit près (docs/spec/estimateur-modele.md § Déterminisme) : des valeurs en dur dans `commonTest`, sur des
 * titres accentués (« É », « ß », « Ё », « ñ »), couvrent l'index (idf), une ressemblance, une médiane pondérée et son
 * intervalle, la régression et l'empreinte ; toute divergence entre JVM, Android et wasm les fait échouer.
 */
class ModelReproducibilityTest {
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")
    private val history = listOf(
        DurationEstimator.Sample(1, null, "Rédiger le rapport trimestriel", TaskSpace.PERSONAL, 50, "Rédaction", 5),
        DurationEstimator.Sample(2, null, "Rapport mensuel", TaskSpace.PERSONAL, 40, "Rédaction", 3),
        DurationEstimator.Sample(3, null, "Straße Ёлка niño — Été", TaskSpace.PERSONAL, 25, "Admin", 2),
        DurationEstimator.Sample(4, null, "Revue de code module paie", TaskSpace.PERSONAL, 35, "Revue", 3),
        DurationEstimator.Sample(5, null, "Rapports annuels", TaskSpace.PERSONAL, 65, "Rédaction", 8),
        DurationEstimator.Sample(6, null, "Relire la note", TaskSpace.PERSONAL, 20, "Revue", 2),
    )

    /** Une tâche accentuée et majuscule, comparée au corpus ci-dessus. */
    private val title = "Rédiger un rapport — ÉTÉ"

    @Test
    fun theIndexAndTheSimilarityAreFixedToTheBit() {
        val index = ModelIndex.build(history)
        val q = index.query(title)
        assertEquals(0.5227933411371939, index.text(q, 0))
        assertEquals(0.20512554372510378, index.text(q, 2), "« Été » rejoint « ÉTÉ » par le repli des accents")
        val target = Similarity.Target(q, "Rédaction", 5, "")
        assertEquals(0.7348851895206634, Similarity.of(target, index, 0))
        assertEquals(0.475511878067807, Similarity.of(target, index, 4))
        assertEquals(-3881057229422056706L, ModelIndex.fingerprint(history))
        assertEquals(ModelIndex.fingerprint(history), ModelIndex.fingerprint(history.reversed()))
    }

    @Test
    fun theWeightedMedianAndItsIntervalAreFixed() {
        val task = Task(100, title, taskType = "Rédaction", fibonacciPoints = 5, createdAt = t0, updatedAt = t0)
        val e = ModelEstimator.estimate(task, DurationEstimator.History(history), Settings())!!
        assertEquals(50, e.minutes)
        assertEquals(35, e.low)
        assertEquals(75, e.high)
        assertEquals(4, e.samples)
        assertEquals(listOf(1L to 73, 2L to 56, 5L to 48, 3L to 26), e.evidence!!.neighbours.map { it.taskId to it.similarityPercent })
    }

    @Test
    fun theRegressionIsFixedToTheBit() {
        val ridgeHistory = (1L..24L).map {
            DurationEstimator.Sample(
                it, null, "t$it", TaskSpace.PERSONAL, 20 + (it * 13 % 50).toInt(), if (it % 2 == 0L) "A" else "B",
                listOf(1, 2, 3, 5, 8)[(it % 5).toInt()], (it % 3).toInt(),
            )
        }
        val p = RidgePrior.fit(ridgeHistory)!!.predict("A", 5, 1, false)!!
        assertEquals(6.150766603871855, p.root)
        assertEquals(21.09826721770009, p.low)
        assertEquals(58.252999085573165, p.high)
    }
}
