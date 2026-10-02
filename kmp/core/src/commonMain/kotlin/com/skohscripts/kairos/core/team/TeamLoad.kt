package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.stats.TaskStats.Calibration
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Niveau de charge d'un membre ou de l'équipe : `OK`, `WATCH` (« à surveiller »), `OVERLOADED` (« surchargé »). */
enum class LoadLevel { OK, WATCH, OVERLOADED }

/** Un membre actif dans la vue d'ensemble de la charge (docs/spec/equipe-charge.md § Charge individuelle). */
data class MemberLoad(
    val member: TeamMember,
    /** Capacité sur l'horizon, en heures. */
    val capacityHours: Double,
    /** Charge : heures restantes assignées, tâches estimées seulement. */
    val loadHours: Double,
    /** Taux de charge en % (`charge ÷ capacité`) ; `null` si la capacité est nulle (taux indéfini). */
    val ratePercent: Double?,
    val level: LoadLevel,
    val inProgressCount: Int,
    val todoCount: Int,
    /** Tâches ouvertes sans effort (ni durée ni points), comptées à part. */
    val unestimatedCount: Int,
    val nextAbsence: MemberAbsence?,
    /** Tâches « en danger » : leur fin prévue dépasse l'échéance (ou elles ne peuvent pas la tenir). */
    val atRiskCount: Int,
    val plan: LoadPlan.MemberPlan,
)

/** Une catégorie (`Task.taskType`, `""` = sans catégorie) : où passe la capacité. */
data class CategoryLoad(
    val category: String,
    val assignedHours: Double,
    val backlogHours: Double,
    /** Tâches de la catégorie sans effort (assignées ou non), comptées à part. */
    val unestimatedCount: Int,
    /** `(assignée + backlog) ÷ capacité de l'équipe sur l'horizon`, 0 à n ; `null` si la capacité est nulle. */
    val capacityShare: Double?,
) {
    val totalHours: Double get() = assignedHours + backlogHours
}

/** Écart entre membres : le moins et le plus chargé (parmi ceux dont le taux est défini). */
data class LoadSpread(val lowest: MemberLoad, val highest: MemberLoad) {
    val gapPercent: Double get() = highest.ratePercent!! - lowest.ratePercent!!
}

/**
 * Vue d'ensemble de la charge (docs/spec/equipe-charge.md) : par membre actif,
 * globale, par catégorie. Pur : l'instant et le fuseau sont des paramètres.
 *
 * Les heures de charge ne comptent que les tâches **estimées** ; les tâches
 * sans effort sont dénombrées à part ([unestimatedAssigned],
 * [unestimatedBacklog]), jamais comptées pour zéro en silence.
 */
