package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.model.FIBONACCI_SCALE

/**
 * Ressemblance entre une tâche à estimer et un échantillon (docs/spec/estimateur-modele.md § Le modèle, 2), dans [0 ; 1] :
 * texte 0,5, type 0,2, points 0,2, étiquette de projet 0,1, moyenne pondérée **sur les seuls termes disponibles pour la
 * tâche** (une tâche sans type ne perd pas la moitié de son score maximal). La priorité n'y entre pas : urgence n'est pas taille.
 */
object Similarity {
    /** Ce que la ressemblance lit de la tâche à estimer. */
    class Target(val query: ModelIndex.Query, val taskType: String, val points: Int?, val projectTag: String)

    /** Clé de comparaison d'un type ou d'une étiquette. */
    fun key(value: String): String = value.trim().lowercase()

    /** Rang dans `FIBONACCI_SCALE` (0 à 6), ou `null` si absent ou hors de l'échelle. */
    fun rank(points: Int?): Int? = points?.let { p -> FIBONACCI_SCALE.indexOf(p).takeIf { it >= 0 } }

    fun of(target: Target, index: ModelIndex, i: Int): Double {
        val s: DurationEstimator.Sample = index.samples[i]
        var sum = 0.0
        var weights = 0.0
        if (target.query.hasText) {
            sum += ModelParams.WEIGHT_TEXT * index.text(target.query, i)
            weights += ModelParams.WEIGHT_TEXT
        }
        val type = key(target.taskType)
        if (type.isNotEmpty()) {
            if (key(s.taskType) == type) sum += ModelParams.WEIGHT_TYPE
            weights += ModelParams.WEIGHT_TYPE
        }
        val rank = rank(target.points)
        if (rank != null) {
            val other = rank(s.points)
            if (other != null) sum += ModelParams.WEIGHT_POINTS * (1.0 - kotlin.math.abs(rank - other) / 6.0)
            weights += ModelParams.WEIGHT_POINTS
        }
        val tag = key(target.projectTag)
        if (tag.isNotEmpty()) {
            if (key(s.projectTag) == tag) sum += ModelParams.WEIGHT_TAG
            weights += ModelParams.WEIGHT_TAG
        }
        return if (weights == 0.0) 0.0 else sum / weights
    }
}
