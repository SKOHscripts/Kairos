package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.calendar.CalendarImport
import com.skohscripts.kairos.core.calendar.ImportedCalendars
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockSource
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.db.KairosDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Import d'un calendrier `.ics` (issue #136, docs/spec/connexions-externes.md § Stockage) : dépôt, export, migration 10 -> 11. */
class CalendarImportRepositoryTest {
    private val now = Instant.parse("2026-10-19T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val today = LocalDate(2026, 10, 19)
    private val paris = TimeZone.of("Europe/Paris")

    private suspend fun memory(): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)

    private val ics = """
        BEGIN:VCALENDAR
        X-WR-CALNAME:Travail
        BEGIN:VEVENT
        UID:a
        SUMMARY:Point d'équipe
        DTSTART;TZID=Europe/Paris:20261020T093000
        DTEND;TZID=Europe/Paris:20261020T100000
        RRULE:FREQ=WEEKLY;COUNT=3
        END:VEVENT
        BEGIN:VEVENT
        UID:b
        SUMMARY:Congés
        DTSTART;VALUE=DATE:20261102
        DTEND;VALUE=DATE:20261104
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test
    fun importingReplacesOnlyThatCalendarFromTodayAndKeepsManualSlots() = runTest {
        val repo = memory()
        val manual = BlockEdit("Dentiste", today.atTime(17, 0), today.atTime(18, 0), BlockKind.BUSY)
        assertTrue(repo.createBlock(manual))
        val plan = CalendarImport.plan(ics, today, paris)
        val work = repo.importCalendar("Travail", hideTitles = false, plan = plan, today = today)!!
        val perso = repo.importCalendar("Perso", hideTitles = true, plan = plan, today = today)!!
        val s1 = repo.snapshot.value
        assertEquals(7, s1.timeBlocks.size)
        assertEquals(2, s1.dayMarks.size)
        assertTrue(s1.timeBlocks.filter { it.source == BlockSource.Imported(perso, "a") }.all { it.title.isEmpty() && it.kind == BlockKind.BUSY })
        assertEquals(listOf("Perso", "Travail"), ImportedCalendars.of(s1, today).map { it.source.name })
        assertTrue(ImportedCalendars.named(s1, "travail ")!!.let { !it.hideTitles && it.id == work })
        assertEquals(3, ImportedCalendars.replaced(s1, "Travail", today))

        // Réimporter le même fichier le même jour : la même base, aux identifiants près ; le nom retrouve le calendrier.
        assertEquals(work, repo.importCalendar("Travail", hideTitles = false, plan = plan, today = today))
        val s2 = repo.snapshot.value
        fun content(s: KairosSnapshot) = s.timeBlocks.map { Triple(it.title, it.start, it.source) }.sortedBy { it.toString() } to
            s.dayMarks.map { Triple(it.title, it.start, it.source) }.sortedBy { it.toString() }
        assertEquals(content(s1), content(s2))
        assertEquals(2, s2.calendarSources.size)

        // Dix jours plus tard : le passé du calendrier reste, seul ce qui commence à partir de ce jour est remplacé.
        val later = LocalDate(2026, 10, 29)
        repo.importCalendar("Travail", hideTitles = true, plan = CalendarImport.plan(ics, later, paris), today = later)
        val s3 = repo.snapshot.value
        val workBlocks = s3.timeBlocks.filter { (it.source as? BlockSource.Imported)?.calendarId == work }
        assertEquals(listOf("Point d'équipe", "Point d'équipe", ""), workBlocks.sortedBy { it.start }.map { it.title })
        assertTrue(ImportedCalendars.named(s3, "Travail")!!.hideTitles)

        // « Retirer » : tout le calendrier, passé compris, rien d'autre.
        repo.removeCalendar(work)
        val s4 = repo.snapshot.value
        assertTrue(s4.timeBlocks.none { (it.source as? BlockSource.Imported)?.calendarId == work } && s4.dayMarks.none { (it.source as? BlockSource.Imported)?.calendarId == work })
        assertEquals(listOf("Perso"), s4.calendarSources.map { it.name })
        assertEquals(1, s4.timeBlocks.count { it.source == null })
        assertEquals(3, s4.timeBlocks.count { (it.source as? BlockSource.Imported)?.calendarId == perso })
    }

    @Test
    fun anImportedSlotCannotBeEditedButCanBeDeleted() = runTest {
        val repo = memory()
        repo.importCalendar("Travail", false, CalendarImport.plan(ics, today, paris), today)
        val block = repo.snapshot.value.timeBlocks.first()
        assertFalse(repo.updateBlock(block.id, BlockEdit("Autre", block.start, block.end, BlockKind.BUSY)))
        assertFalse(repo.skipOccurrence(block.id, block.start.date))
        repo.deleteBlock(block.id)
        assertEquals(2, repo.snapshot.value.timeBlocks.size)
        assertNull(repo.importCalendar("  ", false, CalendarImport.plan(ics, today, paris), today))
    }

    @Test
    fun exportIsUnchangedWithoutCalendarsAndRoundTripsWithThem() = runTest {
        val repo = memory()
        repo.createBlock(BlockEdit("Dentiste", today.atTime(17, 0), today.atTime(18, 0), BlockKind.BUSY))
        val plain = ExportCodec.encode(repo.snapshot.value, "3.10.0", now)
        assertFalse("source" in plain || "calendarSources" in plain || "dayMarks" in plain)
        repo.importCalendar("Travail", true, CalendarImport.plan(ics, today, paris), today)
        val text = ExportCodec.encode(repo.snapshot.value, "3.10.0", now)
        assertTrue("\"calendarSources\"" in text && "\"sourceUid\": \"a\"" in text && "\"hideTitles\": true" in text)
        val back = ExportCodec.decode(text)
        assertEquals(repo.snapshot.value.timeBlocks, back.timeBlocks)
        assertEquals(repo.snapshot.value.dayMarks, back.dayMarks)
        assertEquals(repo.snapshot.value.calendarSources, back.calendarSources)
        // Le contenu relu se réécrit à l'identique dans une autre base.
        val other = memory()
        other.replaceAll(back)
        assertEquals(text, ExportCodec.encode(other.snapshot.value, "3.10.0", now))
        // Une journée à l'envers, ou un créneau qui désigne un calendrier absent, sont refusés.
        val reversed = text.replace("\"start\": \"2026-11-02\"", "\"start\": \"2026-11-09\"")
        assertTrue(reversed != text)
        kotlin.test.assertFailsWith<ImportException> { ExportCodec.decode(reversed) }
        val orphan = text.replace("\"sourceId\": 1,", "\"sourceId\": 7,")
        assertTrue(orphan != text)
        kotlin.test.assertFailsWith<ImportException> { ExportCodec.decode(orphan) }
    }

    @Test
    fun aSchemaTenDatabaseGainsTheColumnAndTheTable() = runTest {
        val file = Files.createTempFile("kairos-v10", ".db").toFile().also { it.delete() }
        val first = KairosRepository.open(KairosStore.open(JdbcSqliteDriver("jdbc:sqlite:${file.path}")), { KairosSnapshot() }, clock)
        first.createBlock(BlockEdit("Dentiste", today.atTime(17, 0), today.atTime(18, 0), BlockKind.BUSY))
        val before = first.snapshot.value.timeBlocks
        // Base de la 3.9.0 : ni colonne `source`, ni table `day_mark`, étiquetée en schéma 10.
        java.sql.DriverManager.getConnection("jdbc:sqlite:${file.path}").use { c ->
            c.createStatement().use { st ->
                listOf(
                    "DROP TABLE migration_mark", "DROP TABLE day_mark", "DROP TABLE calendar_source", "DROP INDEX time_block_source",
                    "ALTER TABLE time_block DROP COLUMN source_uid", "ALTER TABLE time_block DROP COLUMN source_id", "PRAGMA user_version = 10",
                ).forEach(st::execute)
            }
        }
        val repo = KairosRepository.open(KairosStore.open(JdbcSqliteDriver("jdbc:sqlite:${file.path}")), { KairosSnapshot() }, clock)
        assertEquals(before, repo.snapshot.value.timeBlocks)
        assertNull(repo.snapshot.value.timeBlocks.single().source)
        val version = java.sql.DriverManager.getConnection("jdbc:sqlite:${file.path}").use { c ->
            c.createStatement().executeQuery("PRAGMA user_version").use { it.getInt(1) }
        }
        assertEquals(KairosDatabase.Schema.version.toInt(), version)
        repo.importCalendar("Travail", false, CalendarImport.plan(ics, today, paris), today)
        assertEquals(1, repo.snapshot.value.dayMarks.size)
        file.delete()
    }
}
