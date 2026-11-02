package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.BackupStore
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.day.DayScreen
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.theme.KairosTheme
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Avis des mois sautés (docs/spec/recurrence.md § Avis des mois sautés, issue #111) : une ligne par série sur la vue Jour
 * d'aujourd'hui, « Les recréer » et « Ignorer » avec « Annuler », boutons de 48 dp nommés avec la série, rien hors du vrai
 * aujourd'hui, aucun débordement à 360 dp. Horloge figée au mardi 29 septembre 2026 ; la série « Note de frais » (le 14)
 * n'a qu'une occurrence en juin : juillet et août sont sautés.
 */
@OptIn(ExperimentalTestApi::class)
class SkippedMonthsUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()
    private val notice = "2 monthly occurrences were skipped: Expense report"

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private object NoFiles : FileService {
        override suspend fun saveText(suggestedName: String, text: String) = false
        override suspend fun openText(): String? = null
    }

    private fun services(): AppServices = runBlocking {
        val june = Task(
            1, "Expense report", priority = 1, fibonacciPoints = 2, recurrence = TaskRecurrence.MONTHLY_ON_DAY, recurrenceDayOfMonth = 14,
            recurrencePeriod = "2026-06", deadline = LocalDate(2026, 6, 14), createdAt = now, updatedAt = now,
        )
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(KairosSnapshot(tasks = listOf(june)))
        AppServices(repository, NoFiles, BackupStore { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitGone(text: String) =
        waitUntil(conditionDescription = "plus de « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun AppServices.overdue() = repository.snapshot.value.tasks.filter { it.title == "Expense report" }.mapNotNull { it.recurrencePeriod }.sorted()

    @Test
    fun recreatingAddsTheMissingMonthsAndUndoRemovesThem() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText(notice)
        // Nom accessible : libellé visible suivi de la série ; cible tactile Material 3.
        onNodeWithContentDescription("Recreate them: Expense report").performScrollTo().assertTouchHeightIsEqualTo(48.dp)
        onNodeWithContentDescription("Ignore: Expense report").assertTouchHeightIsEqualTo(48.dp)
        onNodeWithText("Recreate them").performClick()
        waitText("2 occurrences recreated: Expense report")
        waitGone(notice)
        // Juillet et août recréés ; septembre vient de calendarOccurrences.
        assertEquals(listOf("2026-06", "2026-07", "2026-08", "2026-09"), services.overdue())
        onNodeWithText("Undo").performClick()
        waitText(notice)
        assertEquals(listOf("2026-06", "2026-09"), services.overdue())
    }

    @Test
    fun ignoringHidesTheNoticeAndUndoBringsItBack() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText(notice)
        onNodeWithText("Ignore").performScrollTo().performClick()
        waitText("Notice ignored: Expense report")
        waitGone(notice)
        assertEquals(mapOf("14|Expense report" to "2026-08"), services.repository.snapshot.value.settings.recurrence?.skipIgnored)
        assertEquals(listOf("2026-06", "2026-09"), services.overdue())
        onNodeWithText("Undo").performClick()
        waitText(notice)
        assertEquals(null, services.repository.snapshot.value.settings.recurrence)
    }

    @Test
    fun anotherDayShowsNoNotice() = runComposeUiTest {
        val services = services()
        setContent { KairosTheme { DayScreen(services, selectedDay = LocalDate(2026, 9, 30)) } }
        waitText("Back to today")
        waitForIdle()
        assertEquals(0, onAllNodesWithText(notice).fetchSemanticsNodes().size)
    }

    @Test
    fun theNoticeWrapsAt360dpWithoutOverflowing() = runComposeUiTest {
        val services = services()
        setContent { Box(Modifier.width(360.dp).height(1400.dp)) { KairosApp(Platform.ANDROID, Destination.DAY, TeamDestination.BOARD) { services } } }
        waitText(notice)
        for (node in listOf(onNodeWithText(notice), onNodeWithText("Recreate them"), onNodeWithText("Ignore"))) {
            val bounds = node.performScrollTo().getUnclippedBoundsInRoot()
            assertTrue(bounds.right <= 360.dp, "hors de la fenêtre de 360 dp : $bounds")
        }
        // Les boutons passent sous le texte.
        assertTrue(onNodeWithText("Recreate them").getUnclippedBoundsInRoot().top >= onNodeWithText(notice).getUnclippedBoundsInRoot().bottom)
    }
}
