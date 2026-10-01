package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.engine.Dependencies
import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.stats.TaskStats.Calibration
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Plan de charge déterministe (docs/spec/equipe-charge.md § Plan de charge) :
 * pour chaque membre actif, ses tâches ouvertes posées l'une après l'autre dans
 * sa capacité, jour après jour, en respectant les dépendances d'équipe. Donne
 * une date de fin prévue par tâche, sans incertitude. Pur : le jour, les
 * réglages, la calibration et (en option) les efforts et la capacité sont des
 * paramètres ; aucun hasard.
 *
 * **Point d'entrée de la simulation** (`equipe-simulation.md`) : [build] accepte
 * une table d'efforts et une fonction de capacité qui remplacent celles du plan
 * déterministe, pour rejouer **le même plan** avec des valeurs tirées.
 *
 * Posage (simulation à événements, tous les membres ensemble, jour par jour) :
 * à chaque instant libre, un membre prend la **première** tâche de son ordre
 * (`Scheduling.sortKey`, urgence héritée des dépendances comme la vue Jour)
 * dont tous les bloqueurs d'équipe sont finis ; si aucune ne l'est, il attend
 * (capacité perdue, comptée « attente »). Une tâche commencée n'est jamais
 * interrompue.
 */
object LoadPlan {
    /** Garde-fou : le posage s'arrête 2 ans (730 jours) après le jour du plan ; au-delà, « hors horizon ». */
    const val GUARD_DAYS = 730

    private const val EPS = 1e-9

    /** Sort d'une tâche dans le plan. */
    enum class Placement {
        /** Posée : [PlannedTask.start] et [PlannedTask.end] sont connus. */
        PLANNED,

        /** Pas finie dans les 2 ans du garde-fou (capacité nulle, membre absent en permanence…). */
        OUT_OF_HORIZON,

        /** Bloquée, directement ou non, par une tâche d'équipe ouverte qui n'est assignée à aucun membre actif : aucune date possible. */
        BLOCKED_BY_BACKLOG,
    }

    /**
     * Une tâche posée. [start] / [end] : premier et dernier jour travaillés
     * (`null` si [placement] n'est pas [Placement.PLANNED], [start] peut être
     * connu sans [end] quand la tâche est commencée puis dépasse le garde-fou).
     * [hours] : effort restant posé, valeur par défaut comprise pour une tâche
     * [unestimated]. [late] : la fin prévue dépasse l'échéance (ou, pour une
     * tâche non posée avec échéance, elle ne peut pas la tenir) ; [lateDays] :
     * de combien, en jours ouvrés (au moins 1 si en retard et posée, 0
     * sinon).
     */
    data class PlannedTask(
        val taskId: Long,
        val memberId: Long,
        val start: LocalDate?,
        val end: LocalDate?,
        val late: Boolean,
        val lateDays: Int,
        val effortSource: EffortSource,
        val unestimated: Boolean,
        val hours: Double,
        val placement: Placement = Placement.PLANNED,
    )

    /**
     * Une semaine de l'horizon (la première commence aujourd'hui, les autres le
     * lundi ; toutes finissent le dimanche). [hours] : charge posée dans la
     * semaine, tâches estimées ; [assumedHours] : celle des tâches non estimées
     * (valeur par défaut) ; [capacityHours] : capacité de la semaine.
     */
    data class WeekLoad(
        val start: LocalDate,
        val end: LocalDate,
        val capacityHours: Double,
        val hours: Double,
        val assumedHours: Double,
    )

    /**
     * Plan d'un membre. [tasks] : ses tâches ouvertes dans l'ordre du plan.
     * [loadHours] : heures restantes assignées, **tâches estimées seulement** ;
     * [assumedHours] : valeur par défaut posée pour les [unestimatedCount]
     * tâches non estimées (jamais mêlée à la charge). [weeks] : par semaine de
     * l'horizon. [byCategory] : [loadHours] par `taskType` (`""` = sans
     * catégorie). [waitHours] : capacité perdue à attendre un bloqueur.
     * [capacityHours] : capacité sur l'horizon.
     */
    data class MemberPlan(
        val memberId: Long,
        val tasks: List<PlannedTask>,
        val capacityHours: Double,
        val loadHours: Double,
        val assumedHours: Double,
        val unestimatedCount: Int,
        val weeks: List<WeekLoad>,
        val byCategory: Map<String, Double>,
        val waitHours: Double,
    ) {
        /** Charge posée, valeur par défaut des non estimées comprise (pour comparer des membres entre eux). */
        val plannedHours: Double get() = loadHours + assumedHours
    }

