package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.engine.Dependencies
import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.stats.TaskStats.Calibration
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Suggestion de répartition du backlog (docs/spec/equipe-charge.md §
 * Suggestion). Glouton explicable : les tâches prêtes sont prises dans l'ordre
 * du score ; chacune va au membre actif qui la **finirait le plus tôt** selon le
 * plan de charge, avec deux préférences dans l'écart de fin toléré. Pur et
 * déterministe : mêmes entrées, même proposition.
 */
object AssignmentSuggestion {
    /** Fenêtre de l'affinité : tâches de la catégorie faites dans les 12 dernières semaines. */
    const val AFFINITY_WEEKS = 12

    /** Critère qui a désigné le membre retenu. */
    enum class Decider {
        /** Seul candidat, ou seul à finir au plus tôt (dans l'écart toléré). */
        EARLIEST,

        /** Le plus d'affinité de catégorie parmi les fins proches. */
        AFFINITY,

        /** Le seul sous la limite d'en-cours parmi les ex æquo. */
        WIP_LIMIT,

        /** La charge la plus faible parmi les ex æquo. */
        LOAD,

        /** Dernier recours : le plus petit identifiant. */
        ID,
    }

    /**
     * Pourquoi ce membre, de façon structurée (l'interface fait la phrase).
     * [plannedEnd] : fin prévue (`null` = pas de date possible, tâche bloquée par
     * le backlog ou hors horizon) ; [category] : catégorie de la tâche (`""` =
     * sans) ; [affinityTasks] : tâches de cette catégorie faites par le membre
     * en 12 semaines (toujours 0 sans catégorie) ; [wipLimit] : la limite
     * d'en-cours des réglages ; [overWipLimit] : le membre retenu est déjà
     * au-delà (aucun autre ex æquo ne l'était pas) ; [decidedBy] : le critère
     * décisif.
     */
    data class Reason(
        val plannedEnd: LocalDate?,
        val category: String,
        val affinityTasks: Int,
        val wipLimit: Int,
        val overWipLimit: Boolean,
        val decidedBy: Decider,
    )

    /** Essai d'un candidat : la base avec la tâche à son nom, la fin prévue de la tâche et la charge posée du membre. */
    /**
     * Un essai : [end] = fin prévue de la tâche chez ce membre (affichée) ; [queueEnd] = fin de
     * **toute** sa file, tâche comprise, qui sert à choisir. Le plan rejoué place la tâche à son
     * rang de score : juger sur sa seule fin laisserait une P0 doubler tout le travail d'un membre
     * surchargé et paraître finir tôt ; la fin de file revient à l'ajouter « en fin de plan »
     * (docs/spec/equipe-charge.md § Suggestion).
     */
    private class Trial(val member: TeamMember, val snapshot: KairosSnapshot, val end: LocalDate?, val queueEnd: LocalDate?, val load: Double)

    data class Suggestion(val taskId: Long, val memberId: Long, val plannedEnd: LocalDate?, val reason: Reason)

    /**
     * Proposition : [suggestions] dans l'ordre de traitement ; [toQualify] :
     * tâches écartées car à qualifier (priorité ou points manquants) ;
     * [unplaceable] : tâches sans candidat (équipe vide, ou tout le monde absent
     * sur l'horizon) ; [archivedMembers] : nombre de membres archivés exclus.
     */
    data class Result(
        val suggestions: List<Suggestion>,
        val toQualify: List<Long>,
        val unplaceable: List<Long>,
        val archivedMembers: Int,
    )

