package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.BackupStore
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/** La méthode GTD expliquée (docs/spec/methode-gtd.md, issue #235). Interface en anglais. */
@OptIn(ExperimentalTestApi::class)
class GtdUiTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-09-29T07:00:00Z")
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(settings: Settings = Settings()): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot(settings = settings) }, clock)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, BackupStore { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitGone(text: String) =
        waitUntil(conditionDescription = "disparition de « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    /** Le dialogue est ouvert : les cinq étapes et les écarts avec GTD ; « Close » le ferme. */
    private fun ComposeUiTest.dialogShowsTheMethodThenCloses() {
        waitText("What Kairos does differently")
        listOf("1. Capture", "2. Clarify", "3. Organize", "4. Reflect", "5. Engage").forEach { onNodeWithText(it).assertExists() }
        onNodeWithText("Close").performClick()
        waitGone("What Kairos does differently")
    }

    @Test
    fun notesSaysItIsTheCaptureStepAndExplainsTheMethod() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.NOTES, TeamDestination.BOARD) { services } }
        waitText("GTD step 1 of 5 · Capture")
        onNodeWithText("Get everything on your mind out of your head, without sorting or deciding. A note can wait here as long as it needs.").assertExists()
        onNodeWithText("The GTD method").performClick()
        dialogShowsTheMethodThenCloses()
    }

    @Test
    fun theStepLinesOpenTheSameDialogExceptOnTheTintedNowCard() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("GTD steps 2 and 3 · Clarify, organize")
        onNodeWithText("GTD step 5 · Engage").assertHasNoClickAction()
        onNodeWithText("GTD steps 2 and 3 · Clarify, organize").performScrollTo().performClick()
        dialogShowsTheMethodThenCloses()
        // Semaine, puis Stats : l'étape 4.
        onAllNodesWithText("Week")[0].performClick()
        waitText("GTD step 4 · Reflect")
        onNodeWithText("GTD step 4 · Reflect").performClick()
        dialogShowsTheMethodThenCloses()
        onAllNodesWithText("Stats")[0].performClick()
        waitText("GTD step 4 · Reflect")
        onNodeWithText("GTD step 4 · Reflect").performClick()
        dialogShowsTheMethodThenCloses()
    }

    @Test
    fun theHelpOffersTheMethodInThePersonalSpaceOnly() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithContentDescription("Help").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Help").performClick()
        waitText("The five steps and where they are in Kairos")
        onNodeWithText("The GTD method").performClick()
        dialogShowsTheMethodThenCloses()
    }

    @Test
    fun theTeamSpaceHelpHasNoGtdEntry() = runComposeUiTest {
        val team = TeamSettings(enabled = true, name = "Platform", managerName = "Corentin", identity = "id-1", lastSpace = TeamSettings.SPACE_TEAM)
        val services = services(Settings(team = team))
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Platform · Tracking")
        onNodeWithContentDescription("Help").performClick()
        waitText("Help: Tracking")
        assertEquals(0, onAllNodesWithText("The five steps and where they are in Kairos").fetchSemanticsNodes().size)
    }
}
