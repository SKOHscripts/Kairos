package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Vue Mois (docs/spec/vue-mois.md, issue #107) : « Month » dans la destination Semaine, une case par jour lue en entier par
 * le lecteur d'écran, toucher un jour ouvre la vue Jour, mois suivant. Horloge figée au mardi 3 novembre 2026 ; 11 novembre
 * férié (calendrier français).
 */
@OptIn(ExperimentalTestApi::class)
class MonthViewUiTest {
    private val now = Instant.parse("2026-11-03T08:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(
            KairosSnapshot(
                tasks = listOf(
                    Task(1, "Quarterly report", priority = 0, fibonacciPoints = 5, estimatedMinutes = 600, deadline = LocalDate(2026, 11, 10), createdAt = now, updatedAt = now),
                ),
                settings = Settings(holidaysFr = true),
            ),
        )
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.openMonth(width: Int) {
        setContent { Box(Modifier.width(width.dp).height(1400.dp)) { KairosApp(Platform.DESKTOP, Destination.WEEK, TeamDestination.BOARD) { services() } } }
        waitText("Month")
        onAllNodesWithText("Month").onFirst().performClick()
        waitText("November 2026")
    }

    @Test
    fun aDayOfTheMonthOpensTheDayView() = runComposeUiTest {
        openMonth(1200)
        waitText("Month · November 2026")
        // Mardi 10 : la tâche de 10 h due ce jour dépasse la journée de 9 h.
        val tuesday = "Tuesday, November 10, 2026. Planned tasks: 1. Deadlines: 1. Overloaded."
        onNodeWithContentDescription(tuesday).assertExists()
        onNodeWithContentDescription("Wednesday, November 11, 2026. Planned tasks: 0. Deadlines: 0. Public holiday.").assertExists()
        // Les listes sous la grille disent la même chose en texte.
        onNodeWithText("Nov 10: Quarterly report").assertExists()
        onNodeWithText("Wed 11/11 · public holiday").assertExists()
        onNodeWithContentDescription(tuesday).performScrollTo().performClick()
        waitText("Day · Tuesday, November 10, 2026")
    }

    @Test
    fun theNextMonthAndBackToTheWeek() = runComposeUiTest {
        openMonth(1200)
        onNodeWithContentDescription("Next month").performClick()
        waitText("Month · December 2026")
        // Le segment « Week » de la bascule (le premier « Week » est la destination du rail, qui garde le mode).
        onAllNodesWithText("Week").onLast().performClick()
        // Un autre mois revient à la semaine de son 1er.
        waitText("Week · from Monday, November 30, 2026")
    }

    @Test
    fun theGridFitsAt360dp() = runComposeUiTest {
        openMonth(360)
        val cells = onAllNodesWithContentDescription("November", substring = true).fetchSemanticsNodes()
        assertTrue(cells.size >= 30, "une case par jour : ${cells.size}")
        onAllNodesWithContentDescription("November", substring = true).fetchSemanticsNodes().forEach { node ->
            val right = node.boundsInRoot.right
            assertTrue(right <= 360f * 1.0f * density.density + 0.5f, "case hors de la fenêtre de 360 dp : ${node.boundsInRoot}")
        }
        onNodeWithContentDescription("Monday, November 30, 2026", substring = true).assertHeightIsAtLeast(48.dp)
    }
}
