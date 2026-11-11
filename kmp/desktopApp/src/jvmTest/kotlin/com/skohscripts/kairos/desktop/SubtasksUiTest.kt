package com.skohscripts.kairos.desktop

import kotlin.test.assertTrue
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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

/**
 * Sous-tâches dans l'interface (docs/spec/sous-taches.md, issues #201 et #209) : priorité imposée, échéance héritée, agrégat,
 * conseil, file enchaînée.
 */
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
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("due Nov 20 (from “Alpha”)", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
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
        // Aucune tâche de 13 points terminée : trop peu pour savoir, Kairos conseille de découper ; le résumé le signale.
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("advice: split it", substring = true).fetchSemanticsNodes().isNotEmpty() }
        openSheetSection("Qualification")
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
        // Rien n'est listé tant qu'on n'a rien tapé (docs/spec/fiche-tache.md) : plus de mur de cases.
        assertTrue(onAllNodes(hasText("Epsilon") and hasClickAction() and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isEmpty())
        val search = hasSetTextAction() and hasText("Add a blocker…")
        onNode(search).performScrollTo().performTextInput("ÉPS")
        // Casse et accents ignorés : seule Epsilon est proposée (le titre de l'agenda derrière la fiche ne compte pas).
        val epsilon = hasText("Epsilon") and hasClickAction() and hasAnyAncestor(isDialog()) and !hasSetTextAction()
        waitUntil(conditionDescription = "résultat Epsilon", timeoutMillis = 5_000) { onAllNodes(epsilon).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodes(hasText("Delta") and hasClickAction() and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isEmpty())
        // Toucher le résultat en fait une puce, vide la recherche, et la section le résume.
        onNode(epsilon).performScrollTo().performClick()
        waitUntil(conditionDescription = "puce Epsilon", timeoutMillis = 5_000) { onAllNodesWithContentDescription("Remove “Epsilon”").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("1 blocker(s)").fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
        onNodeWithContentDescription("Remove “Epsilon”").performClick()
        waitUntil(conditionDescription = "puce retirée", timeoutMillis = 5_000) { onAllNodesWithContentDescription("Remove “Epsilon”").fetchSemanticsNodes().isEmpty() }
        onNode(search).performTextReplacement("zzz")
        waitUntil(conditionDescription = "aucun résultat", timeoutMillis = 5_000) { onAllNodesWithText("No task matches “zzz”.").fetchSemanticsNodes().isNotEmpty() }
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
        openSheetSection("Qualification")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Split into subtasks").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Split into subtasks").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasSetTextAction() and hasText("New subtasks (one per line)") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theChainSwitchQueuesNewSubtasksAndIsRemembered() = runComposeUiTest {
        val services = services()
        runBlocking {
            val r = services.repository
            listOf("Lancer la campagne", "Autre sujet").forEach { title ->
                val id = r.createTask(title)!!
                r.setPriority(id, 2); r.setPoints(id, 3); r.setTaskType(id, "Development")
            }
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Lancer la campagne", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // La mémoire est celle de l'appareil, pas d'une tâche : la première fiche de l'agenda suffit, les deux fois.
        fun openFirstSheet() {
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Edit").fetchSemanticsNodes().isNotEmpty() }
            onAllNodesWithContentDescription("Edit")[0].performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Edit task").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Splitting and sequencing").performScrollTo().performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Chain them", substring = true).fetchSemanticsNodes().isNotEmpty() }
        }
        val chain = hasText("Chain them", substring = true) and androidx.compose.ui.test.isToggleable()
        openFirstSheet()
        // Coupé la première fois ; activé, il enchaîne les lignes saisies.
        onNode(chain).performScrollTo().assertIsOff().performClick().assertIsOn()
        onNode(hasSetTextAction() and hasText("New subtasks (one per line)")).performScrollTo().performTextInput("Brief\nVisuels\nEnvoi")
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { services.repository.snapshot.value.dependencies.size == 2 }
        val title = services.repository.snapshot.value.tasks.associate { it.id to it.title }
        kotlin.test.assertEquals(
            setOf("Visuels" to "Brief", "Envoi" to "Visuels"),
            services.repository.snapshot.value.dependencies.mapTo(HashSet()) { title.getValue(it.taskId) to title.getValue(it.blockerId) },
        )
        // La fiche suivante propose le dernier choix.
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Edit task").fetchSemanticsNodes().isEmpty() }
        openFirstSheet()
        onNode(chain).performScrollTo().assertIsOn()
    }
}
