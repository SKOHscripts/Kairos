package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.Task
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/**
 * Tâches qui traînent (`app/tasks_staleness.py`, Kairos 2) : signal d'affichage
 * seulement, jamais une clé de tri.
 */
object Staleness {
    /**
     * Jours « de trop » si la tâche traîne, sinon `null` : échéance ou date
     * programmée dépassée depuis plus de [overdueDays] (la plus ancienne des deux
     * fait foi), ou, sans aucune des deux, pas modifiée depuis plus de
     * [untouchedDays]. La date de `updatedAt` est prise **en UTC**, comme Kairos 2
     * (`updated_at.date()` sur un horodatage UTC naïf).
     */
    fun daysStale(task: Task, today: LocalDate, overdueDays: Int, untouchedDays: Int): Int? {
        val overdue = listOfNotNull(task.deadline, task.scheduledDate).filter { it <= today }
        if (overdue.isNotEmpty()) {
            val days = overdue.min().daysUntil(today)
            return days.takeIf { it > overdueDays }
        }
        if (task.deadline == null && task.scheduledDate == null) {
            val days = task.updatedAt.toLocalDateTime(TimeZone.UTC).date.daysUntil(today)
            return days.takeIf { it > untouchedDays }
        }
        return null
    }
}
