package com.skohscripts.kairos.core.team.forecast

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import com.skohscripts.kairos.core.engine.Workdays

/**
 * Résultats bruts d'une tranche de tirages ([from] inclus, [count] tirages), rendus
 * par `MonteCarlo.runBatch` et consommés par [ForecastAccumulator.add]. Opaque :
 * des tableaux primitifs, pas d'objet par tâche ni par tirage.
 */
class DrawBatch internal constructor(
    val from: Int,
    val count: Int,
    /** Fin du périmètre par tirage, en jours depuis le jour de la simulation ([ForecastSamples.OUT] = hors horizon). */
    internal val ends: IntArray,
    /** Fin de chaque tâche du périmètre, par tirage, triées dans le tirage ; `null` si non gardées. */
    internal val taskEnds: ShortArray?,
    /** Par tirage et par tâche à échéance : 1 = en retard. */
    internal val late: ByteArray,
    /** Par tirage et par membre du périmètre : 1 = finit le dernier (ex æquo compris). */
    internal val last: ByteArray,
)

/**
 * Agrégateur **incrémental** : reçoit des [DrawBatch] (dans n'importe quel découpage)
 * et rend le [ForecastResult] à tout moment, y compris partiel ([result] avec
 * `interrupted`). Tous les agrégats sont des **comptes entiers** : le résultat est
 * le même en une tranche ou en vingt (docs/spec/equipe-simulation.md § Exécution).
 */
class ForecastAccumulator internal constructor(private val setup: MonteCarlo.Setup) {
    private val total = setup.runs
    private val scope = setup.scopeSize
    private val ends = IntArray(total)
    private val taskEnds: ShortArray? = if (setup.keepTaskEnds) ShortArray(total * scope) else null
    private val deadlineTasks = setup.deadlineTaskIds.size
    private val lateCounts = IntArray(deadlineTasks)
    private val lateDistribution = IntArray(4)
    private val lateSets = HashMap<List<Int>, Int>()
    private val lastCounts = IntArray(setup.memberIds.size)
    private var lateTotal = 0L
    private val present = BooleanArray(total)
    private var done = 0

    /** Tirages agrégés jusqu'ici. */
    val runs: Int get() = done

    /** Ajoute [batch] (ses tirages se rangent à leur numéro : l'ordre d'arrivée est sans effet sur le résultat). */
    fun add(batch: DrawBatch) {
        val count = minOf(batch.count, total - batch.from).coerceAtLeast(0)
        batch.ends.copyInto(ends, batch.from, 0, count)
        if (taskEnds != null && batch.taskEnds != null) batch.taskEnds.copyInto(taskEnds, batch.from * scope, 0, count * scope)
        for (i in 0 until count) {
            // Un tirage ajouté deux fois ne compte qu'une fois : l'agrégat reste celui des tirages distincts.
            if (present[batch.from + i]) continue
            present[batch.from + i] = true
            var lateInDraw = 0
            var key: ArrayList<Int>? = null
            for (d in 0 until deadlineTasks) {
                if (batch.late[i * deadlineTasks + d].toInt() != 0) {
                    lateCounts[d]++
                    lateInDraw++
                    (key ?: ArrayList<Int>().also { key = it }) += d
                }
            }
            lateDistribution[minOf(lateInDraw, 3)]++
            lateTotal += lateInDraw
            val setKey: List<Int> = key ?: emptyList()
            lateSets[setKey] = (lateSets[setKey] ?: 0) + 1
            for (m in lastCounts.indices) lastCounts[m] += batch.last[i * lastCounts.size + m].toInt()
            done++
        }
    }

