package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.model.SimulatedHistory.Profile
import kotlin.test.Test
import kotlin.test.assertTrue

/** Critères chiffrés (docs/spec/estimateur-modele.md § Critères de succès), graines 1 à 20 en dur. */
class ModelBacktestTest {
    private val seeds = 1..20

    private fun steps(seed: Int, profile: Profile) = Backtest(SimulatedHistory.generate(seed, profile)).steps()

    @Test
    fun novelTitlesBeatTheDefault() {
        val wins = seeds.count { seed ->
            val s = steps(seed, Profile.NOVEL)
            Backtest.medianError(s) { it.chain } <= 0.8 * Backtest.medianError(s) { it.chainWithoutModel }
        }
        assertTrue(wins >= 18, "titres nouveaux : la chaîne complète ne bat la chaîne sans modèle que sur $wins graines sur 20")
    }

    @Test
    fun modelIsNotWorseThanTitleMedian() {
        var model = 0.0
        var title = 0.0
        for (seed in seeds) {
            val s = steps(seed, Profile.RECURRING)
            val m = Backtest.medianError(s) { it.model }
            val t = Backtest.medianError(s) { it.title }
            assertTrue(m <= 1.1 * t, "graine $seed : modèle $m, médiane par titre $t")
            model += m
            title += t
        }
        assertTrue(model <= title, "en agrégé : modèle $model, médiane par titre $title")
    }

    @Test
    fun neverWorseThanDefault() {
        for (profile in listOf(Profile.NOVEL, Profile.RECURRING, Profile.NOISE, Profile.SPARSE)) {
            for (seed in seeds) {
                val s = steps(seed, profile)
                val chain = Backtest.medianError(s) { it.chain }
                val default = Backtest.medianError(s) { it.default }
                assertTrue(chain <= 1.1 * default, "$profile graine $seed : chaîne $chain, durée par défaut $default")
            }
        }
    }
}
