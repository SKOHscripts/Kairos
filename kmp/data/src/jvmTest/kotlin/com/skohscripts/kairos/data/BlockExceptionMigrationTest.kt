package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.db.KairosDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Une seule occurrence d'un créneau (issue #110) : table `time_block_exception`, migration 8 -> 9 (`8.sqm`). */
class BlockExceptionMigrationTest {
    private val now = Instant.parse("2026-09-28T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val examples = { ExampleData.snapshot(LocalDate(2026, 9, 28), now, "fr") }

    private suspend fun open(file: File, withExamples: Boolean = false): KairosRepository =
        KairosRepository.open(
            KairosStore.open(JdbcSqliteDriver("jdbc:sqlite:${file.path}")),
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
    fun aSchemaEightDatabaseGainsTheExceptionTableWithoutLoss() = runTest {
        val file = Files.createTempFile("kairos-v8", ".db").toFile().also { it.delete() }
        open(file, withExamples = true)
        val before = open(file).snapshot.value
        // Base de la 3.7.0 : pas de table d'exceptions, étiquetée en schéma 8.
        sql(file, "DROP TABLE migration_mark", "DROP TABLE day_mark", "DROP TABLE calendar_source", "DROP INDEX time_block_source", "ALTER TABLE time_block DROP COLUMN source_uid", "ALTER TABLE time_block DROP COLUMN source_id", "ALTER TABLE team_member DROP COLUMN week_hours", "ALTER TABLE task DROP COLUMN note_out", "ALTER TABLE task DROP COLUMN note_in", "ALTER TABLE task DROP COLUMN proposed_to", "DROP TABLE time_block_exception", "PRAGMA user_version = 8")
        val repo = open(file)
        assertEquals(KairosDatabase.Schema.version.toInt(), userVersion(file))
        assertEquals(before.tasks, repo.snapshot.value.tasks)
        assertEquals(before.timeBlocks, repo.snapshot.value.timeBlocks)
        assertTrue(repo.snapshot.value.blockExceptions.isEmpty())
        // La table est là : poser une exception fonctionne.
        val lunch = repo.snapshot.value.timeBlocks.first { it.recurrence.code == "daily" }
        assertTrue(repo.skipOccurrence(lunch.id, LocalDate(2026, 9, 29)))
        assertEquals(1, repo.snapshot.value.blockExceptions.size)
        file.delete()
    }
}
