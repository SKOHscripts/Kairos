package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.guide.TipId
import com.skohscripts.kairos.core.guide.Tips
import com.skohscripts.kairos.core.model.GuideSettings
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.guide.TIP_DELAY_MILLIS
import com.skohscripts.kairos.ui.guide.TIP_DIALOG_TAG
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.settings.SettingsCategory
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Le dialogue des conseils (docs/spec/conseils.md, comportement attendu points 1 à 3 et 6) : il vient sur l'Accueil quand rien
 * d'autre n'est en cours, ses trois réponses écrivent la mémoire du guide, « Montre-moi » ouvre l'écran ; jamais pendant un
 * chrono, hors de l'Accueil, avec l'interrupteur coupé ni pour des services qui ne proposent pas de conseils (tests, captures).
 */
@OptIn(ExperimentalTestApi::class)
class TipDialogUiTest {
    /** Un mercredi : la base a dix jours, cinq tâches faites, Statistiques jamais ouvertes → « Découvrir les statistiques ». */
    private val now = Instant.parse("2026-11-18T09:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private val done = (1L..5L).map { Task(it, "Done $it", status = TaskStatus.DONE, createdAt = now - 10.days, updatedAt = now - 2.days) }
    private val data = KairosSnapshot(tasks = done, notes = listOf(Note(1, "First note", createdAt = now - 10.days, updatedAt = now - 10.days)))

    private fun services(snapshot: KairosSnapshot = data, settings: Settings = Settings(), tips: Boolean = true): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(snapshot.copy(settings = settings))
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock, tips = tips)
    }

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.dialogs() = onAllNodesWithTag(TIP_DIALOG_TAG).fetchSemanticsNodes().size

    /** Laisse passer le délai du guetteur, puis le calcul du conseil. */
    private fun ComposeUiTest.afterTheDelay() {
        mainClock.advanceTimeBy(TIP_DELAY_MILLIS + 1_000)
        waitForIdle()
    }

    private fun ComposeUiTest.waitTip() {
        afterTheDelay()
        waitUntil(conditionDescription = "dialogue du conseil", timeoutMillis = 15_000) { dialogs() == 1 }
    }

    private fun guide(services: AppServices) = services.repository.snapshot.value.settings.guide

    @Test
    fun theTipComesOnHomeWithTheUsersNumbersAndShowMeOpensTheScreen() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.HOME, TeamDestination.HOME) { services } }
        waitTip()
        waitText("Discover your statistics")
        waitText("You have finished 5 tasks. The statistics show your pace, your time by type and what is still waiting.")
        waitText("Later")
        waitText("Don’t suggest this again")
        // La date est retenue dès l'affichage (rythme).
        assertEquals(listOf("2026-11-18"), guide(services)?.tipsShown)
        onNodeWithText("Show me").performClick()
        waitUntil(timeoutMillis = 15_000) { dialogs() == 0 }
        // Accepté, et les Statistiques se sont ouvertes : elles retiennent leur première ouverture.
        waitUntil(conditionDescription = "Statistiques ouvertes", timeoutMillis = 15_000) {
            guide(services)?.seenScreens?.contains(Tips.SCREEN_STATS) == true
        }
        assertEquals("2026-11-18", guide(services)?.tipsDone?.get(TipId.STATS_FIRST.key))
    }

    @Test
    fun laterBringsItBackInTwoWeeks() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.HOME, TeamDestination.HOME) { services } }
        waitTip()
        onNodeWithText("Later").performClick()
        waitUntil(timeoutMillis = 15_000) { guide(services)?.tipsSnoozed?.get(TipId.STATS_FIRST.key) == "2026-12-02" }
        assertEquals(0, dialogs())
    }

    @Test
    fun dontSuggestThisAgainDismissesItForGood() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.HOME, TeamDestination.HOME) { services } }
        waitTip()
        onNodeWithText("Don’t suggest this again").performClick()
        waitUntil(timeoutMillis = 15_000) { guide(services)?.tipsDismissed == listOf(TipId.STATS_FIRST.key) }
        assertEquals(0, dialogs())
    }

    @Test
    fun noTipWhileATimerRuns() = runComposeUiTest {
        val running = WorkSession(1, done[0].id, now - 30.minutes, createdAt = now - 30.minutes)
        val services = services(data.copy(workSessions = listOf(running)))
        setContent { KairosApp(Platform.DESKTOP, Destination.HOME, TeamDestination.HOME) { services } }
        afterTheDelay()
        afterTheDelay()
        assertEquals(0, dialogs())
        assertEquals(null, guide(services))
    }

    @Test
    fun noTipAwayFromHome() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.HOME) { services } }
        waitText("Today, in order")
        afterTheDelay()
        assertEquals(0, dialogs())
    }

    @Test
    fun noTipWithTheSwitchOff() = runComposeUiTest {
        val services = services(settings = Settings(guide = GuideSettings(tipsOff = true)))
        setContent { KairosApp(Platform.DESKTOP, Destination.HOME, TeamDestination.HOME) { services } }
        afterTheDelay()
        afterTheDelay()
        assertEquals(0, dialogs())
        assertEquals(GuideSettings(tipsOff = true), guide(services))
    }

    @Test
    fun noTipForServicesWithoutTips() = runComposeUiTest {
        // Les tests et les captures (auto-test, magasins) : `AppServices.tips` faux par défaut.
        val services = services(tips = false)
        setContent { KairosApp(Platform.DESKTOP, Destination.HOME, TeamDestination.HOME) { services } }
        afterTheDelay()
        afterTheDelay()
        assertEquals(0, dialogs())
        assertEquals(null, guide(services))
    }

    @Test
    fun theSwitchInAboutTurnsTipsOffAtOnce() = runComposeUiTest {
        val services = services(tips = false)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.HOME) { services } }
        openSettings(SettingsCategory.ABOUT)
        waitText("Suggest tips")
        onNodeWithText("Suggest tips").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { guide(services) == GuideSettings(tipsOff = true) }
        onNodeWithText("Suggest tips").performClick()
        waitUntil(timeoutMillis = 15_000) { guide(services) == null }
    }
}
