package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.time.Instant

/**
 * Récurrence des tâches et des créneaux (docs/spec/recurrence.md) : portage
 * de `app/tasks_recurrence.py` (Kairos 2). Pur : les occurrences à créer sont
 * rendues, le dépôt les enregistre.
 */
object Recurrence {
    /** Règles recréées à la complétion. */
    val COMPLETION_RULES = setOf(TaskRecurrence.DAILY, TaskRecurrence.WEEKDAYS, TaskRecurrence.WEEKLY, TaskRecurrence.MONTHLY)

    /** Prochaine échéance après [base] ; [dayOfWeek] (0 = lundi) ancre `weekly`. */
    fun nextDeadline(rule: TaskRecurrence, base: LocalDate, dayOfWeek: Int? = null): LocalDate = when (rule) {
        TaskRecurrence.DAILY -> base.plus(DatePeriod(days = 1))
        TaskRecurrence.WEEKDAYS -> {
            var step = base.plus(DatePeriod(days = 1))
            while (Workdays.weekday(step) >= 5) step = step.plus(DatePeriod(days = 1))
            step
        }
        TaskRecurrence.WEEKLY -> {
            val target = dayOfWeek ?: Workdays.weekday(base)
            var delta = (target - Workdays.weekday(base)).mod(7)
            if (delta == 0) delta = 7
            base.plus(DatePeriod(days = delta))
        }
        TaskRecurrence.MONTHLY -> {
            // +1 mois, jour borné à la fin du mois cible (31 janv. → 28/29 févr.).
            val december = base.month.ordinal == 11
            val year = if (december) base.year + 1 else base.year
            val month = if (december) 1 else base.month.ordinal + 2
            LocalDate(year, month, minOf(base.day, daysInMonth(year, month)))
        }
        else -> throw IllegalArgumentException("Règle de récurrence inconnue : $rule")
    }

    fun daysInMonth(year: Int, month: Int): Int {
        val first = LocalDate(year, month, 1)
        val next = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
        return first.daysUntil(next)
    }

    private fun shiftPinned(pinned: LocalDateTime?, newDate: LocalDate): LocalDateTime? =
        pinned?.let { newDate.atTime(it.time) }

    /**
     * Occurrence suivante d'une tâche récurrente qui vient d'être terminée
     * (`spawn_next_occurrence`), ou `null` : règle non récurrente, ou occurrence
     * identique (titre, règle, échéance) déjà à faire dans [existing].
     * L'identifiant rendu vaut 0 (attribué par la base).
     */
    fun nextOccurrence(task: Task, today: LocalDate, existing: List<Task>, now: Instant): Task? {
        if (task.recurrence !in COMPLETION_RULES) return null
        val base = maxOf(task.deadline ?: today, today)
        val anchor = if (task.recurrence == TaskRecurrence.WEEKLY) {
            task.recurrenceDayOfWeek ?: task.deadline?.let(Workdays::weekday)
        } else {
            null
        }
        val deadline = nextDeadline(task.recurrence, base, anchor)
        val duplicate = existing.any {
            it.title == task.title && it.recurrence == task.recurrence && it.deadline == deadline && it.status == TaskStatus.TODO
        }
        if (duplicate) return null
        return Task(
            id = 0,
            title = task.title,
            description = task.description,
            priority = task.priority,
            fibonacciPoints = task.fibonacciPoints,
            taskType = task.taskType,
            deadline = deadline,
            scheduledDate = deadline,
            pinnedStart = shiftPinned(task.pinnedStart, deadline),
            projectTag = task.projectTag,
            estimatedMinutes = task.estimatedMinutes,
            recurrence = task.recurrence,
            recurrenceDayOfWeek = anchor,
            parentId = task.parentId,
            createdAt = now,
            updatedAt = now,
        )
    }

