package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlin.math.ceil

/**
 * Conseil « découpe-la en sous-tâches » (docs/spec/sous-taches.md § Conseil de découpage, issue #201). Pur. Le seuil est
 * celui de l'utilisateur : pour le niveau de points choisi, ses tâches terminées disent si ce niveau s'estime bien.
 */
object SplitAdvice {
    /** Jamais à 1 ou 2 points : une petite tâche ne se découpe pas. */
    const val MIN_POINTS = 3

    /** Tâches terminées à ce niveau en dessous desquelles on ne sait pas encore (et l'on conseille de découper). */
    const val MIN_SAMPLES = 5

    /** Rapport entre les plus longues et les plus courtes (80ᵉ et 20ᵉ centiles) au-delà duquel ce niveau ne dit rien. */
    const val MAX_SPREAD = 2.5

    /** Biais réel ÷ estimé (médiane) hors de [BIAS_LOW, BIAS_HIGH] : l'estimation diverge trop de la réalité. */
    const val BIAS_LOW = 0.67
    const val BIAS_HIGH = 1.5

    enum class Reason { BIAS, SPREAD, NOT_ENOUGH_HISTORY }

    /**
     * Le conseil et ce qui le justifie : [samples] tâches terminées à [points] ; [bias] la médiane réel ÷ estimé (motif
     * [Reason.BIAS]) ; [shortest] et [longest] les 20ᵉ et 80ᵉ centiles du temps réel en minutes (motif [Reason.SPREAD]).
     */
    data class Advice(val reason: Reason, val points: Int, val samples: Int, val bias: Double? = null, val shortest: Int? = null, val longest: Int? = null)

    /** Une tâche terminée : son temps réel et son estimation (`null` sans estimation). */
    data class Sample(val actualMinutes: Int, val estimatedMinutes: Int?)

    /** L'historique par niveau de points d'un espace, calculé une fois pour la fiche. */
    class History(val byPoints: Map<Int, List<Sample>>)

    /**
     * Les tâches **terminées** de [space] qui ont des points et du temps réel ([spent]) ; ni les mères (leur temps est
     * celui de leurs filles), ni, côté Perso, les tâches reçues d'un manager (elles ne disent rien de ses propres estimations).
     */
    fun history(tasks: Collection<Task>, spent: Map<Long, Int>, space: TaskSpace): History {
        val mothers = tasks.filter { it.status != TaskStatus.ARCHIVED }.mapNotNullTo(HashSet()) { it.parentId }
        val samples = tasks.filter {
            it.status == TaskStatus.DONE && it.space == space && it.fibonacciPoints != null && it.id !in mothers &&
                (space == TaskSpace.TEAM || it.origin == null) && (spent[it.id] ?: 0) > 0
        }
        return History(samples.groupBy({ it.fibonacciPoints!! }, { Sample(spent.getValue(it.id), it.estimatedMinutes?.takeIf { m -> m > 0 }) }))
    }

    /**
     * Le conseil pour une tâche de [points], `null` s'il n'y en a pas : moins de [MIN_POINTS] points, tâche déjà découpée
     * ([hasSubtasks]), ou niveau bien estimé. Motifs, du plus parlant au moins : biais, dispersion, historique trop court.
     */
    fun advise(points: Int?, hasSubtasks: Boolean, history: History): Advice? {
        if (points == null || points < MIN_POINTS || hasSubtasks) return null
        val samples = history.byPoints[points].orEmpty()
        if (samples.size < MIN_SAMPLES) return Advice(Reason.NOT_ENOUGH_HISTORY, points, samples.size)
        val ratios = samples.mapNotNull { s -> s.estimatedMinutes?.let { s.actualMinutes.toDouble() / it } }
        if (ratios.size >= MIN_SAMPLES) {
            val bias = median(ratios)
            if (bias < BIAS_LOW || bias > BIAS_HIGH) return Advice(Reason.BIAS, points, samples.size, bias = bias)
        }
        val actual = samples.map { it.actualMinutes }.sorted()
        val shortest = percentile(actual, 0.2)
        val longest = percentile(actual, 0.8)
        if (shortest > 0 && longest.toDouble() / shortest > MAX_SPREAD) {
            return Advice(Reason.SPREAD, points, samples.size, shortest = shortest, longest = longest)
        }
        return null
    }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }

    /** Centile au rang le plus proche (valeur réellement observée) d'une liste triée non vide. */
    private fun percentile(sorted: List<Int>, p: Double): Int = sorted[(ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)]
}
