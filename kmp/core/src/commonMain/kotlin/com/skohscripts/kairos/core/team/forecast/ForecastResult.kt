package com.skohscripts.kairos.core.team.forecast

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** Modèle de simulation (docs/spec/equipe-simulation.md § Deux modèles). */
enum class ForecastModel {
    /** Rejoue le plan de charge avec des efforts tirés ; connaît les tâches une à une. */
    EFFORT,

    /** Tire le nombre de tâches finies par semaine dans l'historique ; ne connaît pas les tâches une à une. */
    THROUGHPUT,
}

/** Périmètre d'une prévision : les tâches dont on mesure la fin. */
sealed interface ForecastScope {
    /** Tout le travail assigné (tâches d'équipe à faire, portées par un membre actif). */
    data object Assigned : ForecastScope

    /** Le travail assigné et le backlog prêt (le backlog est alors réparti par la suggestion). */
    data object AssignedAndBacklog : ForecastScope

    /** Les tâches d'une catégorie (`Task.taskType`, `""` = sans catégorie). */
    data class Category(val name: String) : ForecastScope

    /** Les tâches d'un membre. */
    data class Member(val memberId: Long) : ForecastScope

    /** Une sélection de tâches (identifiants de la base, négatifs pour une tâche de scénario). */
    data class Tasks(val taskIds: Set<Long>) : ForecastScope
}

/** Dates de fin du périmètre : [p50], [p85], [p95] (`null` = « hors horizon », la fin n'arrive pas dans le garde-fou) et histogramme par semaine. */
data class FinishOutlook(
    val p50: LocalDate?,
    val p85: LocalDate?,
    val p95: LocalDate?,
    /** Tirages par semaine (lundi), de la première à la dernière semaine de fin, sans trou. */
    val histogram: List<WeekBucket>,
    /** Tirages où le périmètre n'est pas fini dans le garde-fou. */
    val outOfHorizon: Int,
)

/** Une barre de l'histogramme : la semaine commençant le [weekStart] (lundi) et ses [draws] tirages. */
data class WeekBucket(val weekStart: LocalDate, val draws: Int)

/**
 * Probabilité de tenir l'échéance d'une tâche : [lateDraws] tirages sur [draws] la
 * finissent en retard. [criticality] est l'indice de criticité (part des tirages en
 * retard). [atRisk] : [onTimeProbability] sous le seuil `deadlineRiskPercent`.
 */
data class DeadlineOutlook(
    val taskId: Long,
    val deadline: LocalDate,
    val lateDraws: Int,
    val draws: Int,
    val atRisk: Boolean,
) {
    val criticality: Double get() = if (draws == 0) 0.0 else lateDraws.toDouble() / draws
    val onTimeProbability: Double get() = if (draws == 0) 1.0 else (draws - lateDraws).toDouble() / draws
}

/** Un membre et le nombre de tirages où il finit le dernier ([draws], les ex æquo comptent chacun). */
data class MemberShare(val memberId: Long, val draws: Int, val total: Int) {
    val share: Double get() = if (total == 0) 0.0 else draws.toDouble() / total
}

/** Un ensemble de tâches en retard ([taskIds] triés) et le nombre de tirages où c'est exactement lui. */
data class LateSet(val taskIds: List<Long>, val draws: Int)

/**
 * « D'ici [date], au moins N tâches finies » : N pour 50 %, 85 % et 95 % de chances
 * (`Percentiles.atLeast`), sur [total] tâches du périmètre.
 */
data class FinishedBy(val date: LocalDate, val atLeast50: Int, val atLeast85: Int, val atLeast95: Int, val total: Int)

/**
 * Échantillons gardés pour répondre après coup (histogramme, « combien d'ici une
 * date ») : fins du périmètre triées et, par tirage, fins de chaque tâche triées
 * (en jours depuis le jour de la simulation, [OUT] = pas dans le garde-fou).
 * Égalité par **contenu**, pour que deux résultats de mêmes entrées soient égaux.
 */
class ForecastSamples internal constructor(
    internal val sortedEnds: IntArray,
    internal val taskEnds: ShortArray?,
    val scopeSize: Int,
) {
    val draws: Int get() = sortedEnds.size

    override fun equals(other: Any?): Boolean =
        other is ForecastSamples && scopeSize == other.scopeSize && sortedEnds.contentEquals(other.sortedEnds) &&
            (taskEnds == null && other.taskEnds == null || taskEnds != null && other.taskEnds != null && taskEnds.contentEquals(other.taskEnds))

    override fun hashCode(): Int = 31 * (31 * scopeSize + sortedEnds.contentHashCode()) + (taskEnds?.contentHashCode() ?: 0)

    override fun toString(): String = "ForecastSamples(draws=$draws, scopeSize=$scopeSize, perTask=${taskEnds != null})"

    companion object {
        /** Fin « hors horizon » d'une tâche ou d'un périmètre. */
        const val OUT: Int = Short.MAX_VALUE.toInt()
    }
}