    /** Résultat : [horizonStart] = jour du plan, [horizonEnd] = dimanche de la dernière semaine. */
    data class Result(
        val day: LocalDate,
        val horizonEnd: LocalDate,
        val members: List<MemberPlan>,
        val tasks: List<PlannedTask>,
    ) {
        private val byMember = members.associateBy { it.memberId }
        private val byTask = tasks.associateBy { it.taskId }

        fun of(memberId: Long): MemberPlan? = byMember[memberId]

        fun task(taskId: Long): PlannedTask? = byTask[taskId]
    }

    /**
     * Construit le plan.
     *
     * @param snapshot la base complète ; comptent les tâches d'équipe à faire
     * assignées à un membre actif.
     * @param day aujourd'hui (début du posage).
     * @param calibration `Effort.teamCalibration`.
     * @param horizonWeeks horizon d'affichage (semaines par membre et capacité) ;
     * le posage, lui, va jusqu'au garde-fou.
     * @param todayFraction part restante de la journée de « moi » aujourd'hui
     * (`Capacity.todayFraction`) ; sans effet si [capacity] est fourni.
     * @param efforts **surcharge** : heures restantes par identifiant de tâche,
     * à la place de `Effort.planned` (une tâche absente garde son effort).
     * @param capacity **surcharge** : heures d'un membre un jour donné, à la
     * place de `Capacity.dailyHours`.
     */
    fun build(
        snapshot: KairosSnapshot,
        day: LocalDate,
        calibration: List<Calibration> = emptyList(),
        horizonWeeks: Int = (snapshot.settings.team ?: TeamSettings()).horizonWeeks,
        todayFraction: Double = 1.0,
        efforts: Map<Long, Double>? = null,
        capacity: ((TeamMember, LocalDate) -> Double)? = null,
    ): Result {
        val settings = snapshot.settings
        val holidays = Workdays.holidaysFor(day, settings.holidaysFr, settings.extraHolidays)
        val members = TeamMembers.active(snapshot.members)
        val memberIds = members.mapTo(HashSet()) { it.id }
        val horizonEnd = Capacity.horizonEnd(day, horizonWeeks)

        // Capacité d'un membre un jour : la surcharge, sinon le calcul de Capacity (absences du membre pré-filtrées).
        val absencesOf = members.associate { m -> m.id to snapshot.absences.filter { it.memberId == m.id } }
        val capacityOf: (TeamMember, LocalDate) -> Double = capacity ?: { m, d ->
            Capacity.dailyHours(m, d, holidays, absencesOf.getValue(m.id), settings, if (m.isSelf && d == day) todayFraction else 1.0)
        }

        // Tâches et dépendances d'équipe (cycles neutralisés comme dans la vue Jour).
        val teamTodo = snapshot.tasks.filter { it.space == TaskSpace.TEAM && it.status == TaskStatus.TODO }
        val teamTodoIds = teamTodo.mapTo(HashSet()) { it.id }
        val edges = Dependencies.acyclicEdges(
            snapshot.dependencies
                .filter { it.taskId in teamTodoIds && it.blockerId in teamTodoIds }
                .map { Dependencies.Edge(blocked = it.taskId, blocker = it.blockerId) },
        )
        val blockersOf = HashMap<Long, MutableList<Long>>()
        for (e in edges) blockersOf.getOrPut(e.blocked) { mutableListOf() } += e.blocker

        val own = teamTodo.associate { it.id to Scheduling.sortKey(it, day, settings) }
        val effective = Dependencies.derivedUrgency(edges, own)

        val assigned = teamTodo.filter { it.assigneeId in memberIds }
        val assignedIds = assigned.mapTo(HashSet()) { it.id }

        // Bloquées, directement ou non, par une tâche d'équipe ouverte que personne ne porte : pas de date possible.
        val unreachable = HashSet<Long>()
        do {
            var changed = false
            for (t in assigned) {
                if (t.id in unreachable) continue
                if (blockersOf[t.id].orEmpty().any { it !in assignedIds || it in unreachable }) {
                    unreachable += t.id
                    changed = true
                }
            }
        } while (changed)

        val efforts0 = assigned.associate { it.id to Effort.planned(it, calibration, settings) }
        fun hoursOf(id: Long): Double = (efforts?.get(id) ?: efforts0.getValue(id).hours).coerceAtLeast(0.0)

        // Ordre de chaque membre : clé effective (urgence héritée), tâches posables seulement.
        val order = members.associate { m ->
            m.id to assigned.filter { it.assigneeId == m.id }
                .sortedBy { effective[it.id] ?: own.getValue(it.id) }
        }
        val pending = members.associate { m -> m.id to order.getValue(m.id).filter { it.id !in unreachable }.mapTo(ArrayList()) { it.id } }
        val owner = assigned.associate { it.id to it.assigneeId!! }

        // État du posage.
        val startOn = HashMap<Long, LocalDate>()
        val endOn = HashMap<Long, LocalDate>()
        val left = HashMap<Long, Double>()
        val current = HashMap<Long, Long>()
        val waited = members.associate { it.id to 0.0 }.toMutableMap()

        // Semaines de l'horizon.
        val monday = day.minus(DatePeriod(days = Workdays.weekday(day)))
        val weekCount = monday.daysUntil(horizonEnd) / 7 + 1
        val weekCap = members.associate { it.id to DoubleArray(weekCount) }
        val weekKnown = members.associate { it.id to DoubleArray(weekCount) }
        val weekAssumed = members.associate { it.id to DoubleArray(weekCount) }

        fun startable(id: Long, member: Long, d: LocalDate): Boolean = blockersOf[id].orEmpty().all { b ->
            val e = endOn[b]
            e != null && (e < d || (e == d && owner[b] == member))
        }

        var remainingTasks = pending.values.sumOf { it.size }
        val guardEnd = day.plus(DatePeriod(days = GUARD_DAYS))
        var d = day
        while (d <= guardEnd && (remainingTasks > 0 || d <= horizonEnd)) {
            val week = if (d <= horizonEnd) monday.daysUntil(d) / 7 else -1
            for (m in members) {
                var cap = capacityOf(m, d)
                if (cap <= EPS) continue
                if (week >= 0) weekCap.getValue(m.id)[week] += cap
                val queue = pending.getValue(m.id)
                while (cap > EPS) {
                    var id = current[m.id]
                    if (id == null) {
                        val index = queue.indexOfFirst { startable(it, m.id, d) }
                        if (index < 0) {
                            if (queue.isNotEmpty()) waited[m.id] = waited.getValue(m.id) + cap
                            break
                        }
                        id = queue.removeAt(index)
                        current[m.id] = id
                        startOn[id] = d
                        left[id] = hoursOf(id)
                    }
                    val used = minOf(cap, left.getValue(id))
                    if (used > 0.0 && week >= 0) {
                        val bucket = if (efforts0.getValue(id).unestimated) weekAssumed else weekKnown
                        bucket.getValue(m.id)[week] += used
                    }
                    cap -= used
                    val rest = left.getValue(id) - used
                    left[id] = rest
                    if (rest <= EPS) {
                        endOn[id] = d
                        current.remove(m.id)
                        remainingTasks--
                    } else {
                        break
                    }
                }
            }
            d = d.plus(DatePeriod(days = 1))
        }

        // Sortie.
        fun planned(task: Task, memberId: Long): PlannedTask {
            val e = efforts0.getValue(task.id)
            val end = endOn[task.id]
            val deadline = task.deadline
            val placement = when {
                task.id in unreachable -> Placement.BLOCKED_BY_BACKLOG
                end == null -> Placement.OUT_OF_HORIZON
                else -> Placement.PLANNED
            }
            val late = if (end != null) deadline != null && end > deadline else deadline != null
            val lateDays = if (end != null && late) maxOf(1, Workdays.businessDaysBetween(deadline!!, end, holidays)) else 0
            return PlannedTask(task.id, memberId, startOn[task.id], end, late, lateDays, e.source, e.unestimated, hoursOf(task.id), placement)
        }

        val memberPlans = members.map { m ->
            val tasks = order.getValue(m.id).map { planned(it, m.id) }
            val byId = order.getValue(m.id).associateBy { it.id }
            val known = tasks.filter { !it.unestimated }
            val weeks = (0 until weekCount).map { w ->
                val start = if (w == 0) day else monday.plus(DatePeriod(days = 7 * w))
                WeekLoad(
                    start = start,
                    end = monday.plus(DatePeriod(days = 7 * w + 6)),
                    capacityHours = weekCap.getValue(m.id)[w],
                    hours = weekKnown.getValue(m.id)[w],
                    assumedHours = weekAssumed.getValue(m.id)[w],
                )
            }
            val byCategory = LinkedHashMap<String, Double>()
            for (t in known) {
                val type = byId.getValue(t.taskId).taskType
                byCategory[type] = (byCategory[type] ?: 0.0) + t.hours
            }
            MemberPlan(
                memberId = m.id,
                tasks = tasks,
                capacityHours = weeks.sumOf { it.capacityHours },
                loadHours = known.sumOf { it.hours },
                assumedHours = tasks.filter { it.unestimated }.sumOf { it.hours },
                unestimatedCount = tasks.count { it.unestimated },
                weeks = weeks,
                byCategory = byCategory,
                waitHours = waited.getValue(m.id),
            )
        }
        return Result(day, horizonEnd, memberPlans, memberPlans.flatMap { it.tasks })
    }
}
