package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.EstimatorChain
import com.skohscripts.kairos.core.engine.SeriesEstimator
import com.skohscripts.kairos.core.engine.TitleEstimator
import com.skohscripts.kairos.core.engine.model.SimulatedHistory.Profile
import com.skohscripts.kairos.core.model.Settings
import kotlin.test.Test
import kotlin.test.assertEquals

/** Le raccourci du rétro-test ([Backtest]) rend exactement ce que rend la vraie chaîne, garde comprise. */
class BacktestConsistencyTest {
    @Test
    fun theBacktestShortcutMatchesTheRealChain() {
        val chain = EstimatorChain(listOf(SeriesEstimator, TitleEstimator, ModelEstimator))
        val settings = Settings()
        for (profile in listOf(Profile.NOVEL, Profile.RECURRING, Profile.NOISE)) {
            val history = SimulatedHistory.generate(4, profile, n = 90)
            val bt = Backtest(history, settings)
            for (step in bt.steps().filterIndexed { i, _ -> i % 9 == 0 }) {
                val e = chain.estimate(bt.task(history[step.index]), DurationEstimator.History(history.subList(0, step.index)), settings)
                val applied = e?.takeIf { it.reliable }?.minutes ?: settings.defaultTaskDurationMinutes
                assertEquals(step.chain, applied, "$profile pas ${step.index}")
                assertEquals(ModelGate.evaluate(history.subList(0, step.index), settings), bt.gate(step.index), "$profile garde ${step.index}")
            }
        }
    }
}
