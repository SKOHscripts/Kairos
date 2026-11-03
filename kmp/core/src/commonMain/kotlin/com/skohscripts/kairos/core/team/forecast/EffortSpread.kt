package com.skohscripts.kairos.core.team.forecast

/**
 * Loi d'effort d'une tâche estimée par le modèle (docs/spec/estimateur-modele.md § Prévisions) : sa propre dispersion, apprise
 * de tâches qui lui ressemblent, remplace le facteur d'erreur global de l'équipe pour cette tâche seule. Le facteur est la
 * fonction quantile affine par morceaux passant par (0 ; a), (0,10 ; l), (0,50 ; 1), (0,90 ; h), (1 ; b), avec `l = low / m`,
 * `h = high / m`, `a = max(0,2 ; l − (1 − l) / 2)` et `b = min(5 ; h + (h − 1) / 2)` : les queues prolongent chaque côté de
 * la moitié de l'écart médiane-bord, dans les bornes [0,2 ; 5] des facteurs d'erreur de l'équipe. `+ − × ÷` seulement
 * (reproductible au bit près) ; `l = h = 1` donne un facteur constant de 1.
 */
object EffortSpread {
    const val MIN_FACTOR = 0.2
    const val MAX_FACTOR = 5.0

    /** Les rapports `l = low / m` et `h = high / m` d'un intervalle (bornés pour rester une loi croissante autour de 1). */
    fun ratios(minutes: Int, low: Int, high: Int): Pair<Double, Double> {
        if (minutes <= 0) return 1.0 to 1.0
        val l = (low.toDouble() / minutes).coerceIn(MIN_FACTOR, 1.0)
        val h = (high.toDouble() / minutes).coerceIn(1.0, MAX_FACTOR)
        return l to h
    }

    /** Le facteur d'effort au quantile [u] ∈ [0 ; 1[ pour les rapports [l] ≤ 1 ≤ [h]. */
    fun factor(u: Double, l: Double, h: Double): Double {
        val a = maxOf(MIN_FACTOR, l - (1.0 - l) / 2.0)
        val b = minOf(MAX_FACTOR, h + (h - 1.0) / 2.0)
        return when {
            u <= 0.10 -> a + (l - a) * (u / 0.10)
            u <= 0.50 -> l + (1.0 - l) * ((u - 0.10) / 0.40)
            u <= 0.90 -> 1.0 + (h - 1.0) * ((u - 0.50) / 0.40)
            else -> h + (b - h) * ((u - 0.90) / 0.10)
        }
    }
}
