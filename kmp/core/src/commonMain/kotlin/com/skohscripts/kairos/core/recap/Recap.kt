package com.skohscripts.kairos.core.recap

import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.engine.Recurrence
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Bilan d'une période, semaine ou mois (docs/spec/bilan.md, issue #233) : ce qui a été fait, ce qui a avancé sans finir,
 * les créneaux occupés qui ont eu lieu, ce qui reste à échéance, et où est passé le temps chronométré. De quoi dire en
 * réunion « j'ai fait ceci, on a parlé de cela, il reste ceci ».
 */
data class Recap(
    val start: LocalDate,
    val end: LocalDate,
    val done: List<Line>,
    val advanced: List<Line>,
    val slots: List<Slot>,
    val left: List<Task>,
    /** Parmi [left], celles dont l'échéance est déjà passée. */
    val late: Int,
    val spentMinutes: Int,
    val spentByType: Map<String, Int>,
) {
    /** Une tâche et ses minutes chronométrées **dans la période** (0 : pas de chrono). */
    data class Line(val task: Task, val minutes: Int)

    /** Créneaux de même titre : sept « Déjeuner » font une ligne « ×7 », pas sept. */
    data class Slot(val title: String, val count: Int, val minutes: Int)

    val points: Int get() = done.sumOf { it.task.fibonacciPoints ?: 0 }
    val slotMinutes: Int get() = slots.sumOf { it.minutes }
    val empty: Boolean get() = done.isEmpty() && advanced.isEmpty() && slots.isEmpty() && left.isEmpty() && spentMinutes == 0

    companion object {
        fun build(snapshot: KairosSnapshot, start: LocalDate, end: LocalDate, now: Instant, timeZone: TimeZone, filter: DayFilter = DayFilter()): Recap {
            val today = now.toLocalDateTime(timeZone).date
            val kept = snapshot.tasks.filter { filter.matches(it) }
            val keptIds = kept.mapTo(HashSet()) { it.id }
            // Sans filtre, toutes les sessions comptent, même celles d'une tâche disparue (le total de la semaine d'avant le bilan).
            val sessions = TimeTracking.sessionsInRange(snapshot.workSessions, start, end, timeZone).filter { !filter.active || it.taskId in keptIds }
            val minutes = TimeTracking.spentMinutesByTask(sessions, now)
            val holidays = Workdays.holidaysFor(start, snapshot.settings.holidaysFr, snapshot.settings.extraHolidays)
            val blocks = snapshot.timeBlocks.filter { it.recurrence == BlockRecurrence.NONE } +
                Recurrence.expandRecurringBlocks(snapshot.timeBlocks.filter { it.recurrence != BlockRecurrence.NONE }, start, end, holidays, snapshot.blockExceptions)
            val left = kept.filter { it.status == TaskStatus.TODO && it.deadline?.let { d -> d in start..end } == true }
                .sortedWith(compareBy({ it.deadline }, { it.title }))
            return Recap(
                start = start,
                end = end,
                done = kept.filter { it.status == TaskStatus.DONE && it.updatedAt.toLocalDateTime(timeZone).date in start..end }
                    .sortedWith(compareBy({ it.projectTag }, { it.title }))
                    .map { Line(it, minutes[it.id] ?: 0) },
                advanced = kept.filter { it.status == TaskStatus.TODO && (minutes[it.id] ?: 0) > 0 }
                    .map { Line(it, minutes.getValue(it.id)) }
                    .sortedWith(compareBy({ -it.minutes }, { it.task.title })),
                // Un bilan dit ce qui a eu lieu : créneaux occupés déjà terminés ; le deep work se lit dans les tâches.
                slots = blocks.filter { it.kind == BlockKind.BUSY && it.start.date in start..end && it.end.toInstant(timeZone) <= now }
                    .groupBy { it.title }
                    .map { (title, same) -> Slot(title, same.size, same.sumOf { ((it.end.toInstant(timeZone) - it.start.toInstant(timeZone)).inWholeMinutes).toInt().coerceAtLeast(0) }) }
                    .sortedWith(compareBy({ -it.minutes }, { it.title })),
                left = left,
                late = left.count { it.deadline!! < today },
                spentMinutes = TimeTracking.totalMinutes(sessions, now),
                spentByType = TimeTracking.spentMinutesByType(sessions, snapshot.tasks.associate { it.id to it.taskType }, now)
                    .filter { (type, spent) -> type.isNotEmpty() && spent > 0 }
                    .entries.sortedByDescending { it.value }.associate { it.key to it.value },
            )
        }
    }
}
