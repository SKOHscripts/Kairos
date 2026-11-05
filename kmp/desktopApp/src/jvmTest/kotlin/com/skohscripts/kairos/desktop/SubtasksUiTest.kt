package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
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

/** Sous-tâches dans l'interface (docs/spec/sous-taches.md, issue #201) : priorité imposée, échéance héritée, agrégat, conseil. */
@OptIn(ExperimentalTestApi::class)
class SubtasksUiTest {
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

    @Test
    fun theSheetAdvisesToSplitALevelWithoutEnoughHistory() = runComposeUiTest {
        val services = services()
        runBlocking {
            val r = services.repository
            val id = r.createTask("Refondre le site")!!
            r.setPriority(id, 1); r.setPoints(id, 13); r.setTaskType(id, "Development"); r.setEstimate(id, 240)
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Refondre le site", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription("Edit")[0].performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Edit task").fetchSemanticsNodes().isNotEmpty() }
        // Aucune tâche de 13 points terminée : trop peu pour savoir, Kairos conseille de découper.
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Split it into subtasks: no finished task at 13 points yet", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theSectionGathersSubtasksAndBlockersAndBlockersCanBeSearched() = runComposeUiTest {
        val services = services()
        runBlocking {
            val r = services.repository
            val ids = listOf("Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta").map { r.createTask(it)!! }
            ids.forEach { r.setPriority(it, 2); r.setPoints(it, 3); r.setTaskType(it, "Development") }
            // Alpha attend déjà Theta : la case reste visible quelle que soit la recherche.
            r.updateTask(ids[0], com.skohscripts.kairos.data.TaskEdit(title = "Alpha", priority = 2, points = 3, taskType = "Development", pinDay = kotlinx.datetime.LocalDate(2026, 11, 5), blockerIds = setOf(ids[7])))
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Beta", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // La fiche de la première tâche de l'agenda : section repliée (ni sous-tâche ni bloqueur), puis ouverte.
        onAllNodesWithContentDescription("Edit")[0].performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Edit task").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Splitting and sequencing").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Subtasks split this work", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasSetTextAction() and hasText("Find a task")).performScrollTo().performTextInput("ÉPS")
        // Casse et accents ignorés : seule Epsilon reste parmi les cases (le titre de l'agenda derrière la fiche ne compte pas).
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Delta") and androidx.compose.ui.test.isToggleable()).fetchSemanticsNodes().isEmpty() }
        onNode(hasText("Epsilon") and androidx.compose.ui.test.isToggleable()).assertExists()
        onNode(hasSetTextAction() and hasText("Find a task")).performTextReplacement("zzz")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("No task matches “zzz”.").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theAdviceOpensTheSectionOnTheNewSubtasksField() = runComposeUiTest {
        val services = services()
        runBlocking {
            val r = services.repository
            val id = r.createTask("Refondre le site")!!
            r.setPriority(id, 1); r.setPoints(id, 13); r.setTaskType(id, "Development"); r.setEstimate(id, 240)
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Refondre le site", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription("Edit")[0].performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Split into subtasks").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Split into subtasks").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasSetTextAction() and hasText("New subtasks (one per line)") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
    }
}
