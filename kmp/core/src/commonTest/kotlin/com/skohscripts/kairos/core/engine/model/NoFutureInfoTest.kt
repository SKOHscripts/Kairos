package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.model.SimulatedHistory.Profile
import com.skohscripts.kairos.core.model.Settings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Aucune information future (docs/spec/estimateur-modele.md § Évaluation) : la garde estime chaque candidat avec les seuls
 * échantillons plus anciens, sans lire sa propre durée ; changer les durées et les titres des échantillons plus récents, ou
 * la durée du candidat lui-même, ne change pas son estimation.
 */
class NoFutureInfoTest {
    private fun ModelEstimator.Prediction?.key() = this?.let { listOf(it.minutes, it.low, it.high, it.samples, it.sufficient, it.basis, it.neighbours) }

    @Test
    fun changingLaterOrOwnDurationsLeavesACandidateUnchanged() {
        val settings = Settings()
        for (seed in listOf(1, 7, 13)) {
            val history = SimulatedHistory.generate(seed, Profile.NOVEL, n = 80)
            val reference = ModelGate.candidates(history, settings)
            for (c in reference.filterIndexed { i, _ -> i % 7 == 0 }) {
                val j = c.index
                val altered = history.mapIndexed { i, s ->
                    when {
                        i > j -> s.copy(minutes = 1 + (s.minutes * 7) % 300, title = "autre chose ${s.taskId}")
                        i == j -> s.copy(minutes = 999)
                        else -> s
                    }
                }
                val again = ModelGate.candidates(altered, settings).single { it.index == j }
                assertEquals(c.prediction.key(), again.prediction.key(), "graine $seed, candidat $j")
            }
        }
    }
}
