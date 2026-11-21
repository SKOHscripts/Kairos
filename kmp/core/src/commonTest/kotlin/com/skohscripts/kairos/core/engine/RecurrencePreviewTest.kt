package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.TaskRecurrence
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Aperçu des règles de l'accompagnement de la première tâche récurrente (docs/spec/premiere-tache.md, issue #98). */
class RecurrencePreviewTest {
    @Test
    fun eachRuleGivesItsNextDeadline() {
        // Vendredi 30 octobre 2026 ; le lundi 2 novembre n'est pas férié, le dimanche 1er l'est.
        val holidays = Workdays.holidaysFor(LocalDate(2026, 10, 30), country = "FR", extra = "")
        val preview = Recurrence.preview(LocalDate(2026, 10, 30), dayOfMonth = null, holidays = holidays)
        assertEquals(
            listOf(TaskRecurrence.DAILY, TaskRecurrence.WEEKDAYS, TaskRecurrence.WEEKLY, TaskRecurrence.MONTHLY, TaskRecurrence.MONTHLY_ON_DAY),
            preview.keys.toList(),
        )
        assertEquals(LocalDate(2026, 10, 31), preview[TaskRecurrence.DAILY])
        assertEquals(LocalDate(2026, 11, 2), preview[TaskRecurrence.WEEKDAYS])
        assertEquals(LocalDate(2026, 11, 6), preview[TaskRecurrence.WEEKLY])
        assertEquals(LocalDate(2026, 11, 30), preview[TaskRecurrence.MONTHLY])
        // Le 30 novembre est un lundi ouvré.
        assertEquals(LocalDate(2026, 11, 30), preview[TaskRecurrence.MONTHLY_ON_DAY])
    }

    @Test
    fun workingDaysSkipAHolidayAndTheDayOfMonthMovesBack() {
        // Mardi 10 novembre 2026 : le 11 est férié, « jours ouvrés » passe au 12.
        val holidays = Workdays.holidaysFor(LocalDate(2026, 11, 10), country = "FR", extra = "")
        val preview = Recurrence.preview(LocalDate(2026, 11, 10), dayOfMonth = 25, holidays = holidays)
        assertEquals(LocalDate(2026, 11, 12), preview[TaskRecurrence.WEEKDAYS])
        // Le 25 décembre est férié : l'échéance recule au jeudi 24.
        assertEquals(LocalDate(2026, 12, 24), preview[TaskRecurrence.MONTHLY_ON_DAY])
        // En décembre, le mois suivant est janvier de l'année d'après.
        assertEquals(LocalDate(2027, 1, 15), Recurrence.preview(LocalDate(2026, 12, 15), null, emptySet())[TaskRecurrence.MONTHLY_ON_DAY])
    }
}
