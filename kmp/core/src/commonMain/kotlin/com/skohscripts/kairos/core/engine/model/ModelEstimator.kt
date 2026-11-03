package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.HistoryEstimates
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import kotlin.math.floor

/**
 * Le niveau « modèle » de la chaîne (docs/spec/estimateur-modele.md) : une tâche qui ressemble à des tâches faites reçoit la
 * médiane pondérée de leurs durées réelles, avec un intervalle (quantiles 10 et 90 %, élargis), et la liste de ces tâches pour
 * preuve ; faute de voisins assez nombreux, une régression légère sur le type et les points ([RidgePrior]). Fiable seulement si
 * le corpus est assez grand, la base suffisante et la garde ([ModelGate]) réussie ; sinon rendue peu fiable (affichée, jamais
 * appliquée). Pur et déterministe : aucune graine, `+ − × ÷` et `sqrt` seulement, égalités départagées par `taskId`.
 */
object ModelEstimator : DurationEstimator {
    /** Ce que le modèle lit d'une tâche à estimer (jamais son temps, qu'elle n'a pas). */
    class Features(
        val title: String,
        val taskType: String,
        val points: Int?,
        val priority: Int?,
        val projectTag: String,
        val recurring: Boolean,
        /** Jour ISO où la tâche est planifiée et tranche de son heure fixe (rythme) ; nuls sans date ou sans heure. */
        val weekday: Int? = null,
        val band: Rhythm.Band? = null,
    ) {
        companion object {
            fun of(task: Task): Features {
                val pinned = task.pinnedStart
                val day = pinned?.date ?: task.scheduledDate
                return Features(
                    task.title, task.taskType, task.fibonacciPoints, task.priority, task.projectTag, task.recurrence != TaskRecurrence.NONE,
                    weekday = day?.let { it.dayOfWeek.ordinal + 1 }, band = pinned?.let { Rhythm.band(it.hour) },
                )
            }
            fun of(sample: DurationEstimator.Sample) = Features(sample.title, sample.taskType, sample.points, sample.priority, sample.projectTag, sample.recurring)
        }
    }

    /** Le modèle ajusté d'un corpus, sans sa garde. */
    class Fit(val index: ModelIndex, val ridge: RidgePrior?)

    /** Le modèle d'un corpus, sa garde et son rythme : ce que [ModelCache] mémorise. */
    class State(val space: TaskSpace?, val fit: Fit, val gate: ModelGate.Result, val rhythm: Rhythm.Factors = Rhythm.Factors.NONE)

    /** Une estimation brute : [sufficient] si la base l'est (assez de voisins, ou régression admise). */
    class Prediction(
        val minutes: Int,
        val low: Int,
        val high: Int,
        val samples: Int,
        val sufficient: Boolean,
        val basis: DurationEstimator.Basis,
        val neighbours: List<DurationEstimator.Neighbour>,
        val taskType: String?,
        val points: Int?,
        val corpus: Int,
    )

    /** Le corpus : les [ModelParams.MAX_CORPUS] échantillons les plus récents par `taskId`, triés par `taskId` croissant. */
    fun corpus(samples: List<DurationEstimator.Sample>): List<DurationEstimator.Sample> =
        samples.sortedBy { it.taskId }.takeLast(ModelParams.MAX_CORPUS)

    fun fit(corpus: List<DurationEstimator.Sample>): Fit = Fit(ModelIndex.build(corpus), RidgePrior.fit(corpus))

    /** Le modèle et sa garde, sans cache. */
    fun build(corpus: List<DurationEstimator.Sample>, settings: Settings): State {
        val fit = fit(corpus)
        // Le rythme ne se calcule que si des échantillons ont un jour (séances et fuseau connus).
        val rhythm = if (corpus.any { it.weekday != null }) Rhythm.learn(fit, settings) else Rhythm.Factors.NONE
        return State(corpus.firstOrNull()?.space, fit, ModelGate.evaluate(corpus, settings), rhythm)
    }

    override fun estimate(task: Task, history: DurationEstimator.History, settings: Settings): DurationEstimator.Estimate? {
        val learning = settings.learning ?: LearningSettings()
        if (!learning.enabled || learning.model == false) return null
        val corpus = corpus(history.samples)
        if (corpus.isEmpty()) return null
        val state = history.cache.state(corpus, settings) { build(corpus, settings) }
        val features = Features.of(task)
        val p = predict(state.fit, features, settings) ?: return null
        val reliable = corpus.size >= ModelParams.MIN_CORPUS && p.sufficient && state.gate.passed
        // Rythme (besoin 11) : seulement sur une estimation fiable, pour une tâche planifiée ou à heure fixe.
        val rhythm = if (reliable) Rhythm.applied(state.rhythm, features.weekday, features.band).takeIf { !it.isEmpty } else null
        val f = rhythm?.factor ?: 1.0
        val cap = HistoryEstimates.cap(settings)
        val minutes = if (rhythm == null) p.minutes else round(p.minutes * f, cap)
        val low = if (rhythm == null) p.low else minOf(round(p.low * f, cap), minutes)
        val high = if (rhythm == null) p.high else maxOf(round(p.high * f, cap), minutes)
        return DurationEstimator.Estimate(
            minutes, DurationEstimator.Source.MODEL, p.samples, reliable, low, high,
            DurationEstimator.Evidence(p.basis, p.neighbours, p.taskType, p.points, p.corpus, state.gate, rhythm),
        )
    }

