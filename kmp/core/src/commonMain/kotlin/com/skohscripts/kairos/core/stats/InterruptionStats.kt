package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.engine.Recurrence
import com.skohscripts.kairos.core.engine.Schedule
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.Interruption
import com.skohscripts.kairos.core.model.InterruptionSource
import com.skohscripts.kairos.core.model.KairosSnapshot
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Statistiques des dérangements (docs/spec/derangements.md § Statistiques, issue #272). Pur : l'instantané reçu est l'instantané
 * perso. Un dérangement compte dans une période si son instant `at` (fuseau de l'appareil) y tombe.
 */
object InterruptionStats {
    /** Un moment de la semaine : jour ISO (0 = lundi) et demi-journée (avant ou après midi). */
    data class Moment(val weekday: Int, val afternoon: Boolean)

    /**
     * Ce que la période dit des dérangements. [share] : part du temps de travail mesuré (chronométré + dérangements), `null` sans
     * temps mesuré ; [previousCount] / [previousMinutes] : la période précédente de même longueur ; [byHour] : nombre par heure
     * (du milieu du dérangement) ; [byWeekday] : nombre par jour ISO ; [bySource] : nombre par source (`null` = non précisée) ;
     * [longestCalmMedian] : médiane, sur les jours dérangés, de la plus longue plage de la journée de travail sans dérangement
     * (`null` sans jour dérangé) ; [deepWork] créneaux de deep work de la période, dont [deepWorkInterrupted] traversés.
     */
    data class Summary(
        val count: Int,
        val minutes: Int,
        val share: Double?,
        val previousCount: Int,
        val previousMinutes: Int,
        val byHour: List<Int>,
        val byWeekday: List<Int>,
        val bySource: Map<InterruptionSource?, Int>,
        val longestCalmMedian: Int?,
        val deepWork: Int,
        val deepWorkInterrupted: Int,
        val recent: List<Interruption>,
    ) {
        /** La source qui pèse au moins un tiers des dérangements (la plus fréquente), sinon `null`. Non précisée exclue. */
        val mainSource: InterruptionSource?
            get() = bySource.filterKeys { it != null }.maxByOrNull { it.value }?.takeIf { count > 0 && it.value * 3 >= count }?.key
    }

    /** Les dérangements de [snapshot] retenus par [filter] : sans filtre, tous ; avec, ceux dont la tâche liée passe le filtre. */
    fun kept(snapshot: KairosSnapshot, filter: DayFilter = DayFilter()): List<Interruption> {
        if (!filter.active) return snapshot.interruptions
        val ids = snapshot.tasks.filter(filter::matches).mapTo(HashSet()) { it.id }
        return snapshot.interruptions.filter { it.taskId in ids }
    }

    /** Les dérangements dont l'instant tombe dans `[start ; end]`, dates locales. */
    fun inRange(interruptions: List<Interruption>, start: LocalDate, end: LocalDate, tz: TimeZone): List<Interruption> =
        interruptions.filter { it.at.toLocalDateTime(tz).date in start..end }

    fun summary(snapshot: KairosSnapshot, start: LocalDate, end: LocalDate, now: Instant, tz: TimeZone, filter: DayFilter = DayFilter()): Summary {
        val all = kept(snapshot, filter)
        val period = inRange(all, start, end, tz)
        val length = start.daysUntil(end) + 1
        val previous = inRange(all, start.minus(DatePeriod(days = length)), start.minus(DatePeriod(days = 1)), tz)
        val minutes = period.sumOf { it.minutes }
        val taskIds = if (filter.active) snapshot.tasks.filter(filter::matches).mapTo(HashSet()) { it.id } else null
        val sessions = TimeTracking.sessionsInRange(snapshot.workSessions, start, end, tz).filter { taskIds == null || it.taskId in taskIds }
        val measured = TimeTracking.totalMinutes(sessions, now) + minutes
        val byHour = MutableList(24) { 0 }
        val byWeekday = MutableList(7) { 0 }
        for (i in period) {
            val middle = (i.at - (i.minutes * 30).seconds).toLocalDateTime(tz)
            byHour[middle.hour]++
            byWeekday[middle.date.dayOfWeek.ordinal]++
        }
        val deep = deepWork(snapshot, start, end).map { it.first.toInstant(tz) to it.second.toInstant(tz) }
        return Summary(
            count = period.size,
            minutes = minutes,
            share = if (measured > 0) minutes.toDouble() / measured else null,
            previousCount = previous.size,
            previousMinutes = previous.sumOf { it.minutes },
            byHour = byHour,
            byWeekday = byWeekday,
            bySource = period.groupingBy { it.source }.eachCount(),
            longestCalmMedian = longestCalmMedian(period, snapshot, tz),
            deepWork = deep.size,
            deepWorkInterrupted = deep.count { (from, to) -> period.any { it.start < to && it.at > from } },
            recent = period.sortedByDescending { it.at },
        )
    }

    /** Le moment (jour, demi-journée) qui réunit au moins un tiers des dérangements de [period], sinon `null`. */
    fun mainMoment(period: List<Interruption>, tz: TimeZone): Moment? {
        if (period.isEmpty()) return null
        val top = period.groupingBy {
            val middle = (it.at - (it.minutes * 30).seconds).toLocalDateTime(tz)
            Moment(middle.date.dayOfWeek.ordinal, middle.hour >= 12)
        }.eachCount().maxByOrNull { it.value } ?: return null
        return top.key.takeIf { top.value * 3 >= period.size }
    }

    /** Créneaux de deep work de `[start ; end]`, occurrences des récurrents comprises : (début, fin) en heure locale. */
    fun deepWork(snapshot: KairosSnapshot, start: LocalDate, end: LocalDate): List<Pair<LocalDateTime, LocalDateTime>> {
        val deep = snapshot.timeBlocks.filter { it.kind == BlockKind.DEEPWORK }
        val holidays = Workdays.holidaysFor(start, snapshot)
        val blocks = deep.filter { it.recurrence == BlockRecurrence.NONE } +
            Recurrence.expandRecurringBlocks(deep.filter { it.recurrence != BlockRecurrence.NONE }, start, end, holidays, snapshot.blockExceptions)
        return blocks.filter { it.start.date <= end && it.end.date >= start }.map { it.start to it.end }
    }

    /**
     * Pour chaque jour qui a au moins un dérangement, la plus longue plage de sa journée de travail (`Schedule.windowsFor`) non
     * couverte par un dérangement ; puis la médiane (minutes).
     */
    private fun longestCalmMedian(period: List<Interruption>, snapshot: KairosSnapshot, tz: TimeZone): Int? {
        val days = period.groupBy { it.at.toLocalDateTime(tz).date }
        val longest = days.mapNotNull { (day, list) ->
            val windows = Schedule.windowsFor(day, snapshot.settings)
            if (windows.isEmpty()) return@mapNotNull null
            val covered = list.map { it.start to it.at }.sortedBy { it.first }
            windows.maxOf { w ->
                var cursor = w.start.toInstant(tz)
                val stop = w.end.toInstant(tz)
                var best = 0L
                for ((from, to) in covered) {
                    if (to <= cursor || from >= stop) continue
                    if (from > cursor) best = maxOf(best, (from - cursor).inWholeMinutes)
                    if (to > cursor) cursor = to
                }
                if (stop > cursor) best = maxOf(best, (stop - cursor).inWholeMinutes)
                best
            }.toInt()
        }.sorted()
        if (longest.isEmpty()) return null
        val mid = longest.size / 2
        return if (longest.size % 2 == 1) longest[mid] else (longest[mid - 1] + longest[mid]) / 2
    }
}
