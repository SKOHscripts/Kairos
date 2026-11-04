package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToIndexAction
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
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import com.skohscripts.kairos.ui.app.ChronoNotifier
import com.skohscripts.kairos.ui.app.NotifyState
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Vue Jour de bout en bout (docs/spec/vue-jour.md § Critères de succès) :
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

    /** Choisit [option] dans le champ type (ou catégorie) encore vide : la valeur affichée est « Uncategorised ». */
    private fun ComposeUiTest.pickType(option: String = "Développement") {
        onNode(hasText("Uncategorised") and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(option).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText(option).onFirst().performClick()
    }

    @Test
    fun captureQualifyThenFinish() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("To process (0)").fetchSemanticsNodes().isNotEmpty() }

        // Capture : titre seul, la tâche arrive « À traiter ».
        onNodeWithText("New task").performTextInput("Write the report")
        onNodeWithText("Add").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("To process (1)").fetchSemanticsNodes().isNotEmpty() }

        // Un clic par valeur : priorité et points ne suffisent pas, il manque encore le type (2026-11-03).
        onNodeWithText("P1 Important").performClick()
        onNodeWithText("3 moderate").performClick()
        onNodeWithText("Missing: type").assertExists()
        assertTrue(onAllNodesWithText("To process (1)").fetchSemanticsNodes().isNotEmpty())
        // Le type posé : elle quitte la boîte de réception et entre dans l'agenda à 9 h.
        pickType()
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
    fun theShortcutsStayQuietWhileTypingTheTitleOfASlot() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("To process (0)").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Time slot / deep work").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Start (HH:MM)").fetchSemanticsNodes().isNotEmpty() }
        // « n » (raccourci « nouvelle tâche ») dans le titre du créneau : une saisie, pas un raccourci.
        onNode(hasSetTextAction() and hasText("Title")).performClick()
        onNode(hasSetTextAction() and hasText("Title")).performKeyInput { pressKey(Key.N) }
        waitForIdle()
        assertEquals(1, onAllNodesWithText("Start (HH:MM)").fetchSemanticsNodes().size, "le raccourci N a quitté le formulaire du créneau")
    }

    @Test
    fun snoozeMovesTheDeadlineToTheNextWorkingDay() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        runBlocking {
            val id = repository.createTask("Call back")!!
            repository.setPriority(id, 1)
            repository.setPoints(id, 2)
            repository.setTaskType(id, "Development")
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Snooze").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Snooze").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.tasks.single().deadline != null }
        assertEquals("2026-09-30", repository.snapshot.value.tasks.single().deadline.toString())
    }

    @Test
    fun moveSchedulesATaskOnAnotherDayAndCanBeUndone() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        runBlocking {
            val id = repository.createTask("Call back")!!
            repository.setPriority(id, 1)
            repository.setPoints(id, 2)
            repository.setTaskType(id, "Development")
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Move to another day…").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription("Move to another day…")[0].performClick()
        // Mardi 29 septembre : demain est le mercredi 30.
        onNodeWithText("Tomorrow", substring = true).performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.tasks.single().scheduledDate != null }
        assertEquals("2026-09-30", repository.snapshot.value.tasks.single().scheduledDate.toString())
        // Programmée plus tard : la tâche quitte « Aujourd'hui », l'échéance n'a pas bougé, et le message propose d'annuler.
        assertEquals(null, repository.snapshot.value.tasks.single().deadline)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Undo").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.tasks.single().scheduledDate == null }
    }

    @Test
    fun aDayOffPlacesNothingAndSaysSo() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        runBlocking {
            val id = repository.createTask("Call back")!!
            repository.setPriority(id, 1)
            repository.setPoints(id, 2)
            repository.setTaskType(id, "Development")
            // Le test tourne un mardi (29 septembre 2026) : le mardi est libre.
            repository.updateSettings(repository.snapshot.value.settings.copy(schedule = com.skohscripts.kairos.core.model.ScheduleSettings(mapOf(1 to com.skohscripts.kairos.core.model.DaySchedule(free = true)))))
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Day off:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Rien n'est placé : la tâche attend sous « No slot today ».
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("No slot today", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun blockCaptureAppearsInTheDayList() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
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
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        // La liste est paresseuse : sur une base vide l'invitation « Première tâche » pousse la carte de recherche hors de l'écran.
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Search / filter"))
        onNodeWithTag("day-screen").assertIsFocused()
        onNodeWithTag("day-screen").performKeyInput { pressKey(Key.Slash) }
        // La recherche s'ouvre et prend le curseur (son texte d'exemple n'apparaît qu'avec le focus).
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Search").fetchSemanticsNodes().isNotEmpty() }
        onNode(hasSetTextAction() and hasText("Search")).assertIsFocused()
    }

    @Test
    fun shortcutNFocusesTheTaskCapture() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
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
            listOf(a, b).forEach { repository.setPriority(it, 2); repository.setPoints(it, 3); repository.setTaskType(it, "Development") }
            a to b
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Alpha", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription("Edit").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Edit task").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("More options").performScrollTo().performClick()
        onNode(hasSetTextAction() and hasText("Fixed time (HH:MM)")).performScrollTo().performTextInput("14:30")
        onNodeWithText("Beta").performScrollTo().performClick()
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.dependencies.isNotEmpty() }
        val dep = repository.snapshot.value.dependencies.single()
        assertEquals(a to b, dep.taskId to dep.blockerId)
        assertEquals("2026-09-29T14:30", repository.snapshot.value.tasks.single { it.id == a }.pinnedStart.toString())
        // Alpha, bloquée par Beta, passe dans « Bloquées ».
        // La liste est paresseuse : la ligne de Beta porte maintenant « Blocks “Alpha” » et la section est plus bas.
        onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Blocked (1)"))
        onNodeWithText("Blocked (1)").assertExists()
        // « Bloquées » s'est ouverte pour ce coup, avec la tâche bloquée, et la ligne de Beta dit ce qu'elle bloque.
        onNodeWithText("Blocks “Alpha”").assertExists()
    }

    /** Horloge réglable et notifications système simulées (refusées : le bandeau et le titre prennent le relais). */
    private class Harness {
        var now = Instant.parse("2026-09-29T07:00:00Z")
        val clock = object : Clock {
            override fun now() = this@Harness.now
        }
        val notified = mutableListOf<String>()
        val titles = mutableListOf<String?>()
        var beeps = 0
        val notifier = object : ChronoNotifier {
            override val state = MutableStateFlow(NotifyState.DENIED)
            override suspend fun notify(title: String, body: String, tag: String): Boolean {
                notified += body
                return false
            }
            override fun setTitle(prefix: String?) {
                titles += prefix
            }
            override fun beep() {
                beeps++
            }
        }
    }

    @Test
    fun timerRunsAlertsAndStops() = runComposeUiTest {
        val h = Harness()
        val services = runBlocking {
            val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, h.clock)
            val id = repository.createTask("Write the report")!!
            repository.setPriority(id, 1)
            repository.setPoints(id, 3)
            repository.setTaskType(id, "Development")
            repository.updateSettings(repository.snapshot.value.settings.copy(pomodoroFocusMinutes = 1, timerAlertSound = true))
            AppServices(repository, object : FileService {
                override suspend fun saveText(suggestedName: String, text: String) = false
                override suspend fun openText(): String? = null
            }, { _, _ -> }, clock = h.clock, notifier = h.notifier)
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Start the timer").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Notifications are blocked: alerts will show in the app.").assertExists()
        onNodeWithText("No timer running. Start it on the current task to track your real time.").assertExists()

        onNodeWithText("Start the timer").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Stop the timer").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Right now").assertExists()
        assertEquals(1, services.repository.snapshot.value.workSessions.count { it.endedAt == null })

        // Une minute plus tard : le seuil de pause (1 min) est franchi pendant la veille.
        h.now += 61.seconds
        mainClock.advanceTimeBy(2_000)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Write the report · 1 min of continuous focus: time for a short break?").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("1 min of continuous focus: time for a short break?"), h.notified)
        assertEquals(1, h.beeps)
        assertTrue(h.titles.any { it != null && it.startsWith("⚠") })

        // Le bandeau se ferme d'un clic ; le chrono s'arrête depuis « En ce moment ».
        onNodeWithContentDescription("Dismiss the alert").performClick()
        onAllNodesWithText("Stop the timer").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { services.repository.snapshot.value.workSessions.all { it.endedAt != null } }
        waitUntil(timeoutMillis = 5_000) { h.titles.lastOrNull() == null }
    }
}
