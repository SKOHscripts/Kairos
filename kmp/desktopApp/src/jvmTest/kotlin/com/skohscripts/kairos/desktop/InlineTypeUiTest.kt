package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Type d'une tâche choisi dans « À traiter » (vue Jour) et catégorie dans « À qualifier » (backlog d'équipe)
 * (docs/spec/vue-jour.md, issue #166) : enregistré aussitôt, une durée saisie ne bouge pas, la tâche reste à qualifier
 * tant que priorité ou points manquent ; une fois qualifiée sans durée, la médiane fiable la remplit (tâches d'équipe
 * seulement dans le backlog d'équipe, journalisée) ; rien ne déborde à 360 dp. Horloge figée au mardi 29 septembre 2026.
 */
@OptIn(ExperimentalTestApi::class)
class InlineTypeUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private object NoFiles : FileService {
        override suspend fun saveText(suggestedName: String, text: String) = false
        override suspend fun openText(): String? = null
    }

    private fun services(snapshot: KairosSnapshot): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(snapshot)
        AppServices(repository, NoFiles, { _, _ -> }, clock = clock)
    }

    /** Une tâche capturée, sans priorité ni points, mais avec une durée saisie à la main. */
    private fun draft(space: TaskSpace = TaskSpace.PERSONAL) =
        Task(1, "Draft the newsletter", estimatedMinutes = 30, space = space, createdAt = now, updatedAt = now)

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    private fun AppServices.task() = repository.snapshot.value.tasks.single()

    private fun AppServices.inboxTask() = repository.snapshot.value.tasks.single { it.id == 1L }

    /** Ouvre le champ « [label] » de la ligne (sa valeur affichée est [shown]) et choisit [option]. */
    private fun ComposeUiTest.pick(shown: String, option: String) {
        // Le champ, pas l'étiquette de type que la ligne affiche aussi.
        onNode(hasText(shown) and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(option).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText(option).onFirst().performClick()
    }

    @Test
    fun aTypePickedInToProcessIsSavedWithoutTouchingTheDurationAndTheTaskStaysThere() = runComposeUiTest {
        val services = services(KairosSnapshot(tasks = listOf(draft()), settings = Settings(taskTypes = "Meeting,Writing,Admin")))
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("To process (1)")
        pick("Uncategorised", "Writing")
        waitUntil(timeoutMillis = 15_000) { services.task().taskType == "Writing" }
        // Le type n'écrase pas la durée saisie et n'est pas un critère de qualification.
        assertEquals(30, services.task().estimatedMinutes)
        assertNull(services.task().priority)
        waitText("To process (1)")
        // « Aucun » vide le type.
        pick("Writing", "Uncategorised")
        waitUntil(timeoutMillis = 15_000) { services.task().taskType == "" }
    }

    @Test
    fun finishingTheQualificationFillsAnEmptyDurationFromTheTypeMedianWithUndo() = runComposeUiTest {
        // Trois tâches « Writing » faites en 45 min : une médiane fiable pour ce type.
        val history = (11L..13L).map {
            Task(it, "Old $it", priority = 1, fibonacciPoints = 5, taskType = "Writing", status = TaskStatus.DONE, manualTimeSpentMinutes = 45, createdAt = now, updatedAt = now)
        }
        val inbox = Task(1, "Draft the newsletter", priority = 1, createdAt = now, updatedAt = now)
        val services = services(KairosSnapshot(tasks = history + inbox, settings = Settings(taskTypes = "Meeting,Writing")))
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("To process (1)")
        // Le type seul ne suffit pas : il manque encore les points.
        pick("Uncategorised", "Writing")
        waitUntil(timeoutMillis = 15_000) { services.inboxTask().taskType == "Writing" }
        assertNull(services.inboxTask().estimatedMinutes)
        // Les points complètent la qualification : la médiane du type remplit la durée vide, avec « Annuler ».
        onNodeWithText("3 moderate").performScrollTo().performClick()
        waitText("Estimated at 45 min (median of “Writing”)")
        assertEquals(45, services.inboxTask().estimatedMinutes)
        onNodeWithText("Undo").performClick()
        waitUntil(timeoutMillis = 15_000) { services.inboxTask().estimatedMinutes == null }
    }

    @Test
    fun aCategoryPickedInToQualifyIsSavedOnTheTeamTask() = runComposeUiTest {
        val team = Settings(taskTypes = "Backend,Frontend", team = TeamSettings(enabled = true, name = "Platform", identity = "id-1", lastSpace = TeamSettings.SPACE_TEAM))
        val services = services(KairosSnapshot(tasks = listOf(draft(TaskSpace.TEAM)), settings = team))
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BACKLOG) { services } }
        waitText("To qualify (1)")
        assertTrue(onAllNodesWithText("Category").fetchSemanticsNodes().isNotEmpty())
        pick("Uncategorised", "Frontend")
        waitUntil(timeoutMillis = 15_000) { services.task().taskType == "Frontend" }
        assertEquals(30, services.task().estimatedMinutes)
        waitText("To qualify (1)")
    }

    @Test
    fun theTeamBacklogFillsFromFinishedTeamTasksOnlyAndJournalsTheEstimate() = runComposeUiTest {
        // Trois tâches d'équipe « Frontend » faites en 90 min (temps rapporté) ; une tâche perso « Frontend » de 10 min, ignorée.
        val history = (11L..13L).map {
            Task(it, "Team $it", priority = 1, fibonacciPoints = 5, taskType = "Frontend", status = TaskStatus.DONE, space = TaskSpace.TEAM, reportedMinutes = 90, createdAt = now, updatedAt = now)
        } + (21L..23L).map {
            Task(it, "Mine $it", priority = 1, fibonacciPoints = 5, taskType = "Frontend", status = TaskStatus.DONE, manualTimeSpentMinutes = 10, createdAt = now, updatedAt = now)
        }
        val inbox = Task(1, "Draft the newsletter", priority = 1, space = TaskSpace.TEAM, createdAt = now, updatedAt = now)
        val team = Settings(taskTypes = "Backend,Frontend", team = TeamSettings(enabled = true, name = "Platform", identity = "id-1", lastSpace = TeamSettings.SPACE_TEAM))
        val services = services(KairosSnapshot(tasks = history + inbox, settings = team))
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BACKLOG) { services } }
        waitText("To qualify (1)")
        pick("Uncategorised", "Frontend")
        waitUntil(timeoutMillis = 15_000) { services.inboxTask().taskType == "Frontend" }
        onNodeWithText("3 moderate").performScrollTo().performClick()
        waitText("(median of “Frontend”)", substring = true)
        // La médiane de l'équipe (90 min), pas celle des tâches perso (10 min).
        assertEquals(90, services.inboxTask().estimatedMinutes)
        val estimate = services.repository.snapshot.value.teamEvents.last { it.taskId == 1L }
        assertEquals("estimate:", estimate.fromValue)
        assertEquals("estimate:90", estimate.toValue)
    }

    @Test
    fun theTypeFieldFitsAt360dp() = runComposeUiTest {
        val services = services(KairosSnapshot(tasks = listOf(draft()), settings = Settings(taskTypes = "Meeting,Writing")))
        setContent { Box(Modifier.width(360.dp).height(1400.dp)) { KairosApp(Platform.ANDROID, Destination.DAY, TeamDestination.BOARD) { services } } }
        waitText("To process (1)")
        val bounds = onNode(hasText("Uncategorised") and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).performScrollTo().getUnclippedBoundsInRoot()
        assertTrue(bounds.right <= 360.dp, "champ Type hors de la fenêtre de 360 dp : $bounds")
        assertTrue(bounds.bottom - bounds.top >= 48.dp, "cible du champ Type sous 48 dp : $bounds")
    }
}
