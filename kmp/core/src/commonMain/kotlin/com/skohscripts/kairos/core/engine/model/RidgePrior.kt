package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Régression légère en repli (docs/spec/estimateur-modele.md § Le modèle, 4), quand trop peu de voisins ressemblent : crête
 * (ridge) sur `sqrt(minutes)`, variables constante, rang des points centré-réduit et « points absents », un indicateur par
 * type fréquent (au moins [ModelParams.RIDGE_TYPE_MIN] échantillons, [ModelParams.RIDGE_MAX_TYPES] au plus, ordre
 * alphabétique des types repliés), P0, P1, récurrence. Équations normales résolues par élimination de Gauss à pivot partiel :
 * `+ − × ÷` et `sqrt` seulement, ordre fixe.
 */
class RidgePrior private constructor(
    private val types: List<String>,
    private val beta: DoubleArray,
    /** Quantiles à 10 % et 90 % des résidus (en racine de minutes). */
    private val residualLow: Double,
    private val residualHigh: Double,
    val corpus: Int,
) {
    class Prediction(val root: Double, val minutes: Double, val low: Double, val high: Double, val taskType: String?, val points: Int?)

    /** Le type de [taskType] connu du modèle (une de ses variables), ou `null`. */
    fun knownType(taskType: String): String? = Similarity.key(taskType).takeIf { it.isNotEmpty() && it in types }

    /**
     * La prédiction pour une tâche, ou `null` sans caractéristique pour la décrire (ni type connu ni points) : la régression ne
     * rendrait que la moyenne des tâches, ce qui n'est pas une estimation de *cette* tâche.
     */
    fun predict(taskType: String, points: Int?, priority: Int?, recurring: Boolean): Prediction? {
        val type = knownType(taskType)
        val rank = Similarity.rank(points)
        if (type == null && rank == null) return null
        val x = features(types, type, rank, priority, recurring)
        var y = 0.0
        for (k in x.indices) y += x[k] * beta[k]
        val root = if (y > 0.0) y else 0.0
        fun square(v: Double) = if (v > 0.0) v * v else 0.0
        return Prediction(
            root, root * root,
            maxOf(5.0, square(root + ModelParams.RIDGE_WIDEN * residualLow)),
            square(root + ModelParams.RIDGE_WIDEN * residualHigh),
            type?.let { typeLabel(taskType) }, points?.takeIf { rank != null },
        )
    }

    private fun typeLabel(taskType: String) = taskType.trim()

    companion object {
        /** La régression du corpus (trié par `taskId`), ou `null` sous [ModelParams.MIN_CORPUS] échantillons. */
        fun fit(samples: List<DurationEstimator.Sample>): RidgePrior? {
            if (samples.size < ModelParams.MIN_CORPUS) return null
            val counts = LinkedHashMap<String, Int>()
            for (s in samples) {
                val k = Similarity.key(s.taskType)
                if (k.isNotEmpty()) counts[k] = (counts[k] ?: 0) + 1
            }
            val types = counts.filter { it.value >= ModelParams.RIDGE_TYPE_MIN }.keys.sorted().take(ModelParams.RIDGE_MAX_TYPES)
            val rows = samples.map { s ->
                features(types, Similarity.key(s.taskType).takeIf { it in types }, Similarity.rank(s.points), s.priority, s.recurring)
            }
            val ys = samples.map { sqrt(it.minutes.toDouble()) }
            val d = rows.first().size
            // (XᵀX + λI') β = Xᵀy, sans pénaliser la constante (indice 0).
            val a = Array(d) { DoubleArray(d) }
            val b = DoubleArray(d)
            for (r in rows.indices) {
                val x = rows[r]
                for (i in 0 until d) {
                    b[i] += x[i] * ys[r]
                    for (j in 0 until d) a[i][j] += x[i] * x[j]
                }
            }
            for (i in 1 until d) a[i][i] += ModelParams.RIDGE_LAMBDA
            val beta = solve(a, b) ?: return null
            val residuals = rows.indices.map { r ->
                var y = 0.0
                for (k in 0 until d) y += rows[r][k] * beta[k]
                ys[r] - y
            }.sorted()
            return RidgePrior(types, beta, nearestRank(residuals, ModelParams.QUANTILE_LOW), nearestRank(residuals, ModelParams.QUANTILE_HIGH), samples.size)
        }

        /** Quantile au rang le plus proche d'une liste triée. */
        private fun nearestRank(sorted: List<Double>, q: Double): Double {
            var rank = (q * sorted.size).toInt()
            if (rank.toDouble() < q * sorted.size) rank++
            return sorted[(rank - 1).coerceIn(0, sorted.size - 1)]
        }

        private fun features(types: List<String>, type: String?, rank: Int?, priority: Int?, recurring: Boolean): DoubleArray {
            val x = DoubleArray(1 + 2 + types.size + 3)
            x[0] = 1.0
            if (rank != null) x[1] = (rank - 3) / 3.0 else x[2] = 1.0
            if (type != null) x[3 + types.indexOf(type)] = 1.0
            val base = 3 + types.size
            if (priority == 0) x[base] = 1.0
            if (priority == 1) x[base + 1] = 1.0
            if (recurring) x[base + 2] = 1.0
            return x
        }

        /** Élimination de Gauss à pivot partiel (plus grande valeur absolue, égalités au plus petit indice) ; `null` si singulière. */
        private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
            val n = b.size
            for (col in 0 until n) {
                var pivot = col
                for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
                if (abs(a[pivot][col]) < 1e-12) return null
                if (pivot != col) {
                    val row = a[pivot]; a[pivot] = a[col]; a[col] = row
                    val v = b[pivot]; b[pivot] = b[col]; b[col] = v
                }
                for (r in col + 1 until n) {
                    val f = a[r][col] / a[col][col]
                    if (f == 0.0) continue
                    for (c in col until n) a[r][c] -= f * a[col][c]
                    b[r] -= f * b[col]
                }
            }
            val x = DoubleArray(n)
            for (r in n - 1 downTo 0) {
                var s = b[r]
                for (c in r + 1 until n) s -= a[r][c] * x[c]
                x[r] = s / a[r][r]
            }
            return x
        }
    }
}
