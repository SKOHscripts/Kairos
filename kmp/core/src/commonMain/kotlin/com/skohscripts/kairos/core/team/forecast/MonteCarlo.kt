package com.skohscripts.kairos.core.team.forecast

import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.stats.TaskStats.Calibration
import com.skohscripts.kairos.core.team.AssignmentSuggestion
import com.skohscripts.kairos.core.team.Capacity
import com.skohscripts.kairos.core.team.Effort
import com.skohscripts.kairos.core.team.TeamMembers
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Options du modèle par effort. [includeBacklog] : à la préparation, le backlog prêt
 * (non assigné, qualifié) est réparti par `AssignmentSuggestion` avant le plan ; il
 * prend alors de la capacité comme le reste. [capacityRandomness] : « aléa de
 * capacité » (actif par défaut) ; sans historique de capacité d'un membre, pas
 * d'aléa pour lui.
 */
data class ForecastOptions(
    val includeBacklog: Boolean = false,
    val capacityRandomness: Boolean = true,
)

/**
 * Tout ce qu'une simulation demande. [snapshot] : la base **à simuler** (la réelle, ou
 * celle d'un scénario rendue par `Scenario.apply`) ; [data] : les données d'historique
 * (`ForecastData.build` sur la base **réelle**, la même pour le réel et ses scénarios).
 * [seed] : tirée par l'interface (`core` ne tire jamais de hasard) et affichée avec le
 * résultat ; même graine et mêmes tirages donnent les mêmes aléas aux mêmes tâches.
 * [now] et [timeZone] donnent le jour (et la part restante de la journée de « moi »).
 */
data class ForecastRequest(
    val snapshot: KairosSnapshot,
    val now: Instant,
    val timeZone: TimeZone,
    val data: ForecastData,
    val seed: Long,
    val scope: ForecastScope = ForecastScope.Assigned,
    val model: ForecastModel = ForecastModel.EFFORT,
    val options: ForecastOptions = ForecastOptions(),
    val runs: Int = (snapshot.settings.team ?: TeamSettings()).simulationRuns,
    /** `Effort.teamCalibration` ; calculée à la préparation si absente. */
    val calibration: List<Calibration>? = null,
)

/**
 * Simulation de Monte Carlo (docs/spec/equipe-simulation.md § Moteur, skill
 * `kairos-monte-carlo`). Deux temps :
 *
 * 1. [prepare] fait le travail **unique** : périmètre, calibration, répartition du
 *    backlog si demandée (coûteuse : un plan rejoué par candidat et par tâche ;
 *    mesuré à 0,6 s en JVM pour 150 tâches au backlog et 64 assignées sur 8 membres,
 *    bien plus sur le web : l'interface la calcule hors du fil principal et la
 *    signale), capacités de base ;
 * 2. [runBatch] tire des tranches de tirages ; [accumulator] agrège **incrémentalement**
 *    (`ForecastAccumulator.add`) : le résultat ne dépend pas du découpage.
 *
 * Le tirage `i` ne dépend que de (graine, `i`, identités stables) : jamais de l'ordre
 * de traitement ni du découpage en tranches (`ForecastRandom`).
 */
