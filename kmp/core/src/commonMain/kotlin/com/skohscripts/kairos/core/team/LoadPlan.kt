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
     * Construit le plan : [prepare] puis [Prepared.run].
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
    ): Result = prepare(snapshot, day, calibration, horizonWeeks, todayFraction).run(efforts, capacity)

    /**
     * Tout ce qui ne dépend ni des efforts ni de la capacité surchargés : membres,
     * ordres, dépendances, tâches inatteignables, efforts du plan déterministe.
     * La simulation (`equipe-simulation.md`) le prépare **une fois** et rejoue
     * [Prepared.run] à chaque tirage avec des efforts et des capacités tirés : c'est
     * le même posage que le plan déterministe, sans en payer la préparation 5 000
     * fois. [build] = `prepare(...).run(efforts, capacity)`.
     */
    fun prepare(
        snapshot: KairosSnapshot,
        day: LocalDate,
        calibration: List<Calibration> = emptyList(),
        horizonWeeks: Int = (snapshot.settings.team ?: TeamSettings()).horizonWeeks,
        todayFraction: Double = 1.0,
    ): Prepared {
        val settings = snapshot.settings
        val holidays = Workdays.holidaysFor(day, settings.holidaysFr, settings.extraHolidays)
        val members = TeamMembers.active(snapshot.members)
        val memberIds = members.mapTo(HashSet()) { it.id }
        val horizonEnd = Capacity.horizonEnd(day, horizonWeeks)

        // Capacité d'un membre un jour : le calcul de Capacity (absences du membre pré-filtrées).
        val absencesOf = members.associate { m -> m.id to snapshot.absences.filter { it.memberId == m.id } }
        val defaultCapacity: (TeamMember, LocalDate) -> Double = { m, d ->
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

        // Ordre de chaque membre : clé effective (urgence héritée), tâches posables seulement.
        val order = members.associate { m ->
            m.id to assigned.filter { it.assigneeId == m.id }
                .sortedBy { effective[it.id] ?: own.getValue(it.id) }
        }
        val pending = members.associate { m -> m.id to order.getValue(m.id).filter { it.id !in unreachable }.map { it.id } }
        val owner = assigned.associate { it.id to it.assigneeId!! }

        return Prepared(
            day = day, holidays = holidays, members = members, horizonEnd = horizonEnd, defaultCapacity = defaultCapacity,
            blockersOf = blockersOf, unreachable = unreachable, efforts0 = efforts0, order = order, pending = pending, owner = owner,
            assigned = assigned,
        )
    }

    /** Heures d'un membre (rang dans la liste des membres actifs) un jour (décalage depuis le jour du plan), sans boxing. */
    internal fun interface CapacitySource {
        fun hours(member: Int, offset: Int): Double
    }

    /**
     * Le posage préparé par [prepare] ; [run] le rejoue autant de fois que voulu. Le coeur
     * (`runCore`) travaille sur des tableaux indexés par un **rang de tâche** et un rang de
     * membre, sans objet par tâche ni par jour : c'est lui que rejoue la simulation
     * (5 000 fois), et [run] n'est que son habillage par identifiants.
     */
    class Prepared internal constructor(
        private val day: LocalDate,
        private val holidays: Set<LocalDate>,
        internal val members: List<TeamMember>,
        private val horizonEnd: LocalDate,
        private val defaultCapacity: (TeamMember, LocalDate) -> Double,
        blockersOf: Map<Long, List<Long>>,
        private val unreachable: Set<Long>,
        private val efforts0: Map<Long, Effort.Planned>,
        private val order: Map<Long, List<Task>>,
        pending: Map<Long, List<Long>>,
        private val owner: Map<Long, Long>,
        assigned: List<Task>,
    ) {
        /** Tâches du plan (portées par un membre actif), dans l'ordre de [assigned] : le rang d'une tâche est sa position ici. */
        internal val taskIds: LongArray = LongArray(assigned.size) { assigned[it].id }
        private val taskIndex: Map<Long, Int> = HashMap<Long, Int>().also { m -> taskIds.forEachIndexed { i, id -> m[id] = i } }
        private val memberIndex: Map<Long, Int> = HashMap<Long, Int>().also { m -> members.forEachIndexed { i, mem -> m[mem.id] = i } }

        /** Effort restant du plan déterministe par rang de tâche. */
        internal val baseHours: DoubleArray = DoubleArray(taskIds.size) { efforts0.getValue(taskIds[it]).hours.coerceAtLeast(0.0) }

        private val ownerIndex = IntArray(taskIds.size) { memberIndex.getValue(owner.getValue(taskIds[it])) }
        private val blockers: Array<IntArray> = Array(taskIds.size) { t ->
            blockersOf[taskIds[t]].orEmpty().mapNotNull { taskIndex[it] }.toIntArray()
        }
        private val queueTemplate: Array<IntArray> = Array(members.size) { m ->
            pending.getValue(members[m].id).map { taskIndex.getValue(it) }.toIntArray()
        }
        private val unestimated = BooleanArray(taskIds.size) { efforts0.getValue(taskIds[it]).unestimated }
        private val weekday = Workdays.weekday(day)
        private val monday = day.minus(DatePeriod(days = weekday))
        private val horizonOffset = day.daysUntil(horizonEnd)
        private val weekCount = monday.daysUntil(horizonEnd) / 7 + 1

        /** Les dates du posage : décalage 0 = [day], jusqu'au garde-fou. */
        private val dates: Array<LocalDate> = Array(GUARD_DAYS + 1) { day.plus(DatePeriod(days = it)) }

        /** Rang d'une tâche dans le plan, ou `null` (tâche non posée : non assignée, faite…). */
        internal fun indexOf(taskId: Long): Int? = taskIndex[taskId]

        /**
         * Pose les tâches. [efforts] : heures restantes par tâche, à la place de celles
         * du plan déterministe (une tâche absente garde son effort) ; [capacity] : heures
         * d'un membre un jour donné, à la place de `Capacity.dailyHours`.
         */
        fun run(
            efforts: Map<Long, Double>? = null,
            capacity: ((TeamMember, LocalDate) -> Double)? = null,
        ): Result {
            val hours = DoubleArray(taskIds.size) { (efforts?.get(taskIds[it]) ?: efforts0.getValue(taskIds[it]).hours).coerceAtLeast(0.0) }
            val source = capacity ?: defaultCapacity
            return result(runCore(hours) { m, offset -> source(members[m], dates[offset]) })
        }

        /**
         * Sortie brute du posage, par rang de tâche : [startOn] et [endOn] en décalage de jour depuis le jour
         * du plan (-1 : jamais posée dans le garde-fou), [hours] posées. La simulation ne lit que cela.
         */
        internal class Outcome(
            val hours: DoubleArray,
            val startOn: IntArray,
            val endOn: IntArray,
            val waited: DoubleArray,
            val weekCap: Array<DoubleArray>,
            val weekKnown: Array<DoubleArray>,
            val weekAssumed: Array<DoubleArray>,
        )

        /** Décalage de jour (depuis le jour du plan) d'une date, négatif avant. */
        internal fun offsetOf(date: LocalDate): Int = day.daysUntil(date)

        /** Le posage lui-même : [hours] par rang de tâche (≥ 0), [capacity] par rang de membre et décalage de jour. */
        internal fun runCore(hours: DoubleArray, capacity: CapacitySource): Outcome {
            val memberCount = members.size
            val queues = Array(memberCount) { queueTemplate[it].copyOf() }
            val queueSize = IntArray(memberCount) { queueTemplate[it].size }

            // État du posage (décalages de jour ; -1 = pas encore).
            val startOn = IntArray(taskIds.size) { -1 }
            val endOn = IntArray(taskIds.size) { -1 }
            val left = DoubleArray(taskIds.size)
            val current = IntArray(memberCount) { -1 }
            val waited = DoubleArray(memberCount)

            // Semaines de l'horizon.
            val weekCap = Array(memberCount) { DoubleArray(weekCount) }
            val weekKnown = Array(memberCount) { DoubleArray(weekCount) }
            val weekAssumed = Array(memberCount) { DoubleArray(weekCount) }

            var remainingTasks = queueSize.sum()
            var offset = 0
            while (offset <= GUARD_DAYS && (remainingTasks > 0 || offset <= horizonOffset)) {
                val week = if (offset <= horizonOffset) (offset + weekday) / 7 else -1
                for (m in 0 until memberCount) {
                    var cap = capacity.hours(m, offset)
                    if (cap <= EPS) continue
                    if (week >= 0) weekCap[m][week] += cap
                    val queue = queues[m]
                    while (cap > EPS) {
                        var id = current[m]
                        if (id < 0) {
                            var index = -1
                            for (q in 0 until queueSize[m]) {
                                if (startable(queue[q], m, offset, blockers, endOn)) {
                                    index = q
                                    break
                                }
                            }
                            if (index < 0) {
                                if (queueSize[m] > 0) waited[m] += cap
                                break
                            }
                            id = queue[index]
                            queue.copyInto(queue, index, index + 1, queueSize[m])
                            queueSize[m]--
                            current[m] = id
                            startOn[id] = offset
                            left[id] = hours[id]
                        }
                        val used = minOf(cap, left[id])
                        if (used > 0.0 && week >= 0) {
                            val bucket = if (unestimated[id]) weekAssumed else weekKnown
                            bucket[m][week] += used
                        }
                        cap -= used
                        val rest = left[id] - used
                        left[id] = rest
                        if (rest <= EPS) {
                            endOn[id] = offset
                            current[m] = -1
                            remainingTasks--
                        } else {
                            break
                        }
                    }
                }
                offset++
            }
            return Outcome(hours, startOn, endOn, waited, weekCap, weekKnown, weekAssumed)
        }

        private fun result(outcome: Outcome): Result {
            val hours = outcome.hours
            val startOn = outcome.startOn
            val endOn = outcome.endOn
            val waited = outcome.waited
            val weekCap = outcome.weekCap
            val weekKnown = outcome.weekKnown
            val weekAssumed = outcome.weekAssumed

            fun planned(task: Task, memberId: Long): PlannedTask {
                val t = taskIndex.getValue(task.id)
                val e = efforts0.getValue(task.id)
                val end = if (endOn[t] >= 0) dates[endOn[t]] else null
                val deadline = task.deadline
                val placement = when {
                    task.id in unreachable -> Placement.BLOCKED_BY_BACKLOG
                    end == null -> Placement.OUT_OF_HORIZON
                    else -> Placement.PLANNED
                }
                val late = if (end != null) deadline != null && end > deadline else deadline != null
                val lateDays = if (end != null && late) maxOf(1, Workdays.businessDaysBetween(deadline!!, end, holidays)) else 0
                val start = if (startOn[t] >= 0) dates[startOn[t]] else null
                return PlannedTask(task.id, memberId, start, end, late, lateDays, e.source, e.unestimated, hours[t], placement)
            }

            val memberPlans = members.mapIndexed { mi, m ->
                val tasks = order.getValue(m.id).map { planned(it, m.id) }
                val byId = order.getValue(m.id).associateBy { it.id }
                val known = tasks.filter { !it.unestimated }
                val weeks = (0 until weekCount).map { w ->
                    val start = if (w == 0) day else monday.plus(DatePeriod(days = 7 * w))
                    WeekLoad(
                        start = start,
                        end = monday.plus(DatePeriod(days = 7 * w + 6)),
                        capacityHours = weekCap[mi][w],
                        hours = weekKnown[mi][w],
                        assumedHours = weekAssumed[mi][w],
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
                    waitHours = waited[mi],
                )
            }
            return Result(day, horizonEnd, memberPlans, memberPlans.flatMap { it.tasks })
        }

        /**
         * Une tâche est posable un jour si chaque bloqueur est fini avant ce jour, ou le jour même chez
         * le même membre (« une tâche bloquée par un autre membre commence le jour ouvré suivant »).
         */
        private fun startable(task: Int, member: Int, offset: Int, blockers: Array<IntArray>, endOn: IntArray): Boolean {
            for (b in blockers[task]) {
                val e = endOn[b]
                if (e < 0 || !(e < offset || (e == offset && ownerIndex[b] == member))) return false
            }
            return true
        }
    }
}
