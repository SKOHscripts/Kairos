package com.skohscripts.kairos.core.team.forecast

import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.stats.TaskStats.Calibration
import com.skohscripts.kairos.core.team.Capacity
import com.skohscripts.kairos.core.team.LoadPlan
import kotlinx.datetime.LocalDate

/** Moteur d'un modèle : produit une tranche de tirages. */
internal interface DrawEngine {
    fun runBatch(from: Int, count: Int, setup: MonteCarlo.Setup): DrawBatch
}

/**
 * Modèle **par effort** (docs/spec/equipe-simulation.md § Moteur) : chaque tirage
 * rejoue le plan de charge (le posage de `LoadPlan.build`, préparé une fois par
 * `LoadPlan.prepare` : **le même moteur**, jamais un second ordonnanceur) avec des
 * efforts et des capacités tirés :
 *
 * - effort d'une tâche = l'effort du plan (`Effort.planned` : le restant ; la valeur par défaut pour
 *   une tâche non estimée) × un facteur d'erreur tiré par `ForecastData.errorFactor`
 *   avec le sous-générateur de son `teamUid` ;
 * - capacité d'un membre un jour = capacité de base (`Capacity.dailyHours`,
 *   précalculée **une fois** pour les 731 jours du garde-fou) × le facteur de sa
 *   semaine (si l'option « aléa de capacité » est active et que le membre a un
 *   historique), tiré par le sous-générateur (identifiant du membre, semaine).
 *
 * Sans aléa (facteurs tous à 1, pas d'aléa de capacité) le plan rejoué est
 * **exactement** le plan déterministe : mêmes nombres, mêmes dates.
 *
 * [snapshot] est la base déjà préparée (scénario appliqué, backlog réparti par la
 * suggestion si demandé). La suggestion est faite **une fois** à la préparation, pas
 * à chaque tirage : elle ne voit pas les facteurs tirés, donc son résultat serait
 * identique, et elle coûte plus d'une seconde (décision tracée dans la spec).
 */
internal class EffortModel(
    private val snapshot: KairosSnapshot,
    private val day: LocalDate,
    private val calibration: List<Calibration>,
    private val todayFraction: Double,
    private val data: ForecastData,
    private val capacityRandomness: Boolean,
    private val seed: Long,
    private val scopeIds: LongArray,
) : DrawEngine {
    private val settings = snapshot.settings

    // Le posage (ordres, dépendances, tâches inatteignables) est préparé une fois ; chaque tirage le rejoue.
    private val prepared = LoadPlan.prepare(snapshot, day, calibration, 1, todayFraction)
    private val members = prepared.members
    private val planTasks: Map<Long, Task> = snapshot.tasks.associateBy { it.id }
    private val taskCount = prepared.taskIds.size
    private val taskKeys = LongArray(taskCount) {
        ForecastRandom.fnv1a64(planTasks.getValue(prepared.taskIds[it]).teamUid ?: "task-${prepared.taskIds[it]}")
    }
    private val taskHours: DoubleArray = prepared.baseHours

    // Capacité de base par membre et par jour (décalage depuis [day]), calculée une seule fois.
    private val dayEpoch = day.toEpochDays()
    private val weekday = Workdays.weekday(day)
    private val firstWeek = (dayEpoch - weekday - 4L).floorDiv(7L)
    private val spanDays = LoadPlan.GUARD_DAYS + 2
    private val weekCount = (spanDays + weekday) / 7 + 1
    private val baseCapacity: Array<DoubleArray>
    private val randomMembers: BooleanArray

    init {
        val holidays = Workdays.holidaysFor(day, settings.holidaysFr, settings.extraHolidays)
        baseCapacity = Array(members.size) { m ->
            val member = members[m]
            val mine = snapshot.absences.filter { it.memberId == member.id }
            DoubleArray(spanDays) { offset ->
                val d = LocalDate.fromEpochDays(dayEpoch + offset)
                Capacity.dailyHours(member, d, holidays, mine, settings, if (member.isSelf && offset == 0) todayFraction else 1.0)
            }
        }
        randomMembers = BooleanArray(members.size) { capacityRandomness && data.hasCapacityHistory(members[it].id) }
    }

    private val factorCache = DoubleArray(members.size * weekCount)

    override fun runBatch(from: Int, count: Int, setup: MonteCarlo.Setup): DrawBatch {
        val n = scopeIds.size
        val deadlines = setup.deadlineTaskIds.size
        val memberCount = setup.memberIds.size
        val ends = IntArray(count)
        val taskEnds = if (setup.keepTaskEnds) ShortArray(count * n) else null
        val late = ByteArray(count * deadlines)
        val last = ByteArray(count * memberCount)

        // Rangs dans le plan des tâches du périmètre (−1 : pas posée, jamais finie), de leur membre et de leur échéance.
        val rank = IntArray(n) { prepared.indexOf(scopeIds[it]) ?: -1 }
        val owner = IntArray(n) { j ->
            val member = if (rank[j] < 0) null else planTasks.getValue(scopeIds[j]).assigneeId
            if (member == null) -1 else setup.memberIds.indexOf(member)
        }
        val deadlineOffset = IntArray(n) { j ->
            val d = planTasks[scopeIds[j]]?.deadline
            if (d == null) NO_DEADLINE else prepared.offsetOf(d)
        }
        val deadlinePosition = IntArray(n) { j -> setup.deadlineTaskIds.indexOf(scopeIds[j]) }

        val hours = DoubleArray(taskCount)
        val endOf = IntArray(n)
        for (i in 0 until count) {
            val base = ForecastRandom.drawBase(seed, from + i)
            for (k in 0 until taskCount) {
                val factor = data.errorFactor(ForecastRandom.stream(base, ForecastRandom.DOMAIN_ERROR, taskKeys[k]))
                hours[k] = taskHours[k] * factor
            }
            factorCache.fill(Double.NaN)
            val outcome = prepared.runCore(hours) { m, offset ->
                val capacity = baseCapacity[m][offset]
                if (capacity <= 0.0 || !randomMembers[m]) {
                    capacity
                } else {
                    val week = (offset + weekday) / 7
                    val slot = m * weekCount + week
                    var factor = factorCache[slot]
                    if (factor.isNaN()) {
                        val id = members[m].id
                        factor = data.capacityFactor(id, ForecastRandom.stream(base, ForecastRandom.DOMAIN_CAPACITY, id, firstWeek + week))
                        factorCache[slot] = factor
                    }
                    capacity * factor
                }
            }

            var worst = -1
            for (j in 0 until n) {
                val end = if (rank[j] < 0) -1 else outcome.endOn[rank[j]]
                // Fin « hors horizon » : jamais posée (non assignée, bloquée par le backlog, ou au-delà du garde-fou).
                val offset = if (end < 0) ForecastSamples.OUT else end
                endOf[j] = offset
                if (offset > worst) worst = offset
                val d = deadlinePosition[j]
                if (d >= 0 && (offset == ForecastSamples.OUT || offset > deadlineOffset[j])) late[i * deadlines + d] = 1
            }
            ends[i] = if (n == 0) 0 else worst
            if (taskEnds != null) {
                for (j in 0 until n) taskEnds[i * n + j] = endOf[j].toShort()
                taskEnds.sort(i * n, (i + 1) * n)
            }
            for (j in 0 until n) if (endOf[j] == worst && owner[j] >= 0) last[i * memberCount + owner[j]] = 1
        }
        return DrawBatch(from, count, ends, taskEnds, late, last)
    }

    private companion object {
        const val NO_DEADLINE = Int.MIN_VALUE
    }
}
