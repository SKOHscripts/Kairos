package com.skohscripts.kairos.ui.team.forecast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.team.MemberAbsence
import com.skohscripts.kairos.core.team.TeamEvent
import com.skohscripts.kairos.core.team.TeamLoad
import com.skohscripts.kairos.core.team.TeamMember
import com.skohscripts.kairos.core.team.forecast.ForecastData
import com.skohscripts.kairos.core.team.forecast.ForecastModel
import com.skohscripts.kairos.core.team.forecast.ForecastOptions
import com.skohscripts.kairos.core.team.forecast.ForecastRequest
import com.skohscripts.kairos.core.team.forecast.ForecastResult
import com.skohscripts.kairos.core.team.forecast.ForecastScope
import com.skohscripts.kairos.core.team.forecast.MonteCarlo
import com.skohscripts.kairos.core.team.forecast.Scenario
import com.skohscripts.kairos.core.team.forecast.TeamScenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Instant

/*
 * État et exécution des prévisions (docs/spec/equipe-simulation.md § Exécution). Le moteur (`core`) est pur : c'est
 * ici, côté interface, que la graine est tirée, que les tirages sont découpés en tranches séparées par `yield()`
 * (un seul fil sur le web) et que le résultat est gardé en mémoire avec l'empreinte des données qui l'ont produit.
 */

/** Ce que l'on simule : périmètre, modèle et options. Égalité par valeur (le choix est mémorisé d'un affichage à l'autre). */
data class ForecastSpec(
    val scope: ForecastScope = ForecastScope.Assigned,
    val model: ForecastModel = ForecastModel.EFFORT,
    val options: ForecastOptions = ForecastOptions(),
)

/**
 * Ce dont un résultat dépend dans les données (« empreinte ») : le jour, les membres, leurs absences, les tâches
 * **d'équipe** avec leurs dépendances, leurs sessions de travail et le journal, et les réglages qui comptent
 * (thème et dernier espace affiché exclus : changer de couleur ne périme pas une prévision). Les tâches
 * personnelles, les notes et les créneaux n'y figurent pas. Deux empreintes égales = mêmes entrées de simulation.
 */
class ForecastFingerprint private constructor(
    private val day: LocalDate,
    private val members: List<TeamMember>,
    private val absences: List<MemberAbsence>,
    private val tasks: List<com.skohscripts.kairos.core.model.Task>,
    private val dependencies: List<com.skohscripts.kairos.core.model.TaskDependency>,
    private val sessions: List<com.skohscripts.kairos.core.model.WorkSession>,
    private val events: List<TeamEvent>,
    private val settings: Settings,
) {
    override fun equals(other: Any?): Boolean = other is ForecastFingerprint &&
        day == other.day && settings == other.settings && members == other.members && absences == other.absences &&
        tasks == other.tasks && dependencies == other.dependencies && sessions == other.sessions && events == other.events

    override fun hashCode(): Int = 31 * day.hashCode() + tasks.hashCode()

    companion object {
        fun of(snapshot: KairosSnapshot, day: LocalDate): ForecastFingerprint {
            val tasks = snapshot.tasks.filter { it.space == TaskSpace.TEAM }
            val ids = tasks.mapTo(HashSet()) { it.id }
            val settings = snapshot.settings
            return ForecastFingerprint(
                day = day,
                members = snapshot.members,
                absences = snapshot.absences,
                tasks = tasks,
                dependencies = snapshot.dependencies.filter { it.taskId in ids },
                sessions = snapshot.workSessions.filter { it.taskId in ids },
                events = snapshot.teamEvents,
                settings = settings.copy(themeColor = Settings.DEFAULT_THEME_COLOR, team = settings.team?.copy(lastSpace = "", name = "", managerName = "")),
            )
        }
    }
}

/** Un résultat gardé en mémoire (pas en base) : ce qui a été simulé, quand, et l'empreinte des données d'entrée. */
class ForecastRun(
    val spec: ForecastSpec,
    val result: ForecastResult,
    val computedAt: Instant,
    val fingerprint: ForecastFingerprint,
) {
    companion object {
        /**
         * Calcule tout d'un trait, sans céder la main : captures de l'auto-test et tests. L'écran, lui, découpe en
         * tranches ([ForecastEngine.simulate]).
         */
        fun computeNow(snapshot: KairosSnapshot, now: Instant, zone: TimeZone, spec: ForecastSpec, seed: Long): ForecastRun {
            val result = MonteCarlo.prepare(ForecastEngine.request(snapshot, now, zone, spec, seed)).run()
            return ForecastRun(spec, result, now, ForecastFingerprint.of(snapshot, now.toLocalDateTime(zone).date))
        }
    }
}

/** Une colonne de la comparaison : la situation réelle ([scenarioId] nul) ou un scénario. */
class ComparisonColumn(
    val scenarioId: Long?,
    val name: String?,
    val result: ForecastResult,
    /** Taux de charge de l'équipe sur la base du scénario (`TeamLoad`), `null` si la capacité est nulle. */
    val loadRatePercent: Double?,
    /** Modifications du scénario non appliquées à la base courante (introuvables, hors bornes…). */
    val skipped: Int,
)

/**
 * Une comparaison gardée en mémoire : la situation réelle puis les scénarios choisis, **même graine** pour toutes
 * les colonnes (tirages communs, docs/spec/equipe-simulation.md § Tirages communs). [scenarios] : les scénarios tels
 * qu'ils étaient au calcul, pour dire « périmé » si l'un d'eux a changé depuis.
 */
