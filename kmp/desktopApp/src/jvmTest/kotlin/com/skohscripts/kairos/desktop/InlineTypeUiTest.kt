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
 * (docs/spec/vue-jour.md, issue #166) : enregistré aussitôt, la durée ne bouge pas, la tâche reste à qualifier tant que
 * priorité ou points manquent, et rien ne déborde à 360 dp. Horloge figée au mardi 29 septembre 2026.
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
    fun theTypeFieldFitsAt360dp() = runComposeUiTest {
        val services = services(KairosSnapshot(tasks = listOf(draft()), settings = Settings(taskTypes = "Meeting,Writing")))
        setContent { Box(Modifier.width(360.dp).height(1400.dp)) { KairosApp(Platform.ANDROID, Destination.DAY, TeamDestination.BOARD) { services } } }
        waitText("To process (1)")
        val bounds = onNode(hasText("Uncategorised") and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).performScrollTo().getUnclippedBoundsInRoot()
        assertTrue(bounds.right <= 360.dp, "champ Type hors de la fenêtre de 360 dp : $bounds")
        assertTrue(bounds.bottom - bounds.top >= 48.dp, "cible du champ Type sous 48 dp : $bounds")
    }
}
