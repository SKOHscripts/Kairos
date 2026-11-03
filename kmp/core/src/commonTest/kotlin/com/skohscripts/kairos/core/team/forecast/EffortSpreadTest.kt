package com.skohscripts.kairos.core.team.forecast

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.team.TeamFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** L'intervalle du modèle comme loi d'effort des prévisions (docs/spec/estimateur-modele.md § Prévisions). */
class EffortSpreadTest {
    @Test
    fun theQuantileFunctionGoesThroughItsPointsWithinTheTeamBounds() {
        assertEquals(1.0, EffortSpread.factor(0.37, 1.0, 1.0))
        assertEquals(1.0, EffortSpread.factor(0.0, 1.0, 1.0))
        assertEquals(1.0, EffortSpread.factor(0.999, 1.0, 1.0))
        val (l, h) = EffortSpread.ratios(60, 30, 120)
        assertEquals(0.5, l)
        assertEquals(2.0, h)
        assertEquals(l, EffortSpread.factor(0.10, l, h))
        assertEquals(1.0, EffortSpread.factor(0.50, l, h))
        assertEquals(h, EffortSpread.factor(0.90, l, h))
        assertEquals(0.25, EffortSpread.factor(0.0, l, h))
        assertTrue(EffortSpread.factor(0.9999, l, h) <= 2.5)
        var previous = 0.0
        for (i in 0..1000) {
            val f = EffortSpread.factor(i / 1000.0, l, h)
            assertTrue(f >= previous && f >= EffortSpread.MIN_FACTOR && f <= EffortSpread.MAX_FACTOR, "u = ${i / 1000.0}")
            previous = f
        }
        // Rapports bornés : une borne basse au-dessus de la durée ou une borne haute en dessous ne retournent pas la loi.
        assertEquals(1.0 to 1.0, EffortSpread.ratios(60, 90, 30))
        assertEquals(1.0 to 1.0, EffortSpread.ratios(0, 10, 20))
    }

    /**
     * Un membre, 30 tâches d'équipe faites (rapports de 3 à 7 h, temps rapporté ; une durée apprise ne dépasse jamais la fenêtre de travail), et dans le périmètre une tâche sans durée
     * dont le titre ressemble aux rapports : le modèle l'estime, et sa fourchette élargit la date de fin.
     */
    private fun team(model: Boolean? = null): KairosSnapshot {
        val base = ForecastFixtures.team(1, 1)
        val done = (100L..129L).map {
            TeamFixtures.task(it, assignee = 1, hours = null, points = 3, type = "Rédaction", status = TaskStatus.DONE)
                .copy(title = "Rapport client ${'a' + (it - 100).toInt()}", reportedMinutes = listOf(180, 300, 420)[(it % 3).toInt()], teamUid = "uid-$it")
        }
        val open = ForecastFixtures.task(200, assignee = 1, hours = null, points = 3, type = "Rédaction").copy(title = "Rapport client final")
        return base.copy(tasks = base.tasks + done + open, settings = base.settings.copy(learning = model?.let { LearningSettings(model = it) }))
    }

    @Test
    fun aModelEstimatedTaskIsDrawnInItsRangeAndCounted() {
        val withModel = ForecastFixtures.run(team(), runs = 400)
        assertEquals(1, withModel.modelEstimated)
        // Facteurs d'erreur de l'équipe tous à 1 : seule la fourchette du modèle fait varier la fin.
        assertTrue(withModel.samples.sortedEnds.distinct().size > 1, "la fourchette du modèle élargit la date de fin")
        assertTrue(withModel.finish!!.p95!! > withModel.finish!!.p50!!, "P95 après P50")
        // Reproductible à graine égale.
        val again = ForecastFixtures.run(team(), runs = 400)
        assertEquals(withModel.samples.sortedEnds.toList(), again.samples.sortedEnds.toList())
        // Modèle coupé : plus de fourchette, plan déterministe.
        val without = ForecastFixtures.run(team(model = false), runs = 400)
        assertEquals(0, without.modelEstimated)
        assertEquals(1, without.samples.sortedEnds.distinct().size)
    }
}
