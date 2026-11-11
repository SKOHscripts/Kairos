package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.model.Settings
import kotlin.math.abs

/**
 * La garde (docs/spec/estimateur-modele.md § Fiabilité et garde) : le modèle ne s'applique que s'il a fait ses preuves sur
 * l'historique de l'utilisateur. Chacun des [ModelParams.GATE_SIZE] derniers échantillons du corpus (qui en a au moins
 * [ModelParams.GATE_PREFIX_MIN] plus anciens) est estimé par un modèle construit **sur les seuls échantillons plus anciens**,
 * sans jamais lire sa durée avant le calcul ; l'erreur relative symétrique médiane du modèle doit être au plus
 * [ModelParams.GATE_RATIO] fois celle de la durée par défaut, sur au moins [ModelParams.GATE_MIN] candidats.
 */
object ModelGate {
    enum class Reason { OK, TOO_FEW_SAMPLES, NOT_BETTER }

    /** [modelError] et [defaultError] : médianes des erreurs relatives symétriques sur les [evaluated] candidats. */
    data class Result(
        val evaluated: Int,
        val modelError: Double,
        val defaultError: Double,
        val passed: Boolean,
        val reason: Reason,
        /** Candidats dont la durée réelle tombait dans l'intervalle d'origine (sur [evaluated]). */
        val covered: Int = 0,
        /**
         * Facteur appliqué à chaque côté de l'intervalle pour qu'il contienne [ModelParams.CALIBRATION_PERCENT] % des candidats ;
         * 1 tant qu'il y en a moins de [ModelParams.CALIBRATION_MIN] ([calibrated] faux : fourchette pas encore vérifiée).
         */
        val stretch: Double = 1.0,
    ) {
        val calibrated: Boolean get() = evaluated >= ModelParams.CALIBRATION_MIN
    }

    /**
     * De combien l'intervalle `[low ; high]` autour de [minutes] aurait dû être étiré pour contenir [real] : l'écart à la
     * médiane rapporté à la demi-largeur du côté où tombe [real] (au moins 5 min, le pas d'arrondi). ≤ 1 : dedans.
     */
    fun score(minutes: Int, low: Int, high: Int, real: Int): Double {
        val side = if (real >= minutes) high - minutes else minutes - low
        return abs(real - minutes).toDouble() / maxOf(side, 5)
    }

    /**
     * L'étirement qui couvre [ModelParams.CALIBRATION_PERCENT] % des [scores], borné ; 1 s'ils sont trop peu. Rang
     * `⌈p × (n + 1)⌉` et non `⌈p × n⌉` : c'est la **prochaine** tâche qu'il faut couvrir, pas les n déjà vues (prédiction
     * conforme : le « + 1 » compense le petit échantillon).
     */
    fun stretch(scores: List<Double>): Double {
        if (scores.size < ModelParams.CALIBRATION_MIN) return 1.0
        val rank = ((ModelParams.CALIBRATION_PERCENT.toLong() * (scores.size + 1) + 99) / 100).toInt().coerceIn(1, scores.size)
        return scores.sorted()[rank - 1].coerceIn(ModelParams.STRETCH_MIN, ModelParams.STRETCH_MAX)
    }

    /** Erreur relative symétrique `|p − r| / max(p, r)`, dans [0 ; 1[ : bornée et indifférente au sens de l'erreur. */
    fun error(predicted: Int, real: Int): Double {
        val top = maxOf(predicted, real)
        return if (top <= 0) 0.0 else abs(predicted - real).toDouble() / top
    }

    /** Un candidat de la garde : son indice dans le corpus et l'estimation brute du modèle ajusté aux seuls plus anciens. */
    class Candidate(val index: Int, val prediction: ModelEstimator.Prediction?)

    /**
     * Les candidats du [corpus] (trié par `order`) : chacun est estimé par le modèle ajusté à `corpus[0 until j]`, d'après ses
     * seules caractéristiques (jamais sa durée).
     */
    fun candidates(samples: List<DurationEstimator.Sample>, settings: Settings): List<Candidate> {
        // Trié ici aussi : un préfixe n'est « le passé » que si le corpus est dans l'ordre des `order`.
        val corpus = samples.sortedBy { it.order }
        val start = maxOf(ModelParams.GATE_PREFIX_MIN, corpus.size - ModelParams.GATE_SIZE)
        return (start until corpus.size).map { j ->
            Candidate(j, ModelEstimator.predict(ModelEstimator.fit(corpus.subList(0, j)), ModelEstimator.Features.of(corpus[j]), settings))
        }
    }

    /** La garde du [corpus] (trié par `order`) avec les réglages [settings]. */
    fun evaluate(samples: List<DurationEstimator.Sample>, settings: Settings): Result {
        val corpus = samples.sortedBy { it.order }
        val model = ArrayList<Double>()
        val default = ArrayList<Double>()
        val scores = ArrayList<Double>()
        for (c in candidates(corpus, settings)) {
            val prediction = c.prediction?.takeIf { it.sufficient } ?: continue
            model.add(error(prediction.minutes, corpus[c.index].minutes))
            default.add(error(settings.defaultTaskDurationMinutes, corpus[c.index].minutes))
            scores.add(score(prediction.minutes, prediction.low, prediction.high, corpus[c.index].minutes))
        }
        return fromErrors(model, default, scores)
    }

    /** La décision à partir des erreurs déjà mesurées, candidat par candidat (mêmes candidats pour les deux listes). */
    fun fromErrors(model: List<Double>, default: List<Double>, scores: List<Double> = emptyList()): Result {
        val evaluated = model.size
        val covered = scores.count { it <= 1.0 }
        if (evaluated < ModelParams.GATE_MIN) {
            return Result(evaluated, lowMedian(model), lowMedian(default), passed = false, reason = Reason.TOO_FEW_SAMPLES, covered = covered)
        }
        val m = lowMedian(model)
        val d = lowMedian(default)
        val passed = m <= ModelParams.GATE_RATIO * d
        return Result(evaluated, m, d, passed, if (passed) Reason.OK else Reason.NOT_BETTER, covered, stretch(scores))
    }

    /** Médiane basse (la valeur de rang ⌈n/2⌉) : reproductible, sans moyenne de deux flottants ; 0 sans valeur. */
    fun lowMedian(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        return sorted[(sorted.size - 1) / 2]
    }
}
