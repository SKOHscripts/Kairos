package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.TeamMember
import com.skohscripts.kairos.core.team.Workspaces
import com.skohscripts.kairos.data.db.KairosDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Travail perso de « moi » (docs/spec/equipe-moi.md) : colonne `team_excluded`, migration 6 -> 7, édition, export, nettoyage. */
class MyWorkRepositoryTest {
    private val now = Instant.parse("2026-10-10T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val day = LocalDate(2026, 10, 10)
    private val examples = { ExampleData.snapshot(day, now, "fr") }

    private fun driver(file: File? = null) =
        JdbcSqliteDriver(if (file == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:${file.path}")

    private suspend fun open(file: File? = null): KairosRepository =
        KairosRepository.open(KairosStore.open(driver(file)), examples, clock, timeZone = TimeZone.UTC)

    private fun sql(file: File, vararg statements: String) =
        java.sql.DriverManager.getConnection("jdbc:sqlite:${file.path}").use { c -> c.createStatement().use { st -> statements.forEach { st.execute(it) } } }

    private fun userVersion(f: File) = java.sql.DriverManager.getConnection("jdbc:sqlite:${f.path}").use { c ->
        c.createStatement().executeQuery("PRAGMA user_version").use { it.getInt(1) }
    }

    private fun me() = TeamMember(1, "uid-1", "Moi", availabilityPercent = 100, hoursPerDay = 8.0, isSelf = true, createdAt = now, updatedAt = now)

    @Test
    fun aSchemaSixDatabaseGainsTheColumnAndEveryTaskStillCounts() = runTest {
        // Base neuve ramenée au schéma 6 : la colonne retirée, la version reculée.
        val file = Files.createTempFile("kairos-v6", ".db").toFile().also { it.delete() }
        open(file)
        val before = open(file).snapshot.value.tasks.map { it.id }
        sql(file, "DROP TABLE migration_mark", "DROP TABLE day_mark", "DROP TABLE calendar_source", "DROP INDEX time_block_source", "ALTER TABLE time_block DROP COLUMN source_uid", "ALTER TABLE time_block DROP COLUMN source_id", "ALTER TABLE team_member DROP COLUMN week_hours", "ALTER TABLE task DROP COLUMN note_out", "ALTER TABLE task DROP COLUMN note_in", "ALTER TABLE task DROP COLUMN proposed_to", "DROP TABLE time_block_exception", "ALTER TABLE task DROP COLUMN series_id", "ALTER TABLE task DROP COLUMN team_excluded", "PRAGMA user_version = 6")

        val repo = open(file)
        assertEquals(KairosDatabase.Schema.version.toInt(), userVersion(file))
        assertEquals(before, repo.snapshot.value.tasks.map { it.id })
        assertTrue(repo.snapshot.value.tasks.none { it.teamExcluded })
        file.delete()
    }

    @Test
    fun excludingAPersonalTaskIsSavedAndOnlyForPersonalTasks() = runTest {
        val repo = open()
        val id = repo.createTask("Rendez-vous perso")!!
        repo.updateTask(id, TaskEdit(title = "Rendez-vous perso", pinDay = day, teamExcluded = true))
        assertTrue(repo.snapshot.value.tasks.single { it.id == id }.teamExcluded)
        // Une édition qui ne touche pas l'interrupteur le laisse en place.
        repo.updateTask(id, TaskEdit(title = "Rendez-vous", pinDay = day))
        assertTrue(repo.snapshot.value.tasks.single { it.id == id }.teamExcluded)
        repo.updateTask(id, TaskEdit(title = "Rendez-vous", pinDay = day, teamExcluded = false))
        assertFalse(repo.snapshot.value.tasks.single { it.id == id }.teamExcluded)

        // Sans effet sur une tâche d'équipe.
        val teamId = repo.createTeamTask("Migration")!!
        repo.updateTask(teamId, TaskEdit(title = "Migration", pinDay = day, teamExcluded = true))
        assertFalse(repo.snapshot.value.tasks.single { it.id == teamId }.teamExcluded)
    }

    @Test
    fun theExclusionTravelsInATeamExportOnlyAndMakesItOne() {
        val excluded = Task(1, "Privé", createdAt = now, updatedAt = now, teamExcluded = true)
        val snapshot = KairosSnapshot(tasks = listOf(excluded, Task(2, "Commun", createdAt = now, updatedAt = now)))
        assertTrue(Workspaces.hasTeamData(snapshot))
        val text = ExportCodec.encode(snapshot, "3.3.0", now)
        assertTrue("\"formatVersion\": 2" in text)
        assertEquals(1, Regex("\"teamExcluded\"").findAll(text).count())
        assertEquals(snapshot, ExportCodec.decode(text))
        // Sans exclusion, pas un mot d'équipe : l'export solo reste celui d'avant.
        val solo = ExportCodec.encode(snapshot.copy(tasks = listOf(excluded.copy(teamExcluded = false))), "3.3.0", now)
        assertFalse("teamExcluded" in solo)
        assertTrue("\"formatVersion\": 1" in solo)
    }

    @Test
    fun clearingTheTeamDataClearsTheExclusionsToo() = runTest {
        val repo = open()
        repo.updateSettings(Settings(team = TeamSettings(enabled = true, identity = "id-1")))
        repo.replaceAll(repo.snapshot.value.copy(members = listOf(me())))
        val id = repo.createTask("Privé")!!
        repo.updateTask(id, TaskEdit(title = "Privé", pinDay = day, teamExcluded = true))
        repo.clearTeamData()
        assertTrue(repo.snapshot.value.tasks.none { it.teamExcluded })
        assertEquals(TaskSpace.PERSONAL, repo.snapshot.value.tasks.single { it.id == id }.space)
    }
}