    /** « Décaler » : jour ouvré suivant l'échéance, ou aujourd'hui si absente ou dépassée. */
    fun nextSnoozeDate(deadline: LocalDate?, today: LocalDate, holidays: Set<LocalDate> = emptySet()): LocalDate {
        val base = if (deadline != null && deadline > today) deadline else today
        return Workdays.addBusinessDays(base, 1, holidays)
    }

    fun period(day: LocalDate): String = "${day.year.toString().padStart(4, '0')}-${(day.month.ordinal + 1).toString().padStart(2, '0')}"

    /**
     * Occurrences du mois de [today] des séries « le N du mois »
     * (`ensure_calendar_occurrences`) : une par série `(titre, jour)` pas encore
     * couverte ce mois-ci, échéance reculée au jour ouvré précédent si besoin,
     * champs hérités du membre le plus récent. Identifiants à 0. Jamais de
     * rattrapage des mois passés.
     */
    fun calendarOccurrences(tasks: List<Task>, today: LocalDate, holidays: Set<LocalDate>, now: Instant): List<Task> {
        val currentPeriod = period(today)
        val series = LinkedHashMap<Pair<String, Int>, MutableList<Task>>()
        tasks.filter { it.recurrence == TaskRecurrence.MONTHLY_ON_DAY && it.recurrenceDayOfMonth != null }
            .forEach { series.getOrPut(it.title to it.recurrenceDayOfMonth!!) { mutableListOf() } += it }
        return series.mapNotNull { (key, members) ->
            val covered = members.any { m -> m.recurrencePeriod == currentPeriod || (m.deadline != null && period(m.deadline) == currentPeriod) }
            if (covered) return@mapNotNull null
            val (title, dayOfMonth) = key
            val rep = members.maxBy { it.id }
            val month = today.month.ordinal + 1
            val target = LocalDate(today.year, month, minOf(dayOfMonth, daysInMonth(today.year, month)))
            val deadline = Workdays.onOrBeforeBusinessDay(target, holidays)
            Task(
                id = 0,
                title = title,
                description = rep.description,
                priority = rep.priority,
                fibonacciPoints = rep.fibonacciPoints,
                taskType = rep.taskType,
                deadline = deadline,
                scheduledDate = deadline,
                pinnedStart = shiftPinned(rep.pinnedStart, deadline),
                projectTag = rep.projectTag,
                estimatedMinutes = rep.estimatedMinutes,
                recurrence = TaskRecurrence.MONTHLY_ON_DAY,
                recurrenceDayOfMonth = dayOfMonth,
                recurrencePeriod = currentPeriod,
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    /**
     * Occurrences des créneaux récurrents sur `[rangeStart, rangeEnd]` : le
     * créneau stocké est le modèle (date d'origine, heures), jamais avant son
     * origine. Les occurrences gardent l'identifiant du modèle.
     */
    fun expandRecurringBlocks(templates: List<TimeBlock>, rangeStart: LocalDate, rangeEnd: LocalDate): List<TimeBlock> {
        val out = mutableListOf<TimeBlock>()
        for (tpl in templates) {
            if (tpl.recurrence == BlockRecurrence.NONE) continue
            val origin = tpl.start.date
            val durationMinutes = minutesBetween(tpl.start, tpl.end)
            var day = maxOf(rangeStart, origin)
            while (day <= rangeEnd) {
                val recurs = when (tpl.recurrence) {
                    BlockRecurrence.DAILY -> true
                    BlockRecurrence.WEEKDAYS -> Workdays.weekday(day) < 5
                    BlockRecurrence.WEEKLY -> Workdays.weekday(day) == Workdays.weekday(origin)
                    BlockRecurrence.NONE -> false
                }
                if (recurs) {
                    val start = day.atTime(tpl.start.time)
                    out += tpl.copy(start = start, end = start.plusMinutes(durationMinutes))
                }
                day = day.plus(DatePeriod(days = 1))
            }
        }
        return out
    }
}