/**
 * Résultat d'une simulation (docs/spec/equipe-simulation.md § Agrégats). Honnête :
 * il porte le nombre de tirages ([runs] sur [requestedRuns]), la [seed], la
 * [source] des données (historique n tâches / hypothèse par défaut), « peu
 * fiable » ([reliable] faux) et [interrupted]. Aucune date n'est donnée seule :
 * P50 · P85 · P95.
 *
 * Le modèle par débit ne connaît pas les tâches une à une : [taskLevel] est faux,
 * [deadlines], [bottleneck], [lateDistribution] et [lateSets] sont vides, et
 * [expectedLate] (0) et [allOnTimeProbability] (1) sont des valeurs neutres à ne pas afficher.
 *
 * [lateDistribution] : tirages ayant 0, 1, 2 et 3 retards ou plus. [lateSets] : les
 * ensembles de retards les plus fréquents (50 au plus), le plus fréquent d'abord
 * (ex æquo : le plus petit ensemble, puis l'ordre des identifiants) ; [mostLikelyLateSet]
 * en est le premier (vide = « aucun retard » si c'est l'issue la plus fréquente).
 */
data class ForecastResult(
    val model: ForecastModel,
    val day: LocalDate,
    val seed: Long,
    val requestedRuns: Int,
    val runs: Int,
    val interrupted: Boolean,
    /** Tâches du périmètre. 0 : rien à prévoir, [finish] est nul. */
    val scopeSize: Int,
    val source: SourceInfo,
    val taskLevel: Boolean,
    val finish: FinishOutlook?,
    val deadlines: List<DeadlineOutlook>,
    val bottleneck: List<MemberShare>,
    val lateDistribution: List<Int>,
    val lateSets: List<LateSet>,
    val mostLikelyLateSet: LateSet?,
    /** Nombre moyen de retards par tirage. */
    val expectedLate: Double,
    /** Probabilité qu'aucune échéance ne soit manquée. */
    val allOnTimeProbability: Double,
    /** Tâches du backlog à qualifier, écartées du périmètre (ni priorité ni points : pas de plan possible). */
    val toQualify: Int,
    val samples: ForecastSamples,
) {
    /** Résultat sous le minimum d'échantillons : l'écran écrit « peu fiable ». */
    val reliable: Boolean get() = source.reliable

    /** Date de fin au percentile [percent] (rang le plus proche) ; `null` = hors horizon ou rien à prévoir. */
    fun finishDate(percent: Int): LocalDate? {
        if (samples.draws == 0 || scopeSize == 0) return null
        val offset = Percentiles.nearestRank(samples.sortedEnds, percent) ?: return null
        return if (offset >= ForecastSamples.OUT) null else day.plus(DatePeriod(days = offset))
    }

    /** Les 10 (ou [limit]) tâches les plus souvent en retard : criticité décroissante, puis identifiant. */
    fun mostCritical(limit: Int = 10): List<DeadlineOutlook> =
        deadlines.filter { it.lateDraws > 0 }.sortedWith(compareByDescending<DeadlineOutlook> { it.lateDraws }.thenBy { it.taskId }).take(limit)

    /** Tâches en danger : sous le seuil de probabilité de tenir l'échéance. */
    val atRisk: List<DeadlineOutlook> get() = deadlines.filter { it.atRisk }

    /**
     * Combien de tâches du périmètre sont finies d'ici [date] (comprise), en
     * percentiles « au moins ». `null` si les fins par tâche ne sont pas gardées
     * (trop de tirages × tâches) ou si le périmètre est vide. Modèle par débit :
     * granularité d'une semaine (une tâche compte à la fin de sa semaine).
     */
    fun finishedBy(date: LocalDate): FinishedBy? {
        val ends = samples.taskEnds ?: return null
        val n = samples.scopeSize
        val draws = samples.draws
        if (n == 0 || draws == 0) return null
        val offset = minOf(day.daysUntil(date), ForecastSamples.OUT - 1)
        val counts = IntArray(draws)
        for (i in 0 until draws) {
            val base = i * n
            // Fins triées par tirage : nombre de fins ≤ offset par recherche dichotomique.
            var lo = 0
            var hi = n
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (ends[base + mid] <= offset) lo = mid + 1 else hi = mid
            }
            counts[i] = lo
        }
        counts.sort()
        return FinishedBy(date, Percentiles.atLeast(counts, 50)!!, Percentiles.atLeast(counts, 85)!!, Percentiles.atLeast(counts, 95)!!, n)
    }
}
