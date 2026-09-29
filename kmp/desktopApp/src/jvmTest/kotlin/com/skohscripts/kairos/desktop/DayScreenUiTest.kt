package com.skohscripts.kairos.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
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
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Vue Jour de bout en bout (docs/spec-v3/vue-jour.md § Critères de succès) :
 * vraie base SQLite en mémoire, horloge figée à 7 h UTC, clics et clavier.
 */
@OptIn(ExperimentalTestApi::class)
class DayScreenUiTest {
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

    @Test
    fun captureQualifyThenFinish() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("To process (0)").fetchSemanticsNodes().isNotEmpty() }

        // Capture : titre seul, la tâche arrive « À traiter ».
        onNodeWithText("New task").performTextInput("Write the report")
        onNodeWithText("Add").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("To process (1)").fetchSemanticsNodes().isNotEmpty() }

        // Un clic par valeur : elle quitte la boîte de réception et entre dans l'agenda à 9 h.
        onNodeWithText("P1 Important").performClick()
        onNodeWithText("3 moderate").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("To process (0)").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Up next:", substring = true).assertExists()
        onAllNodesWithText("09:00", substring = true).onFirst().assertExists()

        // « Pourquoi à cette place ? » s'ouvre d'un clic sur le score.
        onNodeWithContentDescription("Why this position?", substring = true).performClick()
        onNodeWithText("Why this position?").assertExists()
        onNodeWithText("Priority P1 Important").assertExists()
        // Un clic hors du menu le referme.
        onAllNodes(isRoot()).onLast().performMouseInput { click(Offset(1000f, 740f)) }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(isRoot()).fetchSemanticsNodes().size == 1 }

        // « Fait » depuis la carte « Maintenant ».
        onNodeWithText("Done").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.tasks.single().status == TaskStatus.DONE }
    }

    @Test
    fun snoozeMovesTheDeadlineToTheNextWorkingDay() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        runBlocking {
            val id = repository.createTask("Call back")!!
            repository.setPriority(id, 1)
            repository.setPoints(id, 2)
        }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Snooze").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Snooze").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.tasks.single().deadline != null }
        assertEquals("2026-09-30", repository.snapshot.value.tasks.single().deadline.toString())
    }

    @Test
    fun blockCaptureAppearsInTheDayList() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Time slot / deep work").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Time slot / deep work").performClick()
        onNodeWithText("No time slot today.").assertExists()
        onNodeWithText("Title").performTextInput("Focus")
        onNodeWithText("Start (HH:MM)").performTextInput("10:00")
        onNodeWithText("End (HH:MM)").performTextInput("11:30")
        onNodeWithText("Deep work block").performClick()
        onNodeWithText("Add").performClick()
        waitUntil(timeoutMillis = 5_000) { services.repository.snapshot.value.timeBlocks.isNotEmpty() }
        onNodeWithText("10:00–11:30").assertExists()
        val block = services.repository.snapshot.value.timeBlocks.single()
        assertEquals("Focus", block.title)
        assertEquals("DEEPWORK", block.kind.name)
    }

    @Test
    fun shortcutsFocusCaptureAndSearch() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Search / filter").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("day-screen").assertIsFocused()
        onNodeWithTag("day-screen").performKeyInput { pressKey(Key.Slash) }
        // La recherche s'ouvre et prend le curseur (son texte d'exemple n'apparaît qu'avec le focus).
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Search").fetchSemanticsNodes().isNotEmpty() }
        onNode(hasSetTextAction() and hasText("Search")).assertIsFocused()
    }

    @Test
    fun shortcutNFocusesTheTaskCapture() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Time slot / deep work").fetchSemanticsNodes().isNotEmpty() }
        // Même depuis le volet « Créneau », `N` revient sur « Tâche » et y met le curseur.
        onNodeWithText("Time slot / deep work").performClick()
        onNodeWithTag("day-screen").performKeyInput { pressKey(Key.N) }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasSetTextAction() and hasText("New task")).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasSetTextAction() and hasText("New task")).assertIsFocused()
    }

    @Test
    fun editDialogSetsBlockerAndFixedTime() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        val (a, b) = runBlocking {
            val a = repository.createTask("Alpha")!!
            val b = repository.createTask("Beta")!!
            listOf(a, b).forEach { repository.setPriority(it, 2); repository.setPoints(it, 3) }
            a to b
        }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Alpha", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription("Edit").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Edit task").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("More options").performClick()
        onNode(hasSetTextAction() and hasText("Fixed time (HH:MM)")).performTextInput("14:30")
        onNodeWithText("Beta").performScrollTo().performClick()
        onNodeWithText("Save").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.dependencies.isNotEmpty() }
        val dep = repository.snapshot.value.dependencies.single()
        assertEquals(a to b, dep.taskId to dep.blockerId)
        assertEquals("2026-09-29T14:30", repository.snapshot.value.tasks.single { it.id == a }.pinnedStart.toString())
        // Alpha, bloquée par Beta, passe dans « Bloquées ».
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Blocked (1)").fetchSemanticsNodes().isNotEmpty() }
    }
}
