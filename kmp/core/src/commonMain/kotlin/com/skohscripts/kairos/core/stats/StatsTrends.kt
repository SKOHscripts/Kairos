package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Évolutions dans le temps (docs/spec/statistiques.md § Évolutions, issue #112) : le temps chronométré jour par jour (semaine
 * par semaine au-delà de [DAILY_MAX_WEEKS]) et le biais d'estimation semaine par semaine, sur la fenêtre des statistiques
 * (celle du tableau de bord : du lundi d'il y a `semaines - 1` semaines à aujourd'hui). Pur : jour, instant et fuseau en
 * paramètres ; les entrées sont celles des filtres (`StatsQuery.Input`).
 */
object StatsTrends {
    /** Au-delà, le temps chronométré passe à une barre par semaine : 56 barres tiennent encore à 360 dp. */
    const val DAILY_MAX_WEEKS = 8

    /** Semaines gardées au plus (période « Tout ») : comme le débit hebdomadaire. */
    const val MAX_WEEKS = 26

    /** Une barre : la période qui commence le [start] (un jour, ou un lundi) et ses minutes chronométrées. */
    data class TrackedPoint(val start: LocalDate, val minutes: Int)

    /**
     * Temps chronométré : [daily] vrai = une barre par jour, sinon par semaine (lundi). [total] = somme ; [active] = barres
     * non nulles ; [busiest] = la plus haute (la plus ancienne à égalité), `null` sans temps.
     */
    data class Tracked(val daily: Boolean, val points: List<TrackedPoint>) {
        val total: Int get() = points.sumOf { it.minutes }
        val active: Int get() = points.count { it.minutes > 0 }
        val busiest: TrackedPoint? get() = points.filter { it.minutes > 0 }.maxByOrNull { it.minutes }
        val averageActive: Int? get() = if (active == 0) null else TaskStats.pyRound(total.toDouble() / active)
    }

    /**
     * Biais d'une semaine : tâches faites cette semaine-là ayant une estimation et du temps réel ([count]), leurs totaux,
     * et `réel ÷ estimé` ([ratio], `null` sans tâche). Sous [TaskStats.MIN_SAMPLE] tâches : « peu fiable ».
     */
    data class WeekBias(val weekStart: LocalDate, val count: Int, val estimatedMinutes: Int, val realMinutes: Int) {
        val ratio: Double? get() = if (count == 0 || estimatedMinutes == 0) null else realMinutes.toDouble() / estimatedMinutes
        val reliable: Boolean get() = count >= TaskStats.MIN_SAMPLE
    }

    data class Result(val tracked: Tracked, val bias: List<WeekBias>) {
        /** Au moins une barre ou une semaine mesurée : sinon les cartes disent qu'il n'y a rien. */
        val hasTracked: Boolean get() = tracked.total > 0
        val hasBias: Boolean get() = bias.any { it.count > 0 }
    }

    fun compute(tasks: List<Task>, sessions: List<WorkSession>, today: LocalDate, windowWeeks: Int, now: Instant, tz: TimeZone): Result {
        val weeks = windowWeeks.coerceIn(1, MAX_WEEKS)
        return Result(tracked(sessions, today, weeks, now, tz), weeklyBias(tasks, sessions, today, weeks, now, tz))
    }

    private fun windowStart(today: LocalDate, weeks: Int): LocalDate = TaskStats.monday(today).minus(DatePeriod(days = 7 * (weeks - 1)))

    /** Minutes des sessions **commencées** chaque jour (ou semaine) de la fenêtre, date locale, comme le tableau de bord. */
    fun tracked(sessions: List<WorkSession>, today: LocalDate, weeks: Int, now: Instant, tz: TimeZone): Tracked {
        val start = windowStart(today, weeks)
        val daily = weeks <= DAILY_MAX_WEEKS
        val byDay = HashMap<LocalDate, Int>()
        for (s in TimeTracking.sessionsInRange(TimeTracking.withoutOverlaps(sessions, now), start, today, tz)) {
            val day = s.startedAt.toLocalDateTime(tz).date
            val key = if (daily) day else TaskStats.monday(day)
            byDay[key] = (byDay[key] ?: 0) + TimeTracking.sessionMinutes(s, now)
        }
        val points = if (daily) {
            (0..start.daysUntil(today)).map { i -> start.plus(DatePeriod(days = i)).let { TrackedPoint(it, byDay[it] ?: 0) } }
        } else {
            (0 until weeks).map { i -> start.plus(DatePeriod(days = 7 * i)).let { TrackedPoint(it, byDay[it] ?: 0) } }
        }
        return Tracked(daily, points)
    }

    /**
     * Biais semaine par semaine : pour chaque semaine de la fenêtre, les tâches **terminées** cette semaine (date de fin =
     * dernière modification, comme le débit) ayant une estimation non nulle et du temps réel (sessions et temps saisi, comme
     * le biais global). La somme des semaines redonne le biais global restreint à la fenêtre.
     */
    fun weeklyBias(tasks: List<Task>, sessions: List<WorkSession>, today: LocalDate, weeks: Int, now: Instant, tz: TimeZone): List<WeekBias> {
        val start = windowStart(today, weeks)
        val spent = TimeTracking.spentMinutesByTask(sessions, now, tasks)
        val acc = HashMap<LocalDate, IntArray>()
        for (t in tasks) {
            if (t.status != TaskStatus.DONE) continue
            val estimate = t.estimatedMinutes ?: continue
            if (estimate == 0) continue
            val real = spent[t.id] ?: 0
            if (real <= 0) continue
            val done = t.updatedAt.toLocalDateTime(tz).date
            if (done < start || done > today) continue
            val cell = acc.getOrPut(TaskStats.monday(done)) { IntArray(3) }
            cell[0]++
            cell[1] += estimate
            cell[2] += real
        }
        return (0 until weeks).map { i ->
            val week = start.plus(DatePeriod(days = 7 * i))
            val cell = acc[week]
            WeekBias(week, cell?.get(0) ?: 0, cell?.get(1) ?: 0, cell?.get(2) ?: 0)
        }
    }
}