data class TeamLoad(
    val horizonWeeks: Int,
    val focusFactor: Double,
    val warnPercent: Int,
    /** Membres actifs, « moi » d'abord. */
    val members: List<MemberLoad>,
    /** Capacité de l'équipe sur l'horizon (membres actifs), en heures. */
    val capacityHours: Double,
    /** Charge assignée de l'équipe, en heures. */
    val assignedHours: Double,
    val ratePercent: Double?,
    val level: LoadLevel,
    /** Backlog non assigné (tâches d'équipe à faire sans membre actif), en heures. */
    val backlogHours: Double,
    /** Backlog en semaines d'équipe : [backlogHours] ÷ capacité hebdomadaire nominale (5 jours ouvrés) ; `null` si elle est nulle. */
    val backlogTeamWeeks: Double?,
    val backlogTaskCount: Int,
    val unestimatedAssigned: Int,
    val unestimatedBacklog: Int,
    /** Catégories de la plus à la moins lourde (assigné + backlog). */
    val categories: List<CategoryLoad>,
    /** Le moins et le plus chargé ; `null` s'il y a moins de deux membres au taux défini. */
    val spread: LoadSpread?,
    val plan: LoadPlan.Result,
) {
    companion object {
        /** Niveau d'un taux (en %) : au-delà de 100 surchargé, au-delà de [warnPercent] à surveiller (bornes strictes). */
        fun level(ratePercent: Double?, loadHours: Double, warnPercent: Int): LoadLevel = when {
            ratePercent == null -> if (loadHours > 0.0) LoadLevel.OVERLOADED else LoadLevel.OK
            ratePercent > 100.0 -> LoadLevel.OVERLOADED
            ratePercent > warnPercent -> LoadLevel.WATCH
            else -> LoadLevel.OK
        }

        /**
         * Vue d'ensemble à [now] (fuseau [timeZone]). [horizonWeeks] : l'horizon
         * choisi à l'écran, à défaut celui des réglages. [calibration] : à défaut,
         * `Effort.teamCalibration`.
         */
        fun build(
            snapshot: KairosSnapshot,
            now: Instant,
            timeZone: TimeZone,
            horizonWeeks: Int? = null,
            calibration: List<Calibration>? = null,
        ): TeamLoad {
            val settings = snapshot.settings
            val team = settings.team ?: TeamSettings()
            val local = now.toLocalDateTime(timeZone)
            val day = local.date
            val weeks = horizonWeeks ?: team.horizonWeeks
            val cal = calibration ?: Effort.teamCalibration(snapshot, now)
            val fraction = Capacity.todayFraction(local, settings)
            val plan = LoadPlan.build(snapshot, day, cal, weeks, fraction)

            val active = TeamMembers.active(snapshot.members)
            val activeIds = active.mapTo(HashSet()) { it.id }
            val open = snapshot.tasks.filter { it.space == TaskSpace.TEAM && it.status == TaskStatus.TODO }

            val members = active.map { m ->
                val mp = checkNotNull(plan.of(m.id))
                val mine = open.filter { it.assigneeId == m.id }
                val rate = if (mp.capacityHours > 0.0) 100.0 * mp.loadHours / mp.capacityHours else null
                MemberLoad(
                    member = m,
                    capacityHours = mp.capacityHours,
                    loadHours = mp.loadHours,
                    ratePercent = rate,
                    level = level(rate, mp.loadHours, team.loadWarnPercent),
                    inProgressCount = mine.count { TeamStates.of(it) == TeamState.IN_PROGRESS },
                    todoCount = mine.count { TeamStates.of(it) == TeamState.TODO },
                    unestimatedCount = mp.unestimatedCount,
                    nextAbsence = TeamMembers.nextAbsence(snapshot.absences, m.id, day),
                    atRiskCount = mp.tasks.count { it.late },
                    plan = mp,
                )
            }

            val capacity = members.sumOf { it.capacityHours }
            val assigned = members.sumOf { it.loadHours }
            val rate = if (capacity > 0.0) 100.0 * assigned / capacity else null

            // Backlog : tâches d'équipe à faire que personne (d'actif) ne porte.
            val backlog = open.filter { it.assigneeId == null || it.assigneeId !in activeIds }
            val backlogEfforts = backlog.associate { it.id to Effort.remaining(it, cal, settings).first }
            val backlogHours = backlogEfforts.values.sumOf { it ?: 0.0 }
            val weeklyTeam = active.sumOf { Capacity.weeklyHours(it, settings) }

            val categoryOf: (Task) -> String = { it.taskType }
            val assignedByCategory = members.flatMap { it.plan.byCategory.entries }
                .groupBy({ it.key }, { it.value }).mapValues { (_, v) -> v.sum() }
            val backlogByCategory = backlog.groupBy(categoryOf)
                .mapValues { (_, list) -> list.sumOf { backlogEfforts.getValue(it.id) ?: 0.0 } }
            val unestimatedByCategory = HashMap<String, Int>()
            for (t in open) {
                val unknown = if (t.assigneeId != null && t.assigneeId in activeIds) {
                    plan.task(t.id)?.unestimated == true
                } else {
                    backlogEfforts.getValue(t.id) == null
                }
                if (unknown) unestimatedByCategory[t.taskType] = (unestimatedByCategory[t.taskType] ?: 0) + 1
            }
            val categories = (assignedByCategory.keys + backlogByCategory.keys + unestimatedByCategory.keys)
                .map { c ->
                    val total = (assignedByCategory[c] ?: 0.0) + (backlogByCategory[c] ?: 0.0)
                    CategoryLoad(
                        category = c,
                        assignedHours = assignedByCategory[c] ?: 0.0,
                        backlogHours = backlogByCategory[c] ?: 0.0,
                        unestimatedCount = unestimatedByCategory[c] ?: 0,
                        capacityShare = if (capacity > 0.0) total / capacity else null,
                    )
                }
                .sortedWith(compareByDescending<CategoryLoad> { it.totalHours }.thenBy { it.category })

            val rated = members.filter { it.ratePercent != null }
            val spread = if (rated.size >= 2) {
                // Égalité : le premier dans l'ordre d'affichage.
                LoadSpread(rated.minBy { it.ratePercent!! }, rated.maxBy { it.ratePercent!! })
            } else {
                null
            }

            return TeamLoad(
                horizonWeeks = weeks,
                focusFactor = team.focusFactor,
                warnPercent = team.loadWarnPercent,
                members = members,
                capacityHours = capacity,
                assignedHours = assigned,
                ratePercent = rate,
                level = level(rate, assigned, team.loadWarnPercent),
                backlogHours = backlogHours,
                backlogTeamWeeks = if (weeklyTeam > 0.0) backlogHours / weeklyTeam else null,
                backlogTaskCount = backlog.size,
                unestimatedAssigned = members.sumOf { it.unestimatedCount },
                unestimatedBacklog = backlogEfforts.values.count { it == null },
                categories = categories,
                spread = spread,
                plan = plan,
            )
        }
    }
}
