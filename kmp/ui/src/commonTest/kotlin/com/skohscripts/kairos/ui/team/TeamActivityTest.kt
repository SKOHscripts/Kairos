package com.skohscripts.kairos.ui.team

import com.skohscripts.kairos.core.team.TeamEvent
import com.skohscripts.kairos.core.team.TeamEventKind
import com.skohscripts.kairos.core.team.TeamEventSource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Section « Activité » de la fiche membre (docs/spec/equipe-backlog-suivi.md § Journal). */
class TeamActivityTest {
    private val today = LocalDate(2026, 9, 30)

    private fun event(id: Long, at: String, kind: TeamEventKind, member: Long?, from: String? = null, to: String? = null) =
        TeamEvent(id, 1, "Tâche", member, kind, from, to, TeamEventSource.MANUAL, Instant.parse(at))

    @Test
    fun keepsTheEventsOfTheMemberOverThirtyDaysMostRecentFirst() {
        val events = listOf(
            event(1, "2026-09-01T10:00:00Z", TeamEventKind.ASSIGNED, 7, null, "7"),
            event(2, "2026-08-31T10:00:00Z", TeamEventKind.ASSIGNED, 7, null, "7"),
            event(3, "2026-09-20T10:00:00Z", TeamEventKind.PROGRESS, 7, "0", "30"),
            event(4, "2026-09-25T10:00:00Z", TeamEventKind.PROGRESS, 8, "0", "30"),
            event(5, "2026-09-30T08:00:00Z", TeamEventKind.DONE, 7, "30", "100"),
        )
        assertEquals(listOf(5L, 3L, 1L), memberActivity(events, 7, today, TimeZone.UTC).map { it.id })
    }

    @Test
    fun aReassignmentAwayFromTheMemberStaysInTheirActivity() {
        val away = event(1, "2026-09-25T10:00:00Z", TeamEventKind.ASSIGNED, 8, "7", "8")
        val toBacklog = event(2, "2026-09-26T10:00:00Z", TeamEventKind.ASSIGNED, null, "7", null)
        val other = event(3, "2026-09-27T10:00:00Z", TeamEventKind.ASSIGNED, 9, "8", "9")
        assertEquals(listOf(2L, 1L), memberActivity(listOf(away, toBacklog, other), 7, today, TimeZone.UTC).map { it.id })
    }
}