class MonteCarlo private constructor(
    internal val setup: Setup,
    private val engine: DrawEngine,
) {
    /** Ce que partagent le moteur, l'agrégateur et le résultat. */
    internal class Setup(
        val model: ForecastModel,
        val day: LocalDate,
        val seed: Long,
        val runs: Int,
        val scopeSize: Int,
        val keepTaskEnds: Boolean,
        val deadlineTaskIds: List<Long>,
        val deadlineDates: List<LocalDate>,
        val memberIds: List<Long>,
        val riskPercent: Int,
        val source: SourceInfo,
        val taskLevel: Boolean,
        val toQualify: Int,
    )

    val model: ForecastModel get() = setup.model
    val seed: Long get() = setup.seed

    /** Nombre de tirages demandés. */
    val runs: Int get() = setup.runs

    /** Nombre de tâches du périmètre (0 : rien à prévoir). */
    val scopeSize: Int get() = setup.scopeSize

    /** Source des données du modèle choisi (historique ou hypothèse par défaut, fiabilité). */
    val source: SourceInfo get() = setup.source

    /**
     * Tire les tirages [from] à `from + count - 1` (bornés à [runs]). Pur et
     * déterministe : même tranche, même résultat, quel que soit le découpage.
     */
    fun runBatch(from: Int, count: Int): DrawBatch {
        val start = from.coerceIn(0, setup.runs)
        val n = minOf(count, setup.runs - start).coerceAtLeast(0)
        return engine.runBatch(start, n, setup)
    }

    /** Un agrégateur vide pour cette simulation. */
    fun accumulator(): ForecastAccumulator = ForecastAccumulator(setup)

    /** Tous les tirages, par tranches de [batchSize], sans céder la main (tests, usage hors interface). */
    fun run(batchSize: Int = DEFAULT_BATCH): ForecastResult {
        val acc = accumulator()
        var from = 0
        while (from < setup.runs) {
            acc.add(runBatch(from, batchSize))
            from += batchSize
        }
        return acc.result()
    }

    companion object {
        /** Taille de tranche conseillée à l'interface (une tranche = environ une trame de rendu sur le bureau). */
        const val DEFAULT_BATCH = 250

        /** Au-delà de ce nombre de (tirages × tâches), les fins par tâche ne sont pas gardées (« combien d'ici une date » indisponible). */
        const val MAX_KEPT_TASK_ENDS = 16_000_000L

        /** Le modèle demandé peut tourner avec ces données (le modèle par débit demande 4 semaines avec une tâche finie). */
        fun isAvailable(model: ForecastModel, data: ForecastData, scope: ForecastScope = ForecastScope.Assigned): Boolean = when (model) {
            ForecastModel.EFFORT -> true
            ForecastModel.THROUGHPUT -> data.throughputAvailable((scope as? ForecastScope.Member)?.memberId)
        }

        /** Prépare la simulation décrite par [request] (voir la classe). */
        fun prepare(request: ForecastRequest): MonteCarlo {
            val snapshot = request.snapshot
            val settings = snapshot.settings
            val team = settings.team ?: TeamSettings()
            val local = request.now.toLocalDateTime(request.timeZone)
            val day = local.date
            val fraction = Capacity.todayFraction(local, settings)
            val calibration = request.calibration ?: Effort.teamCalibration(snapshot, request.now)
            val runs = request.runs.coerceAtLeast(1)
            val scope = request.scope

            val activeIds = TeamMembers.active(snapshot.members).mapTo(HashSet()) { it.id }
            val open = snapshot.tasks.filter { it.space == TaskSpace.TEAM && it.status == TaskStatus.TODO }
            val assigned = open.filter { it.assigneeId != null && it.assigneeId in activeIds }
            val backlog = open.filter { it.assigneeId == null || it.assigneeId !in activeIds }
            val ready = backlog.filter { !it.needsProcessing }
            val toQualify = backlog.size - ready.size

            // Backlog à placer : tout (option ou périmètre « + backlog »), ou seulement la sélection de tâches.
            val everything = request.options.includeBacklog || scope == ForecastScope.AssignedAndBacklog
            val requested: List<Task> = when {
                everything -> ready
                scope is ForecastScope.Tasks -> ready.filter { it.id in scope.taskIds }
                else -> emptyList()
            }
            val requestedIds = requested.mapTo(HashSet()) { it.id }

            var working = snapshot
            if (request.model == ForecastModel.EFFORT && requested.isNotEmpty()) {
                val suggestion = AssignmentSuggestion.suggest(
                    snapshot, request.now, request.timeZone, taskIds = if (everything) null else requestedIds, calibration = calibration,
                )
                val target = suggestion.suggestions.associate { it.taskId to it.memberId }
                working = snapshot.copy(tasks = snapshot.tasks.map { t -> target[t.id]?.let { t.copy(assigneeId = it) } ?: t })
            }

            // Tâches en jeu : portées (après répartition) ou demandées au backlog (même sans porteur : elles ne finiront pas).
            val workingActive = working.tasks.filter {
                it.space == TaskSpace.TEAM && it.status == TaskStatus.TODO &&
                    ((it.assigneeId != null && it.assigneeId in activeIds) || it.id in requestedIds)
            }
            val originallyAssigned = assigned.mapTo(HashSet()) { it.id }
            val scoped = when (scope) {
                ForecastScope.Assigned -> workingActive.filter { it.id in originallyAssigned }
                ForecastScope.AssignedAndBacklog -> workingActive
                is ForecastScope.Category -> workingActive.filter { it.taskType == scope.name }
                is ForecastScope.Member -> workingActive.filter { it.assigneeId == scope.memberId }
                is ForecastScope.Tasks -> workingActive.filter { it.id in scope.taskIds }
            }.sortedBy { it.id }
            val scopeIds = LongArray(scoped.size) { scoped[it].id }

            val withDeadline = scoped.filter { it.deadline != null }
            val memberIds = scoped.mapNotNull { it.assigneeId }.distinct().sorted()
            val memberScope = (scope as? ForecastScope.Member)?.memberId
            val effort = request.model == ForecastModel.EFFORT
            val setup = Setup(
                model = request.model,
                day = day,
                seed = request.seed,
                runs = runs,
                scopeSize = scoped.size,
                keepTaskEnds = runs.toLong() * scoped.size <= MAX_KEPT_TASK_ENDS,
                deadlineTaskIds = if (effort) withDeadline.map { it.id } else emptyList(),
                deadlineDates = if (effort) withDeadline.map { it.deadline!! } else emptyList(),
                memberIds = if (effort) memberIds else emptyList(),
                riskPercent = team.deadlineRiskPercent,
                source = if (effort) request.data.estimationSource else request.data.throughputSource(memberScope),
                taskLevel = effort,
                toQualify = toQualify,
            )
            val engine: DrawEngine = if (effort) {
                EffortModel(
                    snapshot = working, day = day, calibration = calibration, todayFraction = fraction,
                    data = request.data, capacityRandomness = request.options.capacityRandomness, seed = request.seed, scopeIds = scopeIds,
                )
            } else {
                ThroughputModel(
                    day = day,
                    holidays = Workdays.holidaysFor(day, settings.holidaysFr, settings.extraHolidays),
                    history = request.data.throughputOf(memberScope),
                    memberKey = memberScope ?: 0L,
                    seed = request.seed,
                    scopeSize = scoped.size,
                )
            }
            return MonteCarlo(setup, engine)
        }
    }
}
