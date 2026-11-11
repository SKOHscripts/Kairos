package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.HistoryEstimates
import com.skohscripts.kairos.core.engine.EstimatorChain
import com.skohscripts.kairos.core.engine.SeriesEstimator
import com.skohscripts.kairos.core.engine.TitleEstimator
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import kotlin.time.Instant

/**
 * Rétro-test par découpage temporel (walk-forward, docs/spec/estimateur-modele.md § Évaluation) : les 30 % premiers
 * échantillons servent de départ, puis chaque suivant `i` est estimé avec `samples[0 until i]` seuls, sans sa durée.
 *
 * L'estimation du modèle pour `i` est [ModelEstimator.predict] sur le modèle ajusté au préfixe : c'est aussi ce que la garde
 * calcule pour chaque candidat ; on la calcule donc une fois par échantillon et la garde de chaque pas en est tirée
 * ([ModelGate.fromErrors] sur la même fenêtre que [ModelGate.evaluate]). `BacktestConsistencyTest` vérifie que ce raccourci
 * rend exactement ce que rend la vraie chaîne.
 */
class Backtest(val history: List<DurationEstimator.Sample>, val settings: Settings = Settings()) {
    class Step(
        val index: Int,
        val real: Int,
        val default: Int,
        val title: Int,
        val chainWithoutModel: Int,
        val model: Int,
        val modelReliable: Boolean,
        val chain: Int,
        val low: Int?,
        val high: Int?,
        /** L'intervalle d'origine, avant l'étirement de la garde. */
        val rawLow: Int? = null,
        val rawHigh: Int? = null,
    )

    private val t0 = Instant.parse("2026-09-28T07:00:00Z")

    fun task(s: DurationEstimator.Sample) = Task(
        s.taskId, s.title, seriesId = s.seriesId, taskType = s.taskType, fibonacciPoints = s.points, priority = s.priority,
        projectTag = s.projectTag, space = TaskSpace.PERSONAL, createdAt = t0, updatedAt = t0,
    )

    /** L'estimation brute du modèle pour chaque échantillon `j`, ajusté sur `history[0 until j]`. */
    val prefixPredictions: List<ModelEstimator.Prediction?> by lazy {
        history.indices.map { j -> if (j == 0) null else ModelEstimator.predict(ModelEstimator.fit(history.subList(0, j)), ModelEstimator.Features.of(history[j]), settings) }
    }

    /** La garde telle que [ModelGate.evaluate] la calcule sur le corpus `history[0 until i]`. */
    fun gate(i: Int): ModelGate.Result {
        val model = ArrayList<Double>()
        val default = ArrayList<Double>()
        val scores = ArrayList<Double>()
        for (j in maxOf(ModelParams.GATE_PREFIX_MIN, i - ModelParams.GATE_SIZE) until i) {
            val p = prefixPredictions[j]?.takeIf { it.sufficient } ?: continue
            model.add(ModelGate.error(p.minutes, history[j].minutes))
            default.add(ModelGate.error(settings.defaultTaskDurationMinutes, history[j].minutes))
            scores.add(ModelGate.score(p.minutes, p.low, p.high, history[j].minutes))
        }
        return ModelGate.fromErrors(model, default, scores)
    }

    fun steps(): List<Step> {
        val start = (history.size * 3 + 9) / 10
        val default = settings.defaultTaskDurationMinutes
        return (start until history.size).map { i ->
            val prefix = DurationEstimator.History(history.subList(0, i))
            val truth = history[i]
            val task = task(truth)
            val title = TitleEstimator.estimate(task, prefix, settings)?.takeIf { it.reliable }?.minutes
            val withoutModel = WITHOUT_MODEL.estimate(task, prefix, settings)?.takeIf { it.reliable }?.minutes
            val p = prefixPredictions[i]
            val gate = gate(i)
            val reliable = p != null && i >= ModelParams.MIN_CORPUS && p.sufficient && gate.passed
            // L'intervalle livré : étiré par la garde du même préfixe, comme `ModelEstimator.estimate`.
            val k = gate.stretch
            Step(
                i, truth.minutes, default, title ?: default, withoutModel ?: default,
                model = p?.minutes ?: default, modelReliable = reliable,
                chain = withoutModel ?: (if (reliable) p!!.minutes else default),
                low = p?.let { minOf(HistoryEstimates.roundToFive(it.minutes - (it.minutes - it.low) * k).coerceAtLeast(5), it.minutes) },
                high = p?.let { maxOf(HistoryEstimates.roundToFive(it.minutes + (it.high - it.minutes) * k), it.minutes) },
                rawLow = p?.low, rawHigh = p?.high,
            )
        }
    }

    companion object {
        /** La chaîne d'avant le modèle : série, puis titre. */
        val WITHOUT_MODEL = EstimatorChain(listOf(SeriesEstimator, TitleEstimator))

        fun medianError(steps: List<Step>, pick: (Step) -> Int): Double = ModelGate.lowMedian(steps.map { ModelGate.error(pick(it), it.real) })
    }
}
