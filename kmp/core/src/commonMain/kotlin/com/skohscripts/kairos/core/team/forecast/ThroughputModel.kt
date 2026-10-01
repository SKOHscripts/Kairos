package com.skohscripts.kairos.core.team.forecast

import com.skohscripts.kairos.core.engine.Workdays
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Modèle **par débit** (docs/spec/equipe-simulation.md § Modèle « Par débit ») : sans
 * estimation, chaque tirage avance semaine par semaine à partir de la semaine
 * courante en tirant le nombre de tâches finies dans les semaines passées de
 * [history] (celles de l'équipe, ou du membre), jusqu'à finir les [scopeSize] tâches
 * du périmètre.
 *
 * - La semaine courante compte **au prorata des jours ouvrés restants** (de [day]
 *   compris) sur ceux de la semaine ; les suivantes en entier. Le cumul est réel
 *   (fractions comprises) : la `k`-ième tâche finit la semaine où il atteint `k`
 *   (à `EPS` près, pour qu'un débit constant de 5 tâches finisse exactement 20
 *   tâches en 4 semaines).
 * - La date d'une semaine est son dernier jour ouvré (le vendredi, ou le jour ouvré
 *   précédent si férié), au plus tôt [day] ; la fin du périmètre est celle de la
 *   dernière tâche.
 * - Un historique vide ou ne contenant que des semaines à zéro ne finit jamais :
 *   au-delà de [MAX_WEEKS] (520 semaines, 10 ans) le tirage est « hors horizon »,
 *   sans boucle infinie.
 * - Chaque semaine d'un tirage a son sous-générateur (graine, tirage, clé =
 *   identifiant du membre ou 0, numéro de semaine) : même indépendance d'ordre que
 *   le modèle par effort.
 */
internal class ThroughputModel(
    private val day: LocalDate,
    private val holidays: Set<LocalDate>,
    private val history: List<Int>,
    private val memberKey: Long,
    private val seed: Long,
    private val scopeSize: Int,
) : DrawEngine {
    private val dayEpoch = day.toEpochDays()
    private val weekday = Workdays.weekday(day)
    private val monday = day.minus(DatePeriod(days = weekday))

    /** Part de la semaine courante qui reste à travailler : jours ouvrés de [day] au dimanche / jours ouvrés de la semaine. */
    private val currentWeekShare: Double = run {
        var total = 0
        var remaining = 0
        for (i in 0 until 7) {
            val d = monday.plus(DatePeriod(days = i))
            if (Workdays.isWorkday(d, holidays)) {
                total++
                if (d >= day) remaining++
            }
        }
        if (total == 0) 0.0 else remaining.toDouble() / total
    }

    /** Décalage (en jours depuis [day]) du dernier jour ouvré de chaque semaine, calculé à la demande. */
    private val weekEnd = IntArray(MAX_WEEKS) { -1 }

    private fun endOffset(week: Int): Int {
        var offset = weekEnd[week]
        if (offset < 0) {
            val sunday = monday.plus(DatePeriod(days = 7 * week + 6))
            val lastWorkday = Workdays.onOrBeforeBusinessDay(sunday, holidays)
            offset = maxOf(0L, lastWorkday.toEpochDays() - dayEpoch).toInt()
            weekEnd[week] = offset
        }
        return offset
    }

    override fun runBatch(from: Int, count: Int, setup: MonteCarlo.Setup): DrawBatch {
        val n = scopeSize
        val ends = IntArray(count)
        val taskEnds = if (setup.keepTaskEnds) ShortArray(count * n) else null
        val firstWeek = (dayEpoch - weekday - 4L).floorDiv(7L)
        for (i in 0 until count) {
            val base = ForecastRandom.drawBase(seed, from + i)
            var finished = 0
            var cumulated = 0.0
            var week = 0
            while (finished < n && week < MAX_WEEKS) {
                val tasks = if (history.isEmpty()) {
                    0
                } else {
                    history[ForecastRandom.stream(base, ForecastRandom.DOMAIN_THROUGHPUT, memberKey, firstWeek + week).nextInt(history.size)]
                }
                cumulated += if (week == 0) tasks * currentWeekShare else tasks.toDouble()
                while (finished < n && cumulated + EPS >= finished + 1) {
                    if (taskEnds != null) taskEnds[i * n + finished] = endOffset(week).toShort()
                    finished++
                }
                week++
            }
            if (finished < n) {
                if (taskEnds != null) for (k in finished until n) taskEnds[i * n + k] = ForecastSamples.OUT.toShort()
                ends[i] = ForecastSamples.OUT
            } else {
                ends[i] = if (n == 0) 0 else endOffset(week - 1)
            }
        }
        return DrawBatch(from, count, ends, taskEnds, ByteArray(0), ByteArray(0))
    }

    companion object {
        /** Garde-fou : 520 semaines (10 ans) ; au-delà, « hors horizon ». */
        const val MAX_WEEKS = 520

        private const val EPS = 1e-9
    }
}
