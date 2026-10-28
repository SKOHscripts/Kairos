package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.data.db.KairosDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/** Couleur par défaut du thème : l'océan remplace le miel, migration 11 -> 12 (`11.sqm`, docs/spec/apparence.md). */
class ThemeColorMigrationTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-10-27T07:00:00Z")
    }

    private suspend fun open(file: File): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver("jdbc:sqlite:${file.path}")), { KairosSnapshot() }, clock, timeZone = TimeZone.UTC)

    private fun sql(file: File, vararg statements: String) =
        java.sql.DriverManager.getConnection("jdbc:sqlite:${file.path}").use { c -> c.createStatement().use { st -> statements.forEach { st.execute(it) } } }

    private fun userVersion(f: File) = java.sql.DriverManager.getConnection("jdbc:sqlite:${f.path}").use { c ->
        c.createStatement().executeQuery("PRAGMA user_version").use { it.getInt(1) }
    }

    /** Une base de la version précédente (schéma 11) dont le réglage stocké porte [json]. */
    private suspend fun databaseWith(json: String): File {
        val file = Files.createTempFile("kairos-theme", ".db").toFile().also { it.delete() }
        open(file)
        sql(file, "DROP TABLE migration_mark", "INSERT OR REPLACE INTO settings (id, json) VALUES (1, '$json')", "PRAGMA user_version = 11")
        return file
    }

    @Test
    fun aThemeLeftOnTheOldDefaultMovesToOcean() = runTest {
        val file = databaseWith("""{"themeColor":"#C28417","workdayStartHour":8}""")
        val settings = open(file).snapshot.value.settings
        assertEquals(KairosDatabase.Schema.version.toInt(), userVersion(file))
        assertEquals("#2F6FED", settings.themeColor)
        // Le reste du réglage est intact.
        assertEquals(8, settings.workdayStartHour)
        file.delete()
    }

    @Test
    fun aChosenColorOrTheSystemThemeIsKept() = runTest {
        for (kept in listOf("#1B8A8A", Settings.SYSTEM_THEME, "#2F6FED")) {
            val file = databaseWith("""{"themeColor":"$kept"}""")
            assertEquals(kept, open(file).snapshot.value.settings.themeColor)
            file.delete()
        }
    }

    @Test
    fun theMigrationRunsOnceAndNeverUndoesALaterHoneyChoice() = runTest {
        val file = databaseWith("""{"themeColor":"#C28417"}""")
        val repo = open(file)
        assertEquals("#2F6FED", repo.snapshot.value.settings.themeColor)
        // L'utilisateur choisit le miel après la mise à jour : la réouverture ne le lui reprend pas.
        repo.updateSettings(repo.snapshot.value.settings.copy(themeColor = Settings.HONEY_THEME_COLOR))
        assertEquals("#C28417", open(file).snapshot.value.settings.themeColor)
        file.delete()
    }

    @Test
    fun aDatabaseWithoutStoredSettingsGetsTheNewDefault() = runTest {
        val file = Files.createTempFile("kairos-theme-fresh", ".db").toFile().also { it.delete() }
        assertEquals(Settings.DEFAULT_THEME_COLOR, open(file).snapshot.value.settings.themeColor)
        assertEquals("#2F6FED", Settings.DEFAULT_THEME_COLOR)
        file.delete()
    }
}
