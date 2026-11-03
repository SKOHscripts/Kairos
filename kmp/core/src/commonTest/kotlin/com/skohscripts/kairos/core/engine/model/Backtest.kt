package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
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
        for (j in maxOf(ModelParams.GATE_PREFIX_MIN, i - ModelParams.GATE_SIZE) until i) {
            val p = prefixPredictions[j]?.takeIf { it.sufficient } ?: continue
            model.add(ModelGate.error(p.minutes, history[j].minutes))
            default.add(ModelGate.error(settings.defaultTaskDurationMinutes, history[j].minutes))
        }
        return ModelGate.fromErrors(model, default)
    }

    fun steps(): List<Step> {
        val start = (history.size * 3 + 9) / 10
        val default = settings.defaultTaskDurationMinutes
        return (start until history.size).map { i ->
            val prefix = DurationEstimator.History(history.subList(0, i))
            val truth = history[i]
            val task = task(truth)
            val title = TitleEstimator.estimate(task, prefix, settings)?.takeIf { it.reliable }?.minutes
            val withoutModel = DurationEstimator.estimate(task, prefix.samples, settings)?.takeIf { it.reliable }?.minutes
            val p = prefixPredictions[i]
            val reliable = p != null && i >= ModelParams.MIN_CORPUS && p.sufficient && gate(i).passed
            Step(
                i, truth.minutes, default, title ?: default, withoutModel ?: default,
                model = p?.minutes ?: default, modelReliable = reliable,
                chain = withoutModel ?: (if (reliable) p!!.minutes else default),
                low = p?.low, high = p?.high,
            )
        }
    }

    companion object {
        fun medianError(steps: List<Step>, pick: (Step) -> Int): Double = ModelGate.lowMedian(steps.map { ModelGate.error(pick(it), it.real) })
    }
}
