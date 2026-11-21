package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.stats.InterruptionStats
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

/**
 * Dérangements attendus (docs/spec/derangements.md § Dérangements attendus, issue #272) : la part de temps que chaque heure de la
 * journée perd d'habitude en dérangements, apprise des 8 semaines complètes d'avant la semaine courante. Pur. Le deep work n'est
 * jamais dérangé (hypothèse du propriétaire) : il n'entre ni dans l'exposition ni dans l'attendu.
 *
 * [rates] : 24 valeurs de 0 à [CAP] ; toutes nulles ([NONE]) sous le seuil d'historique.
 */
class ExpectedInterruptions(val rates: List<Double>) {
    init {
        require(rates.size == 24)
    }

    /** Aucun dérangement attendu : le calcul est celui d'avant. */
    val none: Boolean get() = rates.all { it == 0.0 }

    fun rate(hour: Int): Double = rates[hour]

    /** Minutes de dérangement attendues sur `[from ; to[`, hors des plages [deepWork]. */
    fun expected(from: LocalDateTime, to: LocalDateTime, deepWork: List<Pair<LocalDateTime, LocalDateTime>> = emptyList()): Double {
        if (none || to <= from) return 0.0
        var total = 0.0
        var t = from
        while (t < to) {
            if (deepWork.none { (a, b) -> t >= a && t < b }) total += rates[t.hour]
            t = t.plusMinutes(1)
        }
        return total
    }

    /**
     * Durée occupée par une tâche de [minutes] de travail commencée à [start] hors deep work : la plus petite durée `d'` telle que
     * le travail fait, minute par minute (`1 − rate(h)`), atteigne [minutes]. Sans dérangement attendu, `d' = minutes`.
     */
    fun stretched(minutes: Int, start: LocalDateTime): Int {
        if (none || minutes <= 0) return minutes
        var done = 0.0
        var elapsed = 0
        var t = start
        // Garde : au plafond (0,5), une tâche double au plus.
        while (done + 1e-9 < minutes && elapsed < minutes * 2 + 1) {
            done += 1.0 - rates[t.hour]
            elapsed++
            t = t.plusMinutes(1)
        }
        return elapsed
    }

    companion object {
        /** Semaines d'historique, comme la prévision par tâche. */
        const val HISTORY_WEEKS = 8

        /** Seuils d'historique : au moins autant de dérangements et de jours actifs, sinon [NONE]. */
        const val MIN_EVENTS = 10
        const val MIN_DAYS = 5

        /** Une heure ne perd jamais plus de la moitié de son temps. */
        const val CAP = 0.5

        val NONE = ExpectedInterruptions(List(24) { 0.0 })

        /**
         * Le taux par heure appris de [snapshot] (instantané perso) avant la semaine de [today] : pour chaque jour actif (une séance
         * ou un dérangement), l'exposition de chaque heure est la part de la journée de travail (`Schedule.windowsFor`) hors deep
         * work ; les minutes de dérangement qui y tombent, divisées par l'exposition, plafonnées à [CAP].
         */
        fun learn(snapshot: KairosSnapshot, today: LocalDate, tz: TimeZone): ExpectedInterruptions {
            if (snapshot.interruptions.isEmpty()) return NONE
            val monday = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
            val first = monday.minus(DatePeriod(days = 7 * HISTORY_WEEKS))
            val last = monday.minus(DatePeriod(days = 1))
            val events = InterruptionStats.inRange(snapshot.interruptions, first, last, tz)
            val activeDays = (events.map { it.at.toLocalDateTime(tz).date } +
                TimeTracking.sessionsInRange(snapshot.workSessions, first, last, tz).map { it.startedAt.toLocalDateTime(tz).date }).toSet()
            if (events.size < MIN_EVENTS || activeDays.size < MIN_DAYS) return NONE
            val deep = InterruptionStats.deepWork(snapshot, first, last)
            val exposure = DoubleArray(24)
            val lost = DoubleArray(24)
            val byDay = events.groupBy { it.at.toLocalDateTime(tz).date }
            for (day in activeDays) {
                val windows = Schedule.windowsFor(day, snapshot.settings)
                val dayDeep = deep.filter { (a, b) -> a.date <= day && b.date >= day }
                fun working(t: LocalDateTime) = windows.any { t >= it.start && t < it.end } && dayDeep.none { (a, b) -> t >= a && t < b }
                for (w in windows) {
                    var t = w.start
                    while (t < w.end) {
                        if (working(t)) exposure[t.hour] += 1.0
                        t = t.plusMinutes(1)
                    }
                }
                for (i in byDay[day].orEmpty()) {
                    var t = i.start
                    while (t < i.at) {
                        val local = t.toLocalDateTime(tz)
                        if (local.date == day && working(local)) lost[local.hour] += 1.0
                        t += 1.minutes
                    }
                }
            }
            return ExpectedInterruptions(List(24) { h -> if (exposure[h] > 0) minOf(CAP, lost[h] / exposure[h]) else 0.0 })
        }
    }
}
