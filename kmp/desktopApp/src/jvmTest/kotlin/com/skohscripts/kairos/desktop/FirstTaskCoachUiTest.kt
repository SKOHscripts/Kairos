package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.ExampleData
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
import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Accompagnement de la première tâche (docs/spec/premiere-tache.md) : base SQLite en mémoire, horloge figée (mardi 29 septembre 2026),
 * interface en anglais.
 */
@OptIn(ExperimentalTestApi::class)
class FirstTaskCoachUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(snapshot: KairosSnapshot = KairosSnapshot(), firstLaunch: Boolean = false): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { snapshot }, clock)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock, firstLaunch = firstLaunch)
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    private fun ComposeUiTest.waitGone(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "disparition de « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isEmpty()
        }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun ComposeUiTest.hasTag(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.type(label: String, text: String) {
        onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextInput(text)
    }

    /** Les champs que chaque étape explique, dans l'ordre des neuf étapes (les balises de la fiche, `EditField.tag`). */
    private val fieldsByStep = listOf(
        listOf("TITLE"),
        listOf("DESCRIPTION"),
        listOf("PRIORITY"),
        listOf("POINTS"),
        // Le type suit les points depuis 3.14.0 (issue #166) : il est dans l'essentiel de la fiche.
        listOf("TYPE"),
        listOf("MINUTES"),
        listOf("DEADLINE", "SCHEDULED"),
        listOf("PROJECT", "MANUAL_TIME", "RECURRENCE", "PIN", "SUBTASKS", "BLOCKERS"),
        emptyList(),
    )

    private fun ComposeUiTest.openFromTheInvitation() {
        waitText("Create your first task")
        onNodeWithText("Walk me through it").performClick()
        waitText("Step 1 of 9")
    }

    @Test
    fun theWholeWalkCreatesTheTaskThenCongratulatesAndExplainsItsPlace() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openFromTheInvitation()
        waitText("Title: a verb and a result")
        // Rien n'est imposé sauf le titre : sans titre, on ne peut pas enregistrer.
        onNodeWithText("Skip the guide").assertExists()
        // Chaque étape met en évidence des champs qui existent dans la vraie fiche.
        type("Title", "Send the quote to Marc")
        for ((index, tags) in fieldsByStep.withIndex()) {
            waitText("Step ${index + 1} of 9")
            tags.forEach { assertTrue(hasTag("edit-field-$it"), "champ « $it » absent de la fiche à l'étape ${index + 1}") }
            if (index == 1) type("Description", "Before Friday")
            if (index == 4) type("Estimated duration (minutes)", "25")
            if (index < fieldsByStep.lastIndex) onNodeWithText("Next").performClick()
        }
        // Les félicitations n'existent pas tant que la tâche ne l'est pas.
        assertEquals(0, count("Your first task is created!"))
        assertEquals(0, repository.snapshot.value.tasks.size)
        onNodeWithText("Save").performClick()
        waitText("Your first task is created!")
        val task = repository.snapshot.value.tasks.single()
        assertEquals("Send the quote to Marc", task.title)
        assertEquals("Before Friday", task.description)
        assertEquals(25, task.estimatedMinutes)
        // Mené à son terme : retenu, l'invitation ne reviendra plus (docs/spec/premiere-tache.md, issue #94).
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.settings.guide?.firstTaskDone == true }

        // Où elle est placée : trois étapes. Sans priorité ni points, la tâche attend dans « À traiter ».
        onNodeWithText("Show me where it is placed").performClick()
        waitText("Step 1 of 3")
        waitText("Not placed yet")
        onNodeWithText("Next").performClick()
        waitText("Its slot")
        onNodeWithText("Next").performClick()
        waitText("The task row")
        onNodeWithText("Finish").performClick()
        waitGone("Step 3 of 3")
        // Fin du parcours : l'invitation a disparu d'elle-même, la tâche est la sienne.
        assertEquals(0, count("Create your first task"))
    }

    /** Choisit [option] dans le champ type (ou catégorie) encore vide : la valeur affichée est « Uncategorised ». */
    private fun ComposeUiTest.pickType(option: String = "Développement") {
        onNode(hasText("Uncategorised") and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(option).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText(option).onFirst().performClick()
    }

    @Test
    fun aQualifiedTaskIsPlacedInTheDayAndItsPlaceExplained() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openFromTheInvitation()
        type("Title", "Plan the sprint")
        onNodeWithText("P1 Important", substring = true).performScrollTo().performClick()
        onNodeWithText("3 moderate", substring = true).performScrollTo().performClick()
        pickType()
        onNodeWithText("Save").performClick()
        waitText("Your first task is created!")
        val task = repository.snapshot.value.tasks.single()
        assertEquals(1 to 3, task.priority to task.fibonacciPoints)
        onNodeWithText("Show me where it is placed").performClick()
        // Priorité, points et type posés : la tâche a une place, et le coach explique son rang.
        waitText("Its place in the day")
        assertEquals(0, count("Not placed yet"))
    }

    @Test
    fun givingUpWritesNothingAndReopeningStartsAgainAtTheTitle() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openFromTheInvitation()
        type("Title", "Half written")
        onNodeWithText("Next").performClick()
        waitText("Step 2 of 9")
        onNodeWithText("Cancel").performClick()
        waitGone("Step 2 of 9")
        assertEquals(0, repository.snapshot.value.tasks.size)
        assertEquals(0, count("Your first task is created!"))
        // Rouvrir recommence au titre, la fiche vide.
        onNodeWithText("Walk me through it").performClick()
        waitText("Step 1 of 9")
        assertEquals(0, onAllNodesWithText("Half written").fetchSemanticsNodes().size)
    }

    @Test
    fun theTitleIsTheOnlyRequiredField() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openFromTheInvitation()
        onNodeWithText("Save").assertIsNotEnabled()
        // Pas de « Supprimer » : rien n'existe encore.
        assertEquals(0, count("Delete"))
    }

    @Test
    fun skippingTheGuideLeavesTheSheetUsable() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openFromTheInvitation()
        onNodeWithText("Skip the guide").performClick()
        waitGone("Step 1 of 9")
        type("Title", "No guide needed")
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.tasks.size == 1 }
        // Créée réellement : les félicitations arrivent, même sans avoir fini le guide.
        waitText("Your first task is created!")
    }

    @Test
    fun theInvitationShowsUntilTheFirstTaskOfTheUserExamplesExcluded() = runComposeUiTest {
        val examples = ExampleData.snapshot(LocalDate(2026, 9, 29), now, "en")
        val services = services(examples)
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Create your first task")
        runBlocking { repository.createTask("Mine") }
        waitGone("Create your first task")
    }

    @Test
    fun theWelcomeOffersTheFirstTaskAndStartsTheWalk() = runComposeUiTest {
        val services = services(ExampleData.snapshot(LocalDate(2026, 9, 29), now, "en"), firstLaunch = true)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Create my first task")
        onNodeWithText("Create my first task").performClick()
        // L'espace Perso, la vue Jour, la fiche et son coach.
        waitText("Step 1 of 9")
    }
}
