package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

/** Priorité imposée par la mère dans « À traiter » (docs/spec/sous-taches.md § Priorité, issue #201). */
@OptIn(ExperimentalTestApi::class)
class SubtaskPriorityUiTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-05T08:00:00Z")
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    @Test
    fun aSubtaskOffersNoPriorityBelowItsMother() = runComposeUiTest {
        val services = services()
        runBlocking {
            val mother = services.repository.createTask("Alpha")!!
            services.repository.setPriority(mother, 0)
            services.repository.createTask("Beta", parentId = mother)
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("At least P0, like “Alpha”", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Pastilles « P0 Critical », « P1 … », « P2 … » : seule la priorité de la mère (ou plus forte) reste proposée.
        onNodeWithText("P0 ", substring = true).assertIsEnabled()
        onNodeWithText("P1 ", substring = true).assertIsNotEnabled()
        onNodeWithText("P2 ", substring = true).assertIsNotEnabled()
    }

    @Test
    fun aChildSaysWhereItsDeadlineComesFromAndTheMotherAddsUp() = runComposeUiTest {
        val services = services()
        runBlocking {
            val r = services.repository
            val mother = r.createTask("Alpha")!!
            r.updateTask(mother, com.skohscripts.kairos.data.TaskEdit(title = "Alpha", priority = 1, deadline = kotlinx.datetime.LocalDate(2026, 11, 20), pinDay = kotlinx.datetime.LocalDate(2026, 11, 5)))
            for ((title, minutes) in listOf("Beta" to 60, "Gamma" to 30)) {
                val id = r.createTask(title, parentId = mother)!!
                r.setPoints(id, 3); r.setTaskType(id, "Development"); r.setEstimate(id, minutes)
            }
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        // La première sous-tâche de l'agenda : sa lignée, et l'échéance qui vient de sa mère.
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("due Nov 20 (from “Alpha”)", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNode(androidx.compose.ui.test.hasScrollToIndexAction())
            .performScrollToNode(androidx.compose.ui.test.hasText("Parent tasks in progress", substring = true))
        onNodeWithText("Parent tasks in progress", substring = true).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("0/2 subtask(s) · 1 h 30 planned").fetchSemanticsNodes().isNotEmpty() }
    }
}