    /**
     * Propose un assigné pour chaque tâche **prête** du backlog (d'équipe, à
     * faire, sans assigné, qualifiée), ou pour [taskIds] si fourni (les tâches
     * hors backlog sont ignorées, les tâches à qualifier signalées).
     *
     * Ordre : `Scheduling.sortKey` avec l'urgence héritée des dépendances (comme
     * le Backlog), donc un bloqueur passe avant ce qu'il bloque et son plan
     * donne une date à la tâche bloquée. Candidats : membres actifs ayant au
     * moins un jour ouvré hors absence sur l'horizon. Chaque candidat reçoit la
     * tâche dans une copie de la base, le plan est recalculé, et on garde la fin
     * la plus tôt ; à `affinityDays` jours ouvrés de cette fin, on départage par
     * affinité (décroissante), puis membre sous la limite d'en-cours, puis
     * charge posée la plus faible, puis plus petit identifiant. La tâche choisie
     * est ensuite ajoutée au plan courant : la suivante voit la charge.
     */
    fun suggest(
        snapshot: KairosSnapshot,
        now: Instant,
        timeZone: TimeZone,
        taskIds: Set<Long>? = null,
        horizonWeeks: Int? = null,
        calibration: List<Calibration>? = null,
    ): Result {
        val settings = snapshot.settings
        val team = settings.team ?: TeamSettings()
        val local = now.toLocalDateTime(timeZone)
        val day = local.date
        val weeks = horizonWeeks ?: team.horizonWeeks
        val cal = calibration ?: Effort.teamCalibration(snapshot, now)
        val fraction = Capacity.todayFraction(local, settings)
        val horizonEnd = Capacity.horizonEnd(day, weeks)
        val holidays = Workdays.holidaysFor(day, settings.holidaysFr, settings.extraHolidays)

        val active = TeamMembers.active(snapshot.members)
        val activeIds = active.mapTo(HashSet()) { it.id }
        val archived = snapshot.members.count { it.archived }
        val candidates = active.filter { Capacity.isAvailableDuring(it, day, horizonEnd, holidays, snapshot.absences, settings) }

        // Tâches du backlog concernées, dans l'ordre (urgence héritée des dépendances).
        val open = snapshot.tasks.filter { it.space == TaskSpace.TEAM && it.status == TaskStatus.TODO }
        val backlog = open.filter { it.assigneeId == null || it.assigneeId !in activeIds }
        val selected = backlog.filter { taskIds == null || it.id in taskIds }
        val toQualify = selected.filter { it.needsProcessing }.map { it.id }.sorted()

        val openIds = open.mapTo(HashSet()) { it.id }
        val edges = snapshot.dependencies
            .filter { it.taskId in openIds && it.blockerId in openIds }
            .map { Dependencies.Edge(blocked = it.taskId, blocker = it.blockerId) }
        val own = open.associate { it.id to Scheduling.sortKey(it, day, settings) }
        val effective = Dependencies.derivedUrgency(edges, own)
        val ready = selected.filter { !it.needsProcessing }.sortedBy { effective[it.id] ?: own.getValue(it.id) }

        if (candidates.isEmpty()) return Result(emptyList(), toQualify, ready.map { it.id }, archived)

        // Affinité : tâches d'équipe faites par membre et catégorie sur les 12 dernières semaines.
        val windowStart = day.minus(DatePeriod(days = 7 * AFFINITY_WEEKS))
        val eventsByTask = snapshot.teamEvents.groupBy { it.taskId }
        val doneCount = HashMap<Pair<Long, String>, Int>()
        for (t in snapshot.tasks) {
            if (t.space != TaskSpace.TEAM || t.status != TaskStatus.DONE || t.assigneeId == null || t.taskType.isEmpty()) continue
            val doneOn = TeamBoard.doneOn(t, eventsByTask[t.id].orEmpty(), timeZone)
            if (doneOn < windowStart || doneOn > day) continue
            val key = t.assigneeId to t.taskType
            doneCount[key] = (doneCount[key] ?: 0) + 1
        }

        var working = snapshot
        val suggestions = ArrayList<Suggestion>()
        for (task in ready) {
            val trials = candidates.map { m ->
                val trial = working.copy(tasks = working.tasks.map { if (it.id == task.id) it.copy(assigneeId = m.id) else it })
                val plan = LoadPlan.build(trial, day, cal, weeks, fraction)
                val end = plan.task(task.id)?.end
                val queueEnd = end?.let { e -> (plan.of(m.id)?.tasks.orEmpty().mapNotNull { it.end } + e).max() }
                Trial(m, trial, end, queueEnd, plan.of(m.id)?.plannedHours ?: 0.0)
            }
            val best = trials.mapNotNull { it.queueEnd }.minOrNull()
            // Sans date pour personne, tous sont ex æquo ; sinon l'écart se mesure en jours ouvrés depuis la meilleure fin.
            val limit = best?.let { Workdays.addBusinessDays(it, team.affinityDays, holidays) }
            var group = trials.filter { t -> limit == null || (t.queueEnd != null && t.queueEnd <= limit) }

            fun affinity(t: Trial) = if (task.taskType.isEmpty()) 0 else doneCount[t.member.id to task.taskType] ?: 0
            fun over(t: Trial) = TeamSignals.wipExceeded(snapshot.tasks, t.member.id, settings)

            var decider = Decider.EARLIEST
            fun <S : Comparable<S>> narrow(by: Decider, higherIsBetter: Boolean, score: (Trial) -> S) {
                if (group.size <= 1) return
                val scores = group.map(score)
                val target = if (higherIsBetter) scores.max() else scores.min()
                val kept = group.filter { score(it) == target }
                if (kept.size < group.size) {
                    group = kept
                    decider = by
                }
            }
            narrow(Decider.AFFINITY, higherIsBetter = true, score = ::affinity)
            narrow(Decider.WIP_LIMIT, higherIsBetter = false) { if (over(it)) 1 else 0 }
            narrow(Decider.LOAD, higherIsBetter = false) { it.load }
            if (group.size > 1) {
                group = listOf(group.minBy { it.member.id })
                decider = Decider.ID
            }
            val chosen = group.single()
            suggestions += Suggestion(
                taskId = task.id,
                memberId = chosen.member.id,
                plannedEnd = chosen.end,
                reason = Reason(chosen.end, task.taskType, affinity(chosen), team.wipLimit, over(chosen), decider),
            )
            working = chosen.snapshot
        }
        return Result(suggestions, toQualify, emptyList(), archived)
    }
}
