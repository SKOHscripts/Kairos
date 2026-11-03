package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.model.SimulatedHistory.Profile
import com.skohscripts.kairos.core.model.Settings
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** 500 tâches faites et 200 tâches à estimer en moins de 1 s (docs/spec/estimateur-modele.md § Cache et performance). */
class ModelPerformanceTest {
    @Test
    fun fiveHundredSamplesAndTwoHundredTasksUnderOneSecond() {
        // Chaque tâche a un jour et une tranche : le rythme (laisser-un-dehors sur tout le corpus) entre dans le budget.
        val history = SimulatedHistory.generate(11, Profile.NOVEL, n = 500)
            .mapIndexed { i, s -> s.copy(weekday = i % 7 + 1, band = Rhythm.Band.entries[i % 3]) }
        val targets = SimulatedHistory.generate(12, Profile.NOVEL, n = 200).map { Backtest(history).task(it).copy(id = 100_000 + it.taskId) }
        val settings = Settings()
        // Une fois pour chauffer la JVM, puis la mesure, avec le cache d'une instance d'application.
        repeat(2) { round ->
            val cache = ModelCache.memo()
            val start = TimeSource.Monotonic.markNow()
            for (t in targets) ModelEstimator.estimate(t, DurationEstimator.History(history, cache), settings)
            val elapsed = start.elapsedNow()
            if (round == 1) assertTrue(elapsed.inWholeMilliseconds < 1000, "200 estimations sur 500 tâches : $elapsed")
        }
    }
}
