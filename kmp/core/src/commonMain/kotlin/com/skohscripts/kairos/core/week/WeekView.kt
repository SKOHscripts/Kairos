package com.skohscripts.kairos.core.week

import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.engine.Recurrence
import com.skohscripts.kairos.core.engine.Schedule
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.DayMark
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.stats.TaskStats
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Un jour de la grille semaine : échéances à faire, tâches faites ce jour-là, créneaux, et les tâches **programmées pour**
 * ce jour ([scheduled], dont l'échéance n'est pas ce jour-là). Une tâche programmée n'est qu'à son jour programmé : une
 * tâche, une seule ligne dans la semaine (issue #106). [holiday] : férié des réglages ; [dayOff] : jour libre des
 * horaires par jour. Le menu « Déplacer » les signale sans les interdire.
 */
data class WeekDay(
    val date: LocalDate,
    val tasks: List<Task>,
    val done: List<Task>,
    val blocks: List<TimeBlock>,
    val scheduled: List<Task> = emptyList(),
    val holiday: Boolean = false,
    val dayOff: Boolean = false,
    /** Journées entières des calendriers importés (badges, issue #136). */
    val marks: List<DayMark> = emptyList(),
)

/**
 * Vue Semaine (docs/spec/vue-semaine.md) :
 * sept jours du lundi au dimanche ; tâches à faire groupées par **échéance**, sauf celles programmées un autre jour
 * (priorité d'abord, sans priorité en dernier), tâches faites par jour de
 * complétion (titre), tâches programmées pour le jour, créneaux du jour (occurrences des récurrents comprises).
 * Le temps chronométré de la semaine est dans le bilan (`Recap`, docs/spec/bilan.md). Le filtre réduit les
 * listes, jamais la grille.
 */
data class WeekView(
    val monday: LocalDate,
    val days: List<WeekDay>,
) {
    companion object {
        fun build(snapshot: KairosSnapshot, anyDay: LocalDate, now: Instant, timeZone: TimeZone, filter: DayFilter = DayFilter()): WeekView {
            val monday = TaskStats.monday(anyDay)
            val sunday = monday.plus(DatePeriod(days = 6))
            val open = snapshot.tasks.filter { it.status == TaskStatus.TODO && filter.matches(it) }
            val done = snapshot.tasks.filter { it.status == TaskStatus.DONE && filter.matches(it) }
            val templates = snapshot.timeBlocks.filter { it.recurrence != BlockRecurrence.NONE }
            // Fériés des réglages : un créneau « jours ouvrés » les saute (docs/spec/recurrence.md, issue #109).
            val holidays = Workdays.holidaysFor(monday, snapshot.settings.holidaysFr, snapshot.settings.extraHolidays)
            val occurrences = Recurrence.expandRecurringBlocks(templates, monday, sunday, holidays, snapshot.blockExceptions)
            val oneOff = snapshot.timeBlocks.filter { it.recurrence == BlockRecurrence.NONE }
            val days = (0..6).map { i ->
                val day = monday.plus(DatePeriod(days = i))
                WeekDay(
                    date = day,
                    // Programmée un autre jour : elle n'est qu'à ce jour-là, pas à son échéance (docs/spec/vue-semaine.md).
                    tasks = open.filter { it.deadline == day && (it.scheduledDate == null || it.scheduledDate == day) }
                        .sortedWith(compareBy<Task>({ it.priority == null }, { it.priority ?: 999 })),
                    done = done.filter { it.updatedAt.toLocalDateTime(timeZone).date == day }.sortedBy { it.title },
                    blocks = (oneOff + occurrences).filter { it.start.date == day }.sortedBy { it.start },
                    scheduled = open.filter { it.scheduledDate == day && it.deadline != day }
                        .sortedWith(compareBy<Task>({ it.priority == null }, { it.priority ?: 999 }, { it.title })),
                    holiday = day in holidays,
                    dayOff = Schedule.windowsFor(day, snapshot.settings).isEmpty(),
                    marks = snapshot.dayMarks.filter { it.covers(day) }.sortedBy { it.title },
                )
            }
            return WeekView(monday, days)
        }
    }
}
