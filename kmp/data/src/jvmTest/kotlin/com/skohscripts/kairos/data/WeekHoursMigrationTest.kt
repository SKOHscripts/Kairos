package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.data.db.KairosDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Instant

/** Semaine type d'un membre (issue #104) : colonne `team_member.week_hours`, migration 9 -> 10 (`9.sqm`), dépôt et export. */
class WeekHoursMigrationTest {
    private val now = Instant.parse("2026-10-05T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val team = Settings(team = TeamSettings(enabled = true, identity = "id-1"))

    private suspend fun open(path: String): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver("jdbc:sqlite:$path")), { KairosSnapshot(settings = team) }, clock, timeZone = TimeZone.UTC)

    private fun sql(path: String, vararg statements: String) =
        java.sql.DriverManager.getConnection("jdbc:sqlite:$path").use { c -> c.createStatement().use { st -> statements.forEach { st.execute(it) } } }

    private fun userVersion(path: String) = java.sql.DriverManager.getConnection("jdbc:sqlite:$path").use { c ->
        c.createStatement().executeQuery("PRAGMA user_version").use { it.getInt(1) }
    }

    @Test
    fun aSchemaNineDatabaseGainsTheColumnAndKeepsItsMembers() = runTest {
        val file = Files.createTempFile("kairos-v9", ".db").toFile().also { it.delete() }
        val first = open(file.path)
        first.createMember("Léa", "", 80, 7.0, false)
        val before = first.snapshot.value.members
        // Base de la 3.8.0 : pas de colonne, étiquetée en schéma 9.
        sql(file.path, "DROP TABLE migration_mark", "DROP TABLE day_mark", "DROP TABLE calendar_source", "DROP INDEX time_block_source", "ALTER TABLE time_block DROP COLUMN source_uid", "ALTER TABLE time_block DROP COLUMN source_id", "ALTER TABLE team_member DROP COLUMN week_hours", "ALTER TABLE task DROP COLUMN note_out", "ALTER TABLE task DROP COLUMN note_in", "ALTER TABLE task DROP COLUMN proposed_to", "PRAGMA user_version = 9")
        val repo = open(file.path)
        assertEquals(KairosDatabase.Schema.version.toInt(), userVersion(file.path))
        assertEquals(before, repo.snapshot.value.members)
        assertNull(repo.snapshot.value.members.single().weekHours)
        file.delete()
    }

    @Test
    fun theWeekIsSavedExportedAndReadBack() = runTest {
        val repo = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot(settings = team) }, clock)
        val id = repo.createMember("Léa", "", 100, 7.0, false, listOf(8.0, 8.0, 0.0, 8.0, 4.5))!!
        assertEquals(listOf(8.0, 8.0, 0.0, 8.0, 4.5), repo.snapshot.value.members.single().weekHours)
        // Une semaine mal formée est écartée ; la retirer rend les heures par jour.
        assertTrue(repo.updateMember(id, "Léa", "", 100, 7.0, false, listOf(0.0, 0.0, 0.0, 0.0, 0.0)))
        assertNull(repo.snapshot.value.members.single().weekHours)
        repo.updateMember(id, "Léa", "", 100, 7.0, false, listOf(6.0, 6.0, 6.0, 6.0, 0.0))
        val text = ExportCodec.encode(repo.snapshot.value, "3.9.0", now)
        assertTrue("\"weekHours\"" in text)
        assertEquals(listOf(6.0, 6.0, 6.0, 6.0, 0.0), ExportCodec.decode(text).members.single().weekHours)
        // Sans semaine type, rien n'est écrit : un export d'avant ne change pas.
        repo.createMember("Tom", "", 100, 7.0, false)
        val tom = ExportCodec.encode(repo.snapshot.value.copy(members = repo.snapshot.value.members.filter { it.name == "Tom" }), "3.9.0", now)
        assertFalse("weekHours" in tom)
        // Une semaine mal formée dans un fichier est refusée.
        val sixDays = text.replace("\"weekHours\": [", "\"weekHours\": [ 9,")
        assertTrue(sixDays != text)
        assertFailsWith<ImportException> { ExportCodec.decode(sixDays) }
    }
}
