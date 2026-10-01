package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.QualifiedField
import com.skohscripts.kairos.core.team.TeamEventKind
import com.skohscripts.kairos.core.team.TeamEvents
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/** `setTaskType` : changement de catégorie en lot du Backlog d'équipe (docs/spec/equipe-backlog-suivi.md). */
class TeamTaskTypeTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-09-28T07:00:00Z")
    }

    private suspend fun open(): KairosRepository {
        val repo = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock, TimeZone.UTC)
        repo.replaceAll(KairosSnapshot(settings = Settings(team = TeamSettings(enabled = true, identity = "id-1"))))
        return repo
    }

    @Test
    fun aTeamTaskChangesCategoryAndTheChangeIsJournaled() = runTest {
        val repo = open()
        val id = repo.createTeamTask("Ship")!!
        repo.setTaskType(id, "  Réunion ")
        repo.setTaskType(id, "Réunion")
        assertEquals("Réunion", repo.snapshot.value.tasks.single().taskType)
        val qualified = repo.snapshot.value.teamEvents.filter { it.kind == TeamEventKind.QUALIFIED }
        assertEquals(1, qualified.size)
        assertEquals(TeamEvents.qualifiedValue(QualifiedField.TYPE, "Réunion"), qualified.single().toValue)
        repo.setTaskType(id, "")
        assertEquals("", repo.snapshot.value.tasks.single().taskType)
    }

    @Test
    fun aPersonalTaskChangesCategoryWithoutAnyEvent() = runTest {
        val repo = open()
        val id = repo.createTask("Mine")!!
        repo.setTaskType(id, "Réunion")
        assertEquals("Réunion", repo.snapshot.value.tasks.single().taskType)
        assertEquals(emptyList(), repo.snapshot.value.teamEvents)
    }
}