    /**
     * L'estimation brute de [features] par le modèle [fit], ou `null` : ni voisin ni régression possible. Les voisins d'abord ;
     * moins de `max(3, minSamples)` voisins retenus : la régression si elle est admise, sinon les voisins trouvés (base
     * insuffisante).
     */
    fun predict(fit: Fit, features: Features, settings: Settings, skip: Int = -1): Prediction? {
        val learning = settings.learning ?: LearningSettings()
        val cap = HistoryEstimates.cap(settings)
        val index = fit.index
        val target = Similarity.Target(index.query(features.title), features.taskType, features.points, features.projectTag)
        val minNeighbours = ModelParams.minNeighbours(learning.minSamples)
        val neighbours = neighbours(index, target, skip)
        if (neighbours.size >= minNeighbours) return fromNeighbours(neighbours, index.samples.size, cap, sufficient = true)
        val ridge = fit.ridge?.predict(features.taskType, features.points, features.priority, features.recurring)
        if (ridge != null) {
            val minutes = round(ridge.minutes, cap)
            return Prediction(
                minutes, minOf(round(ridge.low, cap), minutes), maxOf(round(ridge.high, cap), minutes), fit.ridge.corpus,
                sufficient = true, DurationEstimator.Basis.REGRESSION, emptyList(), ridge.taskType, ridge.points, fit.ridge.corpus,
            )
        }
        if (neighbours.isEmpty()) return null
        return fromNeighbours(neighbours, index.samples.size, cap, sufficient = false)
    }

    private class Neighbour(val sample: DurationEstimator.Sample, val similarity: Double, val weight: Double)

    /** Les voisins retenus (ressemblance ≥ seuil, les plus proches, au plus 9), pondérés `ressemblance² × récence`, sans aberrants. */
    private fun neighbours(index: ModelIndex, target: Similarity.Target, skip: Int = -1): List<Neighbour> {
        val scored = ArrayList<Pair<Int, Double>>()
        for (i in index.samples.indices) {
            if (i == skip) continue
            val s = Similarity.of(target, index, i)
            if (s >= ModelParams.SIMILARITY_MIN) scored.add(i to s)
        }
        val chosen = scored.sortedWith(
            compareByDescending<Pair<Int, Double>> { it.second }.thenByDescending { index.samples[it.first].taskId },
        ).take(ModelParams.NEIGHBOURS_MAX)
        if (chosen.isEmpty()) return emptyList()
        // Récence : 2 − rang / n, le plus récent (rang 0) compte au plus deux fois le plus ancien.
        val n = chosen.size
        val byRecency = chosen.sortedByDescending { index.samples[it.first].taskId }
        val all = byRecency.mapIndexed { rank, (i, s) -> Neighbour(index.samples[i], s, s * s * (2.0 - rank.toDouble() / n)) }
        val first = weightedQuantile(all, 0.5)
        // Un chrono oublié (plus de trois fois la médiane pondérée) est écarté, puis la médiane est recalculée.
        return all.filter { it.sample.minutes <= first * HistoryEstimates.OUTLIER_FACTOR }
    }

    private fun fromNeighbours(neighbours: List<Neighbour>, corpus: Int, cap: Int, sufficient: Boolean): Prediction {
        val m = weightedQuantile(neighbours, 0.5)
        val q10 = weightedQuantile(neighbours, ModelParams.QUANTILE_LOW)
        val q90 = weightedQuantile(neighbours, ModelParams.QUANTILE_HIGH)
        // Le min-max de k tirages ne couvre qu'une part (k−1)/(k+1) des suivants : chaque côté est élargi de (1 + 2/k).
        val widen = 1.0 + ModelParams.NEIGHBOUR_WIDEN / neighbours.size
        val minutes = round(m, cap)
        val low = minOf(round(m - (m - q10) * widen, cap), minutes)
        val high = maxOf(round(m + (q90 - m) * widen, cap), minutes)
        val evidence = neighbours.sortedWith(compareByDescending<Neighbour> { it.similarity }.thenByDescending { it.sample.taskId })
            .map { DurationEstimator.Neighbour(it.sample.taskId, it.sample.title, it.sample.minutes, floor(it.similarity * 100.0 + 0.5).toInt()) }
        return Prediction(minutes, low, high, neighbours.size, sufficient, DurationEstimator.Basis.NEIGHBOURS, evidence, null, null, corpus)
    }

    /**
     * Quantile pondéré (bas) : les durées triées croissantes (égalités par `taskId`), la première dont le poids cumulé atteint
     * [q] du poids total.
     */
    private fun weightedQuantile(neighbours: List<Neighbour>, q: Double): Double {
        val sorted = neighbours.sortedWith(compareBy<Neighbour> { it.sample.minutes }.thenBy { it.sample.taskId })
        var total = 0.0
        for (n in sorted) total += n.weight
        var cumulative = 0.0
        for (n in sorted) {
            cumulative += n.weight
            if (cumulative >= q * total) return n.sample.minutes.toDouble()
        }
        return sorted.last().sample.minutes.toDouble()
    }

    private fun round(minutes: Double, cap: Int): Int = HistoryEstimates.roundToFive(minutes).coerceIn(5, cap)
}
