package com.skohscripts.kairos.core.guide

import com.skohscripts.kairos.core.model.GuideSettings
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.team.TeamEvent
import com.skohscripts.kairos.core.team.TeamEventKind
import com.skohscripts.kairos.core.team.TeamEventSource
import com.skohscripts.kairos.core.team.TeamMember
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * Isolation du mode solo (docs/spec/conseils.md § Calcul pur, docs/spec/equipe.md) : les conseils Perso ne lisent jamais une
 * donnée d'équipe, et sans espace Équipe activé aucun conseil d'équipe n'est proposé.
 */
class TipsIsolationTest {
    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private val tz = TimeZone.UTC
    private val facts = TipFacts(TipPlatform.DESKTOP, darkTheme = false, linkedFile = false, calendarImport = true)

    private fun task(id: Long, daysAgo: Int, space: TaskSpace = TaskSpace.PERSONAL, status: TaskStatus = TaskStatus.DONE, origin: String? = null) =
        Task(id, "T$id", status = status, space = space, origin = origin, estimatedMinutes = 30, taskType = "Dev", createdAt = now - daysAgo.days, updatedAt = now - daysAgo.days)

    /** Une base solo avec un peu de tout : âge, tâches faites, sessions. */
    private val solo = KairosSnapshot(
        tasks = (1L..6L).map { task(it, it.toInt()) },
        workSessions = (1L..6L).map { WorkSession(100 + it, it, now - it.toInt().days, now - it.toInt().days + 45.minutes, createdAt = now) },
        notes = listOf(Note(1, "Note", createdAt = now - 40.days, updatedAt = now - 40.days)),
    )

    /** La même base, plus des données d'équipe : tâches d'équipe et reçues, leurs sessions, membres, journal. */
    private fun withTeamData(settings: Settings) = solo.copy(
        settings = settings,
        tasks = solo.tasks + (200L..260L).map { task(it, 50, space = TaskSpace.TEAM) } + (300L..310L).map { task(it, 60, origin = "pack", status = TaskStatus.TODO) },
        workSessions = solo.workSessions + (200L..260L).map { WorkSession(1000 + it, it, now - 2.days, now - 2.days + 200.minutes, createdAt = now) },
        members = listOf(TeamMember(1, "u1", "Moi", hoursPerDay = 8.0, isSelf = true, createdAt = now, updatedAt = now)),
        teamEvents = listOf(TeamEvent(1, 200, "T", 1, TeamEventKind.SENT, source = TeamEventSource.MANUAL, at = now)),
    )

    private fun allPersonalTips(snapshot: KairosSnapshot): List<TipId?> =
        TipId.entries.filter { it.space == TipSpace.PERSONAL }.map { id ->
            val only = GuideSettings(tipsDismissed = TipId.entries.filter { it != id }.map { it.key })
            Tips.due(snapshot, only, facts, TipSpace.PERSONAL, now, tz)?.id
        }

    @Test
    fun soloNeverReadsTeamData() {
        val expected = allPersonalTips(solo)
        assertEquals(expected, allPersonalTips(withTeamData(Settings())))
        assertEquals(expected, allPersonalTips(withTeamData(Settings(team = TeamSettings(enabled = true)))))
        assertEquals(Tips.due(solo, null, facts, TipSpace.PERSONAL, now, tz), Tips.due(withTeamData(Settings()), null, facts, TipSpace.PERSONAL, now, tz))
    }

    @Test
    fun noTeamTipWithoutTheTeamSpace() {
        val disabled = withTeamData(Settings(team = TeamSettings(enabled = false)))
        assertNull(Tips.due(disabled, null, facts, TipSpace.TEAM, now, tz))
        assertNull(Tips.due(withTeamData(Settings()), null, facts, TipSpace.TEAM, now, tz))
    }
}
