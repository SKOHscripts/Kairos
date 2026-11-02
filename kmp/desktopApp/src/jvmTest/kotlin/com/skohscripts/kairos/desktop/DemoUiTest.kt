package com.skohscripts.kairos.desktop

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.BackupStore
import com.skohscripts.kairos.ui.app.DemoSource
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.app.openDemo
import com.skohscripts.kairos.ui.guide.demo.DEMO_MARK_TAG
import com.skohscripts.kairos.ui.navigation.Destination
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Démonstration interactive (docs/spec/visite-guidee.md § Visite interactive, issue #96) : entrée du hub, bandeau et
 * « Quitter », écrans sans Accueil ni réglages ni aide, « Faire pour moi » jusqu'à la fin, « À moi de jouer » suivi des vrais
 * gestes, retour à l'écran d'avant, entrée grisée pendant un chrono réel. Horloge figée au mardi 29 septembre 2026.
 */
@OptIn(ExperimentalTestApi::class)
class DemoUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()
    private val banner = "Demo: your data is not touched"

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private object NoFiles : FileService {
        override suspend fun saveText(suggestedName: String, text: String) = false
        override suspend fun openText(): String? = null
    }

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        val demo = DemoSource { lang ->
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            openDemo(KairosStore.open(driver), lang, close = { driver.close() }, clock = clock)
        }
        AppServices(repository, NoFiles, BackupStore { _, _ -> }, clock = clock, demo = demo)
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    private fun ComposeUiTest.waitGone(text: String) =
        waitUntil(conditionDescription = "plus de « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size

    /** Accueil → « Découvrir Kairos » → « Essayer en démonstration ». */
    private fun ComposeUiTest.startFromHome() {
        waitText("Discover Kairos")
        onNodeWithText("Discover Kairos").performScrollTo().performClick()
        waitText("Try it in a demo")
        onNodeWithText("Try it in a demo").performClick()
        waitText(banner)
    }

    /** La pastille [label] de la ligne cernée (la tâche suivie), parmi celles des exemples. */
    private fun ComposeUiTest.marked(label: String) = onNode(hasText(label) and hasAnyAncestor(hasTestTag(DEMO_MARK_TAG)))

    @Test
    fun doItForMeLeadsFromTheFirstStepToTheEndAndExitLeavesTheRealDataAlone() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP) { services } }
        startFromHome()
        // Les vrais écrans, sans Accueil, réglages ni aide ; « Quitter » toujours là.
        assertEquals(0, count("Home"))
        assertEquals(0, onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithContentDescription("Help").fetchSemanticsNodes().size)
        listOf("Notes", "Day", "Week", "Stats").forEach { assertEquals(1, count(it), it) }
        assertEquals(1, count("Exit"))
        // Panneau : région vivante polie ; repère « Ici » ; cibles de 48 dp.
        onNode(hasText("Step 1 of 6", substring = true) and SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertExists()
        assertEquals(1, count("Here"))
        onNodeWithText("Do it for me").assertTouchHeightIsEqualTo(48.dp)
        onNodeWithText("My turn").assertTouchHeightIsEqualTo(48.dp)
        for (step in 2..6) {
            onNodeWithText("Do it for me").performClick()
            waitText("Step $step of 6", substring = true)
        }
        onNodeWithText("Do it for me").performClick()
        waitText("You have seen it all")
        onNodeWithText("Exit the demo").performClick()
        waitGone(banner)
        waitText("Discover Kairos")
        // Rien de la démonstration dans la vraie base.
        assertEquals(emptyList(), services.repository.snapshot.value.tasks.map { it.title })
        assertEquals(emptyList(), services.repository.snapshot.value.workSessions)
    }

    @Test
    fun myTurnFocusesTheMarkedElementAndRealGesturesMoveTheTourOn() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP) { services } }
        startFromHome()
        onNodeWithText("My turn").performClick()
        val field = onNode(hasSetTextAction() and hasText("New task"))
        waitUntil(timeoutMillis = 15_000) { runCatching { field.assertIsFocused() }.isSuccess }
        field.performTextInput("Call the bank")
        field.performImeAction()
        waitText("Step 2 of 6", substring = true)
        // « À moi de jouer » à chaque étape : la liste défile jusqu'à la ligne de la tâche, parmi les exemples.
        onNodeWithText("My turn").performClick()
        waitUntil(timeoutMillis = 15_000) { runCatching { marked("P1 Important").fetchSemanticsNode() }.isSuccess }
        marked("P1 Important").performScrollTo().performClick()
        waitText("Step 3 of 6", substring = true)
        onNodeWithText("My turn").performClick()
        waitUntil(timeoutMillis = 15_000) { runCatching { marked("3 moderate").fetchSemanticsNode() }.isSuccess }
        marked("3 moderate").performScrollTo().performClick()
        waitText("Step 4 of 6", substring = true)
        // « Précédent » revient d'une étape sans rien défaire.
        onNodeWithText("Previous").performClick()
        waitText("Step 3 of 6", substring = true)
        assertEquals(emptyList(), services.repository.snapshot.value.tasks)
    }

    @Test
    fun exitBringsBackTheScreenTheDemoWasOpenedFrom() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.STATS) { services } }
        waitText("Statistics")
        onNodeWithContentDescription("Help").performClick()
        waitText("Discover Kairos")
        onNodeWithText("Discover Kairos").performClick()
        onNodeWithText("Try it in a demo").performClick()
        waitText(banner)
        // La démonstration s'ouvre sur la vue Jour.
        waitText("Today")
        onNodeWithText("Exit").performClick()
        waitGone(banner)
        waitText("Statistics")
    }

    @Test
    fun theHubEntryIsGreyedWhileARealTimerRuns() = runComposeUiTest {
        val services = services()
        runBlocking {
            val id = services.repository.createTask("Real work")!!
            services.repository.startTimer(id)
        }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Discover Kairos")
        onNodeWithText("Discover Kairos").performScrollTo().performClick()
        waitText("Stop the running timer first.")
        onNodeWithText("Try it in a demo").assertIsNotEnabled()
        assertEquals(0, count(banner))
    }
}
