package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.db.KairosDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/** Tables de la synchronisation, migration 12 -> 13 (`12.sqm`, docs/spec/synchronisation.md § Stockage) : additive, vide. */
class SyncMigrationTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-10-27T07:00:00Z")
    }

    private suspend fun open(file: File): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver("jdbc:sqlite:${file.path}")), { KairosSnapshot() }, clock, timeZone = TimeZone.UTC)

    private fun sql(file: File, vararg statements: String) =
        java.sql.DriverManager.getConnection("jdbc:sqlite:${file.path}").use { c -> c.createStatement().use { st -> statements.forEach { st.execute(it) } } }

    private fun count(file: File, query: String) = java.sql.DriverManager.getConnection("jdbc:sqlite:${file.path}").use { c ->
        c.createStatement().executeQuery(query).use { it.getInt(1) }
    }

    @Test
    fun aSchemaTwelveDatabaseGainsEmptySyncTablesAndKeepsItsData() = runTest {
        val file = Files.createTempFile("kairos-sync-migration", ".db").toFile().also { it.delete() }
        val repo = open(file)
        repo.createTask("Rapport")
        val before = repo.snapshot.value
        sql(file, "DROP TABLE calendar_connection")
        sql(file, "DROP TABLE sync_state", "DROP TABLE sync_identity", "DROP TABLE sync_mark", "DROP TABLE sync_review", "PRAGMA user_version = 12")
        val reopened = open(file)
        assertEquals(KairosDatabase.Schema.version.toInt(), count(file, "PRAGMA user_version"))
        assertEquals(before, reopened.snapshot.value)
        for (table in listOf("sync_state", "sync_identity", "sync_mark", "sync_review")) assertEquals(0, count(file, "SELECT count(*) FROM $table"), table)
        assertNull(reopened.sync.value, "la migration n'active pas la synchronisation")
        file.delete()
    }
}