    /** Le résultat sur les [runs] tirages agrégés ; [interrupted] marque un calcul arrêté avant son terme. */
    fun result(interrupted: Boolean = false): ForecastResult {
        val n = done
        // Les tirages présents, dans l'ordre de leur numéro (un résultat partiel est donc indépendant de l'ordre d'arrivée).
        val sorted = IntArray(n)
        var at = 0
        for (i in 0 until total) if (present[i]) sorted[at++] = ends[i]
        sorted.sort()
        val day = setup.day
        val finish = if (scope == 0 || n == 0) null else finishOutlook(sorted, day)

        val deadlines = setup.deadlineTaskIds.indices.map { d ->
            val late = lateCounts[d]
            val onTime = if (n == 0) 1.0 else (n - late).toDouble() / n
            DeadlineOutlook(
                taskId = setup.deadlineTaskIds[d], deadline = setup.deadlineDates[d], lateDraws = late, draws = n,
                atRisk = n > 0 && onTime * 100.0 < setup.riskPercent,
            )
        }.sortedWith(compareByDescending<DeadlineOutlook> { it.lateDraws }.thenBy { it.taskId })

        val bottleneck = setup.memberIds.indices.map { m -> MemberShare(setup.memberIds[m], lastCounts[m], n) }
            .filter { it.draws > 0 }
            .sortedWith(compareByDescending<MemberShare> { it.draws }.thenBy { it.memberId })

        val sets = lateSets.entries
            .map { (key, draws) -> LateSet(key.map { setup.deadlineTaskIds[it] }, draws) }
            .sortedWith(
                compareByDescending<LateSet> { it.draws }
                    .thenBy { it.taskIds.size }
                    .thenComparator { a, b -> compareIds(a.taskIds, b.taskIds) },
            )
        val withSets = setup.taskLevel && n > 0

        return ForecastResult(
            model = setup.model,
            day = day,
            seed = setup.seed,
            requestedRuns = total,
            runs = n,
            interrupted = interrupted,
            scopeSize = scope,
            source = setup.source,
            taskLevel = setup.taskLevel,
            finish = finish,
            deadlines = deadlines,
            bottleneck = bottleneck,
            lateDistribution = if (withSets) lateDistribution.toList() else emptyList(),
            lateSets = if (withSets) sets.take(MAX_LATE_SETS) else emptyList(),
            mostLikelyLateSet = if (withSets) sets.firstOrNull() else null,
            expectedLate = if (n == 0 || !setup.taskLevel) 0.0 else lateTotal.toDouble() / n,
            allOnTimeProbability = if (n == 0 || !setup.taskLevel) 1.0 else lateDistribution[0].toDouble() / n,
            toQualify = setup.toQualify,
            samples = ForecastSamples(sorted, taskEnds?.let { stored -> gather(stored, n) }, scope),
        )
    }

    /** Les fins par tâche des tirages présents, dans l'ordre de leur numéro. */
    private fun gather(stored: ShortArray, n: Int): ShortArray {
        if (n == total) return stored.copyOf()
        val out = ShortArray(n * scope)
        var at = 0
        for (i in 0 until total) {
            if (!present[i]) continue
            stored.copyInto(out, at * scope, i * scope, (i + 1) * scope)
            at++
        }
        return out
    }

    private fun compareIds(a: List<Long>, b: List<Long>): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val c = a[i].compareTo(b[i])
            if (c != 0) return c
        }
        return a.size.compareTo(b.size)
    }

    private fun finishOutlook(sorted: IntArray, day: LocalDate): FinishOutlook {
        fun date(percent: Int): LocalDate? {
            val offset = Percentiles.nearestRank(sorted, percent)!!
            return if (offset >= ForecastSamples.OUT) null else day.plus(DatePeriod(days = offset))
        }
        val monday = day.minus(DatePeriod(days = Workdays.weekday(day)))
        val weekday = Workdays.weekday(day)
        val finite = sorted.count { it < ForecastSamples.OUT }
        val buckets = ArrayList<WeekBucket>()
        if (finite > 0) {
            val first = (sorted[0] + weekday) / 7
            val lastWeek = (sorted[finite - 1] + weekday) / 7
            val counts = IntArray(lastWeek - first + 1)
            for (i in 0 until finite) counts[(sorted[i] + weekday) / 7 - first]++
            for (w in counts.indices) buckets += WeekBucket(monday.plus(DatePeriod(days = 7 * (first + w))), counts[w])
        }
        return FinishOutlook(date(50), date(85), date(95), buckets, sorted.size - finite)
    }

    companion object {
        /** Nombre d'ensembles de retards gardés dans le résultat. */
        const val MAX_LATE_SETS = 50
    }
}
