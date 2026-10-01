package com.skohscripts.kairos.core.team.forecast

import com.skohscripts.kairos.core.team.forecast.ForecastFixtures.run
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Budget de performance (docs/spec/equipe-simulation.md § Critères de succès) : 200 tâches,
 * 10 membres, 5 000 tirages en moins de 2 s sur la JVM, **à froid** (la machine virtuelle n'est
 * pas chauffée : c'est la mesure la plus sévère, et la seule qui compte pour un tirage lancé
 * depuis l'interface). Le temps de [MonteCarlo.prepare] (précalcul unique) est compté.
 */
class MonteCarloPerformanceTest {
    @Test
    fun twoHundredTasksTenMembersFiveThousandRunsTakeUnderTwoSeconds() {
        val snapshot = ForecastFixtures.team(members = 10, tasksPerMember = 20)
        val data = ForecastData(
            errorFactors = List(12) { 0.7 + it * 0.1 },
            capacityFactors = (1L..10L).associateWith { List(8) { k -> 0.6 + k * 0.1 } },
        )
        val mark = TimeSource.Monotonic.markNow()
        val result = run(snapshot, data, seed = 1L, runs = 5000, options = ForecastOptions(capacityRandomness = true))
        val elapsed = mark.elapsedNow()
        println("Prévision 200 tâches x 10 membres x 5 000 tirages : $elapsed")
        assertEquals(5000, result.runs)
        assertEquals(200, result.scopeSize)
        assertTrue(elapsed.inWholeMilliseconds < 2000, "5 000 tirages en $elapsed (budget : 2 s)")
    }
}
