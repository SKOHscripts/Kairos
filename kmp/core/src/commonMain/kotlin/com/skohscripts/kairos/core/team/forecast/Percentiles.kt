package com.skohscripts.kairos.core.team.forecast

/**
 * Percentiles par la méthode du **rang le plus proche** (docs/spec/equipe-simulation.md
 * § Moteur) : pour [n] valeurs triées, le percentile `p` est la `ceil(p × n)`-ième,
 * indice borné à [1, n]. Jamais d'interpolation : une date n'a pas de moitié, et
 * le résultat est toujours une valeur réellement tirée.
 *
 * Le pourcentage est un **entier** (50, 85, 95) et le rang se calcule en
 * arithmétique entière : `ceil(percent × n / 100)` sans l'erreur d'arrondi d'un
 * `0,85 × n` flottant (0,85 n'est pas représentable exactement).
 */
object Percentiles {
    /** Rang (de 1 à [n]) du percentile [percent] sur [n] valeurs ; 0 si [n] est nul. */
    fun rank(n: Int, percent: Int): Int {
        if (n <= 0) return 0
        val r = (percent.toLong() * n + 99) / 100
        return r.coerceIn(1L, n.toLong()).toInt()
    }

    /** Percentile [percent] de [sortedAscending] (trié par ordre croissant) ; `null` si vide. */
    fun nearestRank(sortedAscending: IntArray, percent: Int): Int? {
        if (sortedAscending.isEmpty()) return null
        return sortedAscending[rank(sortedAscending.size, percent) - 1]
    }

    /** Percentile [percent] de [sortedAscending] ; `null` si vide. */
    fun <T> nearestRank(sortedAscending: List<T>, percent: Int): T? {
        if (sortedAscending.isEmpty()) return null
        return sortedAscending[rank(sortedAscending.size, percent) - 1]
    }

    /**
     * « Au moins » : la plus grande valeur atteinte ou dépassée avec une
     * probabilité d'au moins [percent] %, c'est-à-dire la `ceil(percent × n / 100)`-ième
     * plus **grande** valeur de [sortedAscending] (« d'ici le 31 oct. : au moins 14
     * tâches, 85 % de chances »). Pour 50 % c'est la médiane haute ; `null` si vide.
     */
    fun atLeast(sortedAscending: IntArray, percent: Int): Int? {
        if (sortedAscending.isEmpty()) return null
        return sortedAscending[sortedAscending.size - rank(sortedAscending.size, percent)]
    }
}
