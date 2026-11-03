package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.model.SimulatedHistory.Profile
import kotlin.test.Test
import kotlin.test.assertTrue

/** L'intervalle nominal à 80 % contient la durée réelle dans 70 à 90 % des cas (docs/spec/estimateur-modele.md). */
class IntervalCoverageTest {
    @Test
    fun theIntervalIsHonestOnNovelTitles() {
        var inside = 0
        var tested = 0
        for (seed in 1..20) {
            for (s in Backtest(SimulatedHistory.generate(seed, Profile.NOVEL)).steps()) {
                val low = s.low ?: continue
                val high = s.high ?: continue
                tested++
                if (s.real in low..high) inside++
            }
        }
        assertTrue(tested >= 100, "$tested tâches testées")
        val coverage = inside.toDouble() / tested
        assertTrue(coverage in 0.70..0.90, "couverture ${(coverage * 100).toInt()} % sur $tested tâches")
    }
}
