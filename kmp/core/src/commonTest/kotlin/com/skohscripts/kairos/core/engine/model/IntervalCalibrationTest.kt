package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.model.SimulatedHistory.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fourchette vérifiée (docs/spec/previsions-taches.md § Étape A). */
class IntervalCalibrationTest {
    @Test
    fun tooFewCandidatesLeaveTheIntervalAlone() {
        assertEquals(1.0, ModelGate.stretch(List(ModelParams.CALIBRATION_MIN - 1) { 2.5 }))
    }

    @Test
    fun theStretchIsTheEightiethPercentileOfTheScoresWithinBounds() {
        // 20 scores 0,1 … 2,0 : le rang ⌈0,8 × 21⌉ = 17 vaut 1,7.
        assertEquals(1.7, ModelGate.stretch((1..20).map { it / 10.0 }.reversed()))
        assertEquals(ModelParams.STRETCH_MAX, ModelGate.stretch(List(20) { 9.0 }))
        assertEquals(ModelParams.STRETCH_MIN, ModelGate.stretch(List(20) { 0.0 }))
    }

    @Test
    fun theScoreIsOneAtTheEdgeOfTheInterval() {
        assertEquals(1.0, ModelGate.score(minutes = 40, low = 30, high = 60, real = 60))
        assertEquals(1.0, ModelGate.score(minutes = 40, low = 30, high = 60, real = 30))
        assertEquals(2.0, ModelGate.score(minutes = 40, low = 30, high = 60, real = 80))
        // Intervalle nul : la demi-largeur vaut au moins le pas d'arrondi (5 min), jamais de division par zéro.
        assertEquals(2.0, ModelGate.score(minutes = 40, low = 40, high = 40, real = 50))
    }

    /**
     * Quel que soit le régime des durées (imprévus qui doublent une tâche sur quatre, dérive, alternance, ou rien), la
     * fourchette vérifiée contient la durée réelle près de 8 fois sur 10. Mesuré le 2026-11-11, avant → après : 81 → 79,
     * 79 → 80, 77 → 80, 76 → 78, 86 → 81 % : l'intervalle d'origine varie de 10 points selon le régime, le vérifié de 3.
     */
    @Test
    fun coverageStaysNearEightyPercentWhateverTheRegime() {
        val regimes: List<Pair<String, (Int, Int, Int) -> Int>> = listOf(
            "une sur quatre double" to { i, _, m -> if (i % 4 == 3) m * 2 else m },
            "une sur six triple" to { i, _, m -> if (i % 6 == 5) m * 3 else m },
            "dérive × 1,6 à mi-parcours" to { i, n, m -> if (i >= n / 2) m * 8 / 5 else m },
            "alternance × 0,5 et × 1,5" to { i, _, m -> if (i % 2 == 0) maxOf(2, m / 2) else m * 3 / 2 },
            "intact" to { _, _, m -> m },
        )
        for ((name, change) in regimes) {
            var inside = 0
            var tested = 0
            for (seed in 1..10) {
                val base = SimulatedHistory.generate(seed, Profile.NOVEL)
                for (s in Backtest(base.mapIndexed { i, sample -> sample.copy(minutes = change(i, base.size, sample.minutes)) }).steps()) {
                    val low = s.low ?: continue
                    val high = s.high ?: continue
                    tested++
                    if (s.real in low..high) inside++
                }
            }
            val coverage = inside.toDouble() / tested
            assertTrue(coverage in 0.75..0.85, "$name : ${(coverage * 100).toInt()} % sur $tested tâches")
        }
    }
}
