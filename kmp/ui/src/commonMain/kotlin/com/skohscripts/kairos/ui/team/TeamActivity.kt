package com.skohscripts.kairos.ui.team

import com.skohscripts.kairos.core.team.TeamEvent
import com.skohscripts.kairos.core.team.TeamEventKind
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** Fenêtre de la section « Activité » de la fiche d'un membre (aujourd'hui compris). */
internal const val ACTIVITY_DAYS = 30

/**
 * Événements des tâches d'un membre sur les [ACTIVITY_DAYS] derniers jours
 * (docs/spec/equipe-backlog-suivi.md § Journal), du plus récent au plus ancien :
 * ceux dont il est le titulaire au moment de l'événement (`memberId`, soit le
 * **nouveau** titulaire pour une assignation) et les réaffectations qui lui ont
 * retiré une tâche (`fromValue`). Une tâche supprimée garde ses événements.
 */
internal fun memberActivity(events: List<TeamEvent>, memberId: Long, today: LocalDate, timeZone: TimeZone): List<TeamEvent> {
    val since = today.minus(DatePeriod(days = ACTIVITY_DAYS - 1))
    val id = memberId.toString()
    return events
        .filter { event ->
            val day = event.at.toLocalDateTime(timeZone).date
            day >= since && day <= today && (event.memberId == memberId || (event.kind == TeamEventKind.ASSIGNED && event.fromValue == id))
        }
        .sortedWith(compareByDescending<TeamEvent> { it.at }.thenByDescending { it.id })
}
