package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
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
 * Supprimer une occurrence d'une série « le N du mois » (docs/spec/recurrence.md, issue #282) : la confirmation demande « Seulement
 * ce mois » ou « Arrêter la série », et l'occurrence ne revient pas. Horloge figée au mercredi 2 décembre 2026.
 */
@OptIn(ExperimentalTestApi::class)
class CalendarSeriesUiTest {
    private val now = Instant.parse("2026-12-02T08:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun occurrence(id: Long, month: Int, status: TaskStatus) = Task(
        id, "PV EDF", status = status, priority = 1, fibonacciPoints = 2, recurrence = TaskRecurrence.MONTHLY_ON_DAY, recurrenceDayOfMonth = 2,
        recurrencePeriod = "2026-%02d".format(month), deadline = LocalDate(2026, month, 2), scheduledDate = LocalDate(2026, month, 2),
        createdAt = now, updatedAt = now,
    )

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(
            KairosSnapshot(
                tasks = listOf(occurrence(1, 11, TaskStatus.DONE), occurrence(2, 12, TaskStatus.TODO)),
                settings = Settings(taskTypes = "", holidaysFr = false),
            ),
        )
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.openDelete() {
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithContentDescription("Edit").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription("Edit").onFirst().performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Delete").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Delete").performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Delete “PV EDF”?").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun onlyThisMonthDoesNotComeBack() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openDelete()
        onNodeWithTag("delete-series-month").performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.tasks.none { it.id == 2L } }
        // La vue Jour s'affiche de nouveau : rien n'est recréé ce mois-ci.
        runBlocking { services.repository.ensureCalendarOccurrences(LocalDate(2026, 12, 2)) }
        assertTrue(services.repository.snapshot.value.tasks.none { it.status == TaskStatus.TODO })
    }

    @Test
    fun stoppingTheSeriesKeepsTheHistory() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openDelete()
        onNodeWithTag("delete-series-stop").performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.tasks.none { it.id == 2L } }
        val past = services.repository.snapshot.value.tasks.single()
        assertTrue(past.id == 1L && past.recurrence == TaskRecurrence.NONE)
    }
}
