package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.data.db.KairosDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Série d'une tâche récurrente (docs/spec/durees-apprises.md) : colonne `series_id`, migration 7 -> 8, export. */
class LearnedDurationsRepositoryTest {
    private val now = Instant.parse("2026-09-28T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val day = LocalDate(2026, 9, 28)
    private val examples = { ExampleData.snapshot(day, now, "fr") }

    private suspend fun open(file: File? = null, withExamples: Boolean = false): KairosRepository =
        KairosRepository.open(
            KairosStore.open(JdbcSqliteDriver(if (file == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:${file.path}")),
            if (withExamples) examples else ({ KairosSnapshot() }),
            clock,
            timeZone = TimeZone.UTC,
        )

    private fun sql(file: File, vararg statements: String) =
        java.sql.DriverManager.getConnection("jdbc:sqlite:${file.path}").use { c -> c.createStatement().use { st -> statements.forEach { st.execute(it) } } }

    private fun userVersion(f: File) = java.sql.DriverManager.getConnection("jdbc:sqlite:${f.path}").use { c ->
        c.createStatement().executeQuery("PRAGMA user_version").use { it.getInt(1) }
    }

    @Test
    fun everyOccurrenceKeepsTheIdOfTheFirstTaskOfItsSeries() = runTest {
        val repo = open()
        val first = repo.createTask("Relever le courrier")!!
        repo.updateTask(first, TaskEdit("Relever le courrier", priority = 2, points = 2, deadline = day, recurrence = TaskRecurrence.DAILY, pinDay = day))
        assertNull(repo.snapshot.value.tasks.single().seriesId)
        repo.toggleDone(first)
        val second = repo.snapshot.value.tasks.single { it.status == TaskStatus.TODO }
        assertEquals(first, second.seriesId)
        repo.toggleDone(second.id)
        val third = repo.snapshot.value.tasks.single { it.status == TaskStatus.TODO }
        assertEquals(first, third.seriesId, "la série garde l'identifiant de la première tâche, pas celui de la précédente")
    }

    @Test
    fun aSchemaSevenDatabaseGainsTheSeriesColumn() = runTest {
        val file = Files.createTempFile("kairos-v7", ".db").toFile().also { it.delete() }
        open(file, withExamples = true)
        val before = open(file).snapshot.value.tasks.map { it.id }
        sql(file, "DROP TABLE migration_mark", "DROP TABLE day_mark", "DROP TABLE calendar_source", "DROP INDEX time_block_source", "ALTER TABLE time_block DROP COLUMN source_uid", "ALTER TABLE time_block DROP COLUMN source_id", "ALTER TABLE team_member DROP COLUMN week_hours", "ALTER TABLE task DROP COLUMN note_out", "ALTER TABLE task DROP COLUMN note_in", "ALTER TABLE task DROP COLUMN proposed_to", "DROP TABLE time_block_exception", "ALTER TABLE task DROP COLUMN series_id", "PRAGMA user_version = 7")
        val repo = open(file)
        assertEquals(KairosDatabase.Schema.version.toInt(), userVersion(file))
        assertEquals(before, repo.snapshot.value.tasks.map { it.id })
        assertTrue(repo.snapshot.value.tasks.none { it.seriesId != null }, "pas de rétro-remplissage")
        file.delete()
    }

    @Test
    fun theSeriesSurvivesAnExportAndIsAbsentWhenThereIsNone() {
        val task = Task(1, "A", createdAt = now, updatedAt = now)
        val plain = ExportCodec.encode(KairosSnapshot(tasks = listOf(task)), "3.4.0", now)
        assertTrue("seriesId" !in plain, "une tâche sans série n'écrit rien : l'export d'avant reste identique")
        val with = ExportCodec.encode(KairosSnapshot(tasks = listOf(task.copy(seriesId = 7))), "3.4.0", now)
        assertEquals(7L, ExportCodec.decode(with).tasks.single().seriesId)
    }
}
