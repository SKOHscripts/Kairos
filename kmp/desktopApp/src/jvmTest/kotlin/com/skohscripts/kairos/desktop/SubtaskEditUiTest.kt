package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
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
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Sous-tâches existantes dans la fiche de la mère (docs/spec/vue-jour.md § Sous-tâches existantes, issue #69). */
@OptIn(ExperimentalTestApi::class)
class SubtaskEditUiTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-09-29T07:00:00Z")
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

    private fun ComposeUiTest.dialogOpen() = onAllNodesWithText("Edit task").fetchSemanticsNodes().isNotEmpty()

    /** Ouvre la fiche qui a la section « Subtasks » (la mère), en essayant chaque « Edit » de la vue Jour. */
    private fun ComposeUiTest.openTheMothersDialog() {
        // La mère est dans « Parent tasks in progress », une section secondaire repliée (docs/spec/vue-jour.md).
        // La liste est paresseuse : l'en-tête n'est composé qu'une fois la liste défilée jusqu'à lui.
        onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Parent tasks in progress", substring = true))
        onNodeWithText("Parent tasks in progress", substring = true).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("0/3 subtask(s)", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val count = onAllNodesWithContentDescription("Edit").fetchSemanticsNodes().size
        for (index in 0 until count) {
            onAllNodesWithContentDescription("Edit")[index].performClick()
            waitUntil(timeoutMillis = 5_000) { dialogOpen() }
            if (onAllNodesWithText("Subtasks").fetchSemanticsNodes().isNotEmpty()) return
            onNodeWithText("Cancel").performClick()
            waitUntil(timeoutMillis = 5_000) { !dialogOpen() }
        }
        error("aucune fiche avec des sous-tâches parmi $count")
    }

    private data class Family(val mother: Long, val beta: Long, val gamma: Long, val delta: Long)

    private fun setup(services: AppServices): Family = runBlocking {
        val repository = services.repository
        val mother = repository.createTask("Alpha")!!
        val beta = repository.createTask("Beta", parentId = mother)!!
        val gamma = repository.createTask("Gamma", parentId = mother)!!
        val delta = repository.createTask("Delta", parentId = mother)!!
        listOf(mother, beta, gamma, delta).forEach { repository.setPriority(it, 2); repository.setPoints(it, 3); repository.setTaskType(it, "Development") }
        Family(mother, beta, gamma, delta)
    }

    @Test
    fun renameCheckAndDeleteTheSubtasksWithOneSave() = runComposeUiTest {
        val services = services()
        val (mother, beta, gamma, delta) = setup(services)
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Alpha", substring = true).fetchSemanticsNodes().isNotEmpty() }
        openTheMothersDialog()

        onNode(hasSetTextAction() and hasText("Beta")).performScrollTo().performTextReplacement("Beta, renamed")
        onNodeWithContentDescription("Subtask done: Gamma").performScrollTo().performClick()
        onAllNodesWithContentDescription("Delete the subtask")[2].performScrollTo().performClick()
        // La ligne supprimée reste, barrée, et l'on peut revenir en arrière.
        onNodeWithContentDescription("Deleted when you save: Delta").assertExists()
        onNodeWithContentDescription("Undo the deletion").performClick()
        onAllNodesWithContentDescription("Delete the subtask")[2].performClick()

        // Rien n'est écrit avant « Save ».
        assertEquals("Beta", repository.snapshot.value.tasks.single { it.id == beta }.title)
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.tasks.none { it.id == delta } }

        val tasks = repository.snapshot.value.tasks
        assertEquals("Beta, renamed", tasks.single { it.id == beta }.title)
        assertEquals(TaskStatus.DONE, tasks.single { it.id == gamma }.status)
        assertEquals(TaskStatus.TODO, tasks.single { it.id == beta }.status)
        assertTrue(tasks.any { it.id == mother })
    }

    @Test
    fun cancellingKeepsEverythingAsItWas() = runComposeUiTest {
        val services = services()
        val (_, beta, _, delta) = setup(services)
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Alpha", substring = true).fetchSemanticsNodes().isNotEmpty() }
        openTheMothersDialog()
        onNode(hasSetTextAction() and hasText("Beta")).performScrollTo().performTextReplacement("Changed")
        onAllNodesWithContentDescription("Delete the subtask")[2].performScrollTo().performClick()
        onNodeWithText("Cancel").performClick()
        waitUntil(timeoutMillis = 5_000) { !dialogOpen() }
        assertEquals("Beta", repository.snapshot.value.tasks.single { it.id == beta }.title)
        assertTrue(repository.snapshot.value.tasks.any { it.id == delta })
    }

    @Test
    fun aTaskWithoutSubtasksHasNoSubtaskSection() = runComposeUiTest {
        val services = services()
        runBlocking { services.repository.createTask("Solo").also { services.repository.setPriority(it!!, 2); services.repository.setPoints(it, 3); services.repository.setTaskType(it, "Development") } }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Solo", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription("Edit")[0].performClick()
        waitUntil(timeoutMillis = 5_000) { dialogOpen() }
        assertTrue(onAllNodesWithText("Subtasks").fetchSemanticsNodes().isEmpty())
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExists() = assertExists()
