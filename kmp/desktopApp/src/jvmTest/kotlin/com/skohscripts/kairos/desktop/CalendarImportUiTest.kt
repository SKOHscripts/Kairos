package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.calendar.CalendarImport
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.BackupStore
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.settings.SettingsCategory
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Import d'un calendrier `.ics` (issue #136, docs/spec/connexions-externes.md § Interface) : aperçu avant écriture,
 * sauvegarde puis import, carte « Calendriers » (« Réimporter », « Retirer »), rien sans sauvegarde ; badge d'une journée entière et suppression d'un
 * créneau importé dans la vue Jour. Heures à midi UTC : les dates ne bougent pas quel que soit le fuseau de la machine.
 */
@OptIn(ExperimentalTestApi::class)
class CalendarImportUiTest {
    private val now = Instant.parse("2026-10-20T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private val ics = """
        BEGIN:VCALENDAR
        X-WR-CALNAME:Travail
        BEGIN:VEVENT
        UID:a
        SUMMARY:Revue
        DTSTART:20261020T120000Z
        DTEND:20261020T130000Z
        RRULE:FREQ=WEEKLY;COUNT=2
        END:VEVENT
        BEGIN:VEVENT
        UID:b
        SUMMARY:Conférence
        DTSTART;VALUE=DATE:20261020
        END:VEVENT
        BEGIN:VEVENT
        UID:c
        SUMMARY:Annulée
        STATUS:CANCELLED
        DTSTART:20261021T120000Z
        DTEND:20261021T130000Z
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    private val backups = mutableMapOf<String, String>()

    private fun services(repository: KairosRepository, toOpen: String? = null): AppServices {
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = true
            override suspend fun openText(): String? = toOpen
        }
        return AppServices(repository, files, BackupStore { name, text -> backups[name] = text }, clock = clock)
    }

    private fun repository(): KairosRepository = runBlocking {
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
    }

    /** Fait défiler la liste de la vue Jour jusqu'à [text] (paresseuse : un élément hors écran n'est pas composé). */
    private fun ComposeUiTest.waitInList(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "« $text » dans la liste", timeoutMillis = 15_000) {
            runCatching { onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text, substring = substring)) }.isSuccess
        }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = text, timeoutMillis = 10_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitGone(text: String) =
        waitUntil(conditionDescription = "disparition de $text", timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    @Test
    fun aCalendarIsPreviewedImportedListedReimportedAndRemoved() = runComposeUiTest {
        val repo = repository()
        val services = services(repo, ics)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.DATA)
        // Le bouton « Importer » de la carte Données reconnaît aussi un calendrier.
        waitText("Import")
        onNodeWithText("Import").performScrollTo().performClick()

        // Aperçu : rien n'est écrit.
        waitText("Import a calendar")
        waitText("2 slots from Oct 20 to Oct 27.")
        waitText("Whole-day events: 1, shown as a badge without blocking time.")
        waitText("Cancelled, skipped: 1")
        assertTrue(repo.snapshot.value.timeBlocks.isEmpty())

        onAllNodesWithText("Import").onLast().performClick()
        waitText("Calendar “Travail” imported: 2 slots.")
        assertEquals(2, repo.snapshot.value.timeBlocks.count { it.source != null })
        assertEquals(1, repo.snapshot.value.dayMarks.size)
        assertTrue(backups.keys.any { it.startsWith("avant-import-calendrier-") })

        // Carte « Calendriers » : la ligne du calendrier, puis « Réimporter » : l'aperçu vise ce calendrier.
        waitText("2 upcoming slots", substring = true)
        onNodeWithText("Reimport").performScrollTo().performClick()
        waitText("Replaces the 2 upcoming slots of “Travail”.")
        onAllNodesWithText("Hide titles", substring = true).onLast().performClick()
        onAllNodesWithText("Import").onLast().performClick()
        waitText("Calendar “Travail” imported: 2 slots.")
        waitUntil(timeoutMillis = 5_000) { repo.snapshot.value.calendarSources.single().hideTitles }
        assertTrue(repo.snapshot.value.timeBlocks.all { it.title.isEmpty() })

        // « Retirer » confirmé.
        onNodeWithText("Remove").performScrollTo().performClick()
        waitText("Remove “Travail” and its 2 slots?")
        onAllNodesWithText("Remove").onLast().performClick()
        waitText("Calendar “Travail” removed.")
        assertTrue(repo.snapshot.value.timeBlocks.isEmpty() && repo.snapshot.value.dayMarks.isEmpty() && repo.snapshot.value.calendarSources.isEmpty())
    }

    @Test
    fun withoutABackupNothingIsImported() = runComposeUiTest {
        val repo = repository()
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = true
            override suspend fun openText(): String? = ics
        }
        val services = AppServices(repo, files, BackupStore { _, _ -> error("disque plein") }, clock = clock)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.DATA)
        waitText("Import a calendar…")
        onNodeWithText("Import a calendar…").performScrollTo().performClick()
        waitText("2 slots from Oct 20 to Oct 27.")
        onAllNodesWithText("Import").onLast().performClick()
        waitGone("Import a calendar")
        assertTrue(repo.snapshot.value.timeBlocks.isEmpty() && repo.snapshot.value.calendarSources.isEmpty())
    }

    @Test
    fun aFileWithNothingToImportOnlyCloses() = runComposeUiTest {
        val repo = repository()
        val past = ics.replace("20261020", "20201020").replace("RRULE:FREQ=WEEKLY;COUNT=2", "").replace("20261021", "20201021")
        val services = services(repo, past)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.DATA)
        waitText("Import a calendar…")
        onNodeWithText("Import a calendar…").performScrollTo().performClick()
        waitText("No event to import.")
        waitText("Already over, skipped: 2")
        onNodeWithText("Close").performClick()
        waitGone("No event to import.")
        assertTrue(repo.snapshot.value.calendarSources.isEmpty())
    }

    @Test
    fun theDayViewShowsTheWholeDayBadgeAndDeletesAnImportedSlot() = runComposeUiTest {
        val repo = repository()
        val zone = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(zone).date
        runBlocking { repo.importCalendar("Travail", false, CalendarImport.plan(ics, today, zone), today) }
        val services = services(repo)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        // Journée entière : un badge sous « Aujourd'hui », sans effet sur le planning.
        waitInList("Conférence")
        waitText("Time slot / deep work")
        onNodeWithText("Time slot / deep work").performClick()
        waitText("Imported from “Travail”")
        onNodeWithContentDescription("Delete the imported slot").performClick()
        waitText("Delete this imported slot?")
        waitText("“Revue” will come back at the next import of “Travail”.")
        onAllNodesWithText("Delete").onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { repo.snapshot.value.timeBlocks.size == 1 }
        assertEquals(1, repo.snapshot.value.dayMarks.size)
    }
}
