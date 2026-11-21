package com.skohscripts.kairos.core.month

import com.skohscripts.kairos.core.engine.Recurrence
import com.skohscripts.kairos.core.engine.Schedule
import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.DayMark
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.plus

/**
 * Un jour de la grille du mois (docs/spec/vue-mois.md) : [planned] = tâches à faire **prévues** ce jour (la règle de la
 * vue Semaine : programmées ce jour, ou dues ce jour sans être programmées un autre jour) ; [deadlines] = tâches à faire
 * dont l'échéance tombe ce jour, programmées ou non ; [slots] = créneaux du jour (occurrences des récurrents comprises,
 * exceptions appliquées). [plannedMinutes] = durée des tâches prévues (estimation, sinon durée par défaut des réglages) ;
 * [capacityMinutes] = plages de travail du jour moins les créneaux occupés ; [overloaded] : la durée prévue dépasse la
 * capacité. [holiday] : férié des réglages ; [dayOff] : jour libre des horaires par jour.
 */
data class MonthDay(
    val date: LocalDate,
    val planned: List<Task>,
    val deadlines: List<Task>,
    val slots: List<TimeBlock>,
    val plannedMinutes: Int,
    val capacityMinutes: Int,
    val holiday: Boolean,
    val dayOff: Boolean,
    /** Journées entières des calendriers importés (badges, issue #136) : ni capacité ni surcharge. */
    val marks: List<DayMark> = emptyList(),
) {
    val overloaded: Boolean get() = plannedMinutes > capacityMinutes
}

/**
 * Vue Mois (docs/spec/vue-mois.md) : les jours du mois de [MonthView.first], du 1er au dernier, et [leading] = nombre de
 * cases vides avant le 1er dans une grille qui commence le lundi. Pur : le jour de référence est un paramètre.
 */
data class MonthView(
    val first: LocalDate,
    val days: List<MonthDay>,
) {
    /** Cases vides avant le 1er (0 = le mois commence un lundi). */
    val leading: Int get() = Workdays.weekday(first)

    /** Semaines de la grille : des listes de 7 cases, `null` hors du mois. */
    val weeks: List<List<MonthDay?>>
        get() {
            val cells: List<MonthDay?> = List(leading) { null } + days
            val padded = cells + List((7 - cells.size % 7) % 7) { null }
            return padded.chunked(7)
        }

    companion object {
        fun build(snapshot: KairosSnapshot, anyDay: LocalDate): MonthView {
            val first = LocalDate(anyDay.year, anyDay.month, 1)
            val length = Recurrence.daysInMonth(first.year, first.month.ordinal + 1)
            val last = first.plus(DatePeriod(days = length - 1))
            val settings = snapshot.settings
            val open = snapshot.tasks.filter { it.status == TaskStatus.TODO }
            // Fériés du 1er : `holidaysFor` couvre l'année d'avant et les deux suivantes, le mois entier y est.
            val holidays = Workdays.holidaysFor(first, snapshot)
            val templates = snapshot.timeBlocks.filter { it.recurrence != BlockRecurrence.NONE }
            val occurrences = Recurrence.expandRecurringBlocks(templates, first, last, holidays, snapshot.blockExceptions)
            val oneOff = snapshot.timeBlocks.filter { it.recurrence == BlockRecurrence.NONE && it.start.date in first..last }
            val slotsByDay = (oneOff + occurrences).groupBy { it.start.date }
            val days = (0 until length).map { i ->
                val day = first.plus(DatePeriod(days = i))
                val planned = open.filter { it.scheduledDate == day || (it.deadline == day && it.scheduledDate == null) }
                    .sortedWith(compareBy<Task>({ it.priority == null }, { it.priority ?: 999 }, { it.title }))
                val slots = slotsByDay[day].orEmpty().sortedBy { it.start }
                val windows = Schedule.windowsFor(day, settings)
                val busy = slots.filter { it.kind == BlockKind.BUSY }
                val capacity = windows.sumOf { w ->
                    val total = ((w.end.hour * 60 + w.end.minute) - (w.start.hour * 60 + w.start.minute)).coerceAtLeast(0)
                    (total - Scheduling.busyMinutesInWindow(busy, w.start, w.end)).coerceAtLeast(0)
                }
                MonthDay(
                    date = day,
                    planned = planned,
                    deadlines = open.filter { it.deadline == day }.sortedWith(compareBy<Task>({ it.priority == null }, { it.priority ?: 999 }, { it.title })),
                    slots = slots,
                    plannedMinutes = planned.sumOf { it.estimatedMinutes ?: settings.defaultTaskDurationMinutes },
                    capacityMinutes = capacity,
                    holiday = day in holidays,
                    dayOff = windows.isEmpty(),
                    marks = snapshot.dayMarks.filter { it.covers(day) }.sortedBy { it.title },
                )
            }
            return MonthView(first, days)
        }

        /** Le 1er du mois d'avant ou d'après [first] ([delta] = -1 ou +1). */
        fun shift(first: LocalDate, delta: Int): LocalDate {
            val index = first.year * 12 + first.month.ordinal + delta
            return LocalDate(index.floorDiv(12), index.mod(12) + 1, 1)
        }
    }
}