class ComparisonRun(
    val spec: ForecastSpec,
    val seed: Long,
    val computedAt: Instant,
    val fingerprint: ForecastFingerprint,
    val scenarios: List<TeamScenario>,
    val columns: List<ComparisonColumn>,
) {
    companion object {
        /** Calcule la comparaison d'un trait (auto-test, tests). */
        fun computeNow(snapshot: KairosSnapshot, now: Instant, zone: TimeZone, spec: ForecastSpec, scenarios: List<TeamScenario>, seed: Long): ComparisonRun {
            val effort = spec.copy(model = ForecastModel.EFFORT)
            val data = ForecastEngine.data(snapshot, now, zone)
            val columns = ForecastEngine.columns(snapshot, scenarios).map { column ->
                val result = MonteCarlo.prepare(ForecastEngine.request(column.snapshot, now, zone, effort, seed, data)).run()
                ComparisonColumn(column.scenario?.id, column.scenario?.name, result, ForecastEngine.loadRate(column.snapshot, now, zone), column.skipped)
            }
            return ComparisonRun(effort, seed, now, ForecastFingerprint.of(snapshot, now.toLocalDateTime(zone).date), scenarios, columns)
        }
    }
}

/**
 * État de l'interface de l'espace Équipe qui survit au changement de destination (jamais en base) : la sélection
 * faite dans le Backlog (partagée avec le périmètre « sélection » des Prévisions), les choix de la simulation, le
 * dernier résultat et la dernière comparaison. [seed] tire la graine d'une simulation (`Random.nextLong()` par
 * défaut ; les tests l'imposent) et [batchSize] est la taille des tranches de tirages. [batchGate] est appelé
 * après chaque tranche avec le nombre de tirages faits (sans effet par défaut) : un test y suspend le calcul pour
 * cliquer « Arrêter » à coup sûr, quelle que soit la vitesse de la machine.
 */
class TeamUiState(
    val seed: () -> Long = { Random.nextLong() },
    val batchSize: Int = MonteCarlo.DEFAULT_BATCH,
    val batchGate: suspend (done: Int) -> Unit = {},
) {
    /** Tâches sélectionnées dans le Backlog (l'écran Backlog en est la source de vérité). */
    var backlogSelection: Set<Long> by mutableStateOf(emptySet())

    var spec: ForecastSpec by mutableStateOf(ForecastSpec())
    var forecast: ForecastRun? by mutableStateOf(null)
    var comparison: ComparisonRun? by mutableStateOf(null)

    /** Scénarios cochés pour la comparaison. */
    var compared: Set<Long> by mutableStateOf(emptySet())
}

/** Où en est un calcul : rien, préparation (périmètre, répartition du backlog), tirages (« 3 200 / 5 000 »). */
internal sealed interface RunPhase {
    data object Idle : RunPhase
    data class Preparing(val comparison: Boolean) : RunPhase
    data class Running(val done: Int, val total: Int, val comparison: Boolean, val column: String?) : RunPhase
}

/** Une colonne à simuler : la base (réelle, ou celle du scénario) et son éventuel scénario. */
internal class ForecastColumn(val scenario: TeamScenario?, val snapshot: KairosSnapshot, val skipped: Int)

internal object ForecastEngine {
    /** Données d'historique de la base **réelle** (un scénario ne change pas le passé). */
    fun data(snapshot: KairosSnapshot, now: Instant, zone: TimeZone): ForecastData =
        ForecastData.build(snapshot, now.toLocalDateTime(zone).date, now, zone)

    fun request(
        snapshot: KairosSnapshot,
        now: Instant,
        zone: TimeZone,
        spec: ForecastSpec,
        seed: Long,
        data: ForecastData = data(snapshot, now, zone),
    ): ForecastRequest =
        ForecastRequest(snapshot = snapshot, now = now, timeZone = zone, data = data, seed = seed, scope = spec.scope, model = spec.model, options = spec.options)

    /** La situation réelle puis chaque scénario appliqué à une copie. */
    fun columns(snapshot: KairosSnapshot, scenarios: List<TeamScenario>): List<ForecastColumn> =
        listOf(ForecastColumn(null, snapshot, 0)) + scenarios.map { s ->
            val (applied, skipped) = Scenario.apply(snapshot, s.modifications)
            ForecastColumn(s, applied, skipped.size)
        }

    fun loadRate(snapshot: KairosSnapshot, now: Instant, zone: TimeZone): Double? = TeamLoad.build(snapshot, now, zone).ratePercent

    /**
     * Les tirages de [request], par tranches de [batchSize] séparées par `yield()` : l'interface reste réactive (sur le
     * web, le calcul partage l'unique fil). La préparation (périmètre, répartition du backlog si demandée) est la
     * partie lourde et se fait hors du fil principal. [shouldStop] est lu entre deux tranches : vrai, le calcul
     * s'arrête et rend ce qu'il a, marqué `interrupted`. [onProgress] reçoit le nombre de tirages faits.
     */
    suspend fun simulate(
        request: ForecastRequest,
        batchSize: Int,
        shouldStop: () -> Boolean,
        onPrepared: (total: Int) -> Unit,
        onProgress: (done: Int) -> Unit,
        gate: suspend (done: Int) -> Unit = {},
    ): ForecastResult {
        val monteCarlo = withContext(Dispatchers.Default) { MonteCarlo.prepare(request) }
        val total = monteCarlo.runs
        onPrepared(total)
        val accumulator = monteCarlo.accumulator()
        var from = 0
        while (from < total && !shouldStop()) {
            val batch = withContext(Dispatchers.Default) { monteCarlo.runBatch(from, batchSize) }
            accumulator.add(batch)
            from += batchSize
            onProgress(accumulator.runs)
            gate(accumulator.runs)
            yield()
        }
        return accumulator.result(interrupted = accumulator.runs < total)
    }
}
