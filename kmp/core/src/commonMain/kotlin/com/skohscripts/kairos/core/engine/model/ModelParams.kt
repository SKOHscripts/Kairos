package com.skohscripts.kairos.core.engine.model

/**
 * Constantes du modèle (docs/spec/estimateur-modele.md § Paramètres) : valeurs de départ, arbitrées par le rétro-test
 * (`ModelBacktestTest`, `IntervalCoverageTest`) ; tout changement se consigne dans la spec. Ce ne sont pas des réglages : un
 * seul interrupteur, `LearningSettings.model`.
 */
object ModelParams {
    /** Échantillons retenus au plus (les plus récents par `order`) : borne le coût de l'index, du cache et du web. */
    const val MAX_CORPUS = 500

    /**
     * Échantillons minimaux du corpus pour qu'une estimation du modèle soit fiable, et pour la régression. 12 depuis le
     * 2026-11-03 (20 avant) : le modèle doit s'activer vite, la garde reste juge de sa qualité (docs/spec/estimateur-modele.md
     * § Paramètres).
     */
    const val MIN_CORPUS = 12

    const val NEIGHBOURS_MAX = 5
    const val SIMILARITY_MIN = 0.25

    /**
     * Voisins retenus minimaux : `max(3, learning.minSamples)`, **plafonné à [NEIGHBOURS_MAX]** : au-delà, aucune base de
     * voisins ne serait jamais suffisante et le modèle s'éteindrait sans le dire (`minSamples` peut monter jusqu'à
     * `learning.history`).
     */
    fun minNeighbours(minSamples: Int): Int = minOf(NEIGHBOURS_MAX, maxOf(3, minSamples))

    const val WEIGHT_TEXT = 0.5
    const val WEIGHT_TYPE = 0.2
    const val WEIGHT_POINTS = 0.2
    const val WEIGHT_TAG = 0.1

    const val QUANTILE_LOW = 0.10
    const val QUANTILE_HIGH = 0.90

    /** Élargissement de l'intervalle des voisins : l'écart de chaque côté × (1 + WIDEN / k). */
    const val NEIGHBOUR_WIDEN = 2.0

    const val RIDGE_LAMBDA = 1.0

    /** Élargissement des quantiles de résidus de la régression. */
    const val RIDGE_WIDEN = 1.25

    /** Types de la régression : présents dans au moins ce nombre d'échantillons, au plus [RIDGE_MAX_TYPES]. */
    const val RIDGE_TYPE_MIN = 3
    const val RIDGE_MAX_TYPES = 8

    /**
     * Garde allégée le 2026-11-03 (avant : préfixe 8, 10 candidats, 0,8) : 6 candidats suffisent, sur un préfixe de 6, et le
     * modèle doit faire au moins 15 % mieux que la durée par défaut. 0,85 et non 0,9 : au rétro-test, 0,9 laisse passer le
     * pur bruit sur 6 graines sur 20 (2 à 0,85) pour à peine plus d'activations (docs/spec/estimateur-modele.md § Paramètres).
     */
    const val GATE_SIZE = 30
    const val GATE_PREFIX_MIN = 6
    const val GATE_MIN = 6
    const val GATE_RATIO = 0.85
}
