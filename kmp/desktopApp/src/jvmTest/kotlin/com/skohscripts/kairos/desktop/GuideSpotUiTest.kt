package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.ui.guide.FeatureCatalog
import com.skohscripts.kairos.ui.guide.GuideChapter
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
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
 * Cartes du guide qui ouvrent la section précise d'un écran (docs/spec/visite-guidee.md, issue #95) : l'écran défile
 * jusqu'à la section et la met en valeur (« Here »), qui s'efface d'elle-même ; une section absente le dit.
 */
@OptIn(ExperimentalTestApi::class)
class GuideSpotUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(snapshot: KairosSnapshot = KairosSnapshot()): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(snapshot)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun task(id: Long, title: String) =
        Task(id, title, priority = 1, fibonacciPoints = 2, estimatedMinutes = 30, createdAt = now, updatedAt = now)

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    private fun ComposeUiTest.waitGone(text: String) =
        waitUntil(conditionDescription = "disparition de « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    /** Aide → « Discover Kairos » → [chapter] → [next] fois « Next » → « Show me the screen ». */
    /** Nombre de « Next » pour atteindre la carte [id] de [chapter]. */
    private fun position(chapter: GuideChapter, id: String) = FeatureCatalog.chapter(chapter).indexOfFirst { it.id == id }.also { require(it >= 0) }

    private fun ComposeUiTest.showCard(chapter: String, next: Int, title: String) {
        onNodeWithContentDescription("Help").performClick()
        onNodeWithText("Discover Kairos").performClick()
        waitText(chapter)
        onNodeWithText(chapter).performClick()
        repeat(next) { onNodeWithText("Next").performClick() }
        waitText(title)
        onNodeWithText("Show me the screen").performClick()
    }

    @Test
    fun learnedDurationsOpensThePlanningCategoryAndMarksItsCard() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        // « Follow » : la place de la carte est tirée du catalogue.
        showCard("Follow", position(GuideChapter.FOLLOW, "learned"), "Learned durations")
        waitText("Learn durations from past occurrences")
        waitText("Here")
        // La mise en valeur s'efface d'elle-même (3 s).
        waitGone("Here")
    }

    @Test
    fun blockersOpensTheBlockedSectionAndMarksIt() = runComposeUiTest {
        val services = services(
            KairosSnapshot(
                tasks = listOf(task(1, "Get the quote"), task(2, "Sign the contract")),
                dependencies = listOf(TaskDependency(1, taskId = 2, blockerId = 1, createdAt = now)),
            ),
        )
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        // Les lignes de l'agenda portent l'heure devant le titre.
        waitText("Sign the contract", substring = true)
        // « Organize » : la place de la carte des bloqueurs est tirée du catalogue.
        showCard("Organize", position(GuideChapter.ORGANIZE, "dependencies"), "Blockers")
        // La section « Blocked », repliée par défaut, est ouverte (son aide s'affiche) et mise en valeur.
        waitText("Waiting for another task to be finished before they can move on.")
        waitText("Here")
    }

    @Test
    fun theDarkThemeCardOpensTheAppearanceCardAndMarksIt() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        // « Settings » : la place de la carte est tirée du catalogue (une carte ajoutée avant elle ne casse pas le test).
        showCard("Settings", position(GuideChapter.SETTINGS, "darktheme"), "Dark theme")
        waitText("Follows the system.")
        waitText("Here")
    }

    @Test
    fun theHolidaysCardOpensThePublicHolidaysCardAndMarksIt() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        // « Organize » : fériés des créneaux « jours ouvrés » (3.7.0), à sa place dans le catalogue.
        showCard("Organize", position(GuideChapter.ORGANIZE, "holidays_slots"), "Weekday slots skip holidays")
        waitText("Public holidays", substring = true)
        waitText("Here")
    }

    @Test
    fun theRemindersCardOpensTheRemindersSectionAndMarksIt() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        // Rappels (3.8.0, docs/spec/rappels.md) : la section de « Timer and statistics ».
        showCard("Settings", position(GuideChapter.SETTINGS, "reminders"), "Reminders before it is time")
        waitText("Remind me of fixed-time tasks")
        waitText("Here")
    }

    @Test
    fun aMissingSectionIsSaidAndTheScreenStaysAtTheTop() = runComposeUiTest {
        val services = services(KairosSnapshot(tasks = listOf(task(1, "Get the quote"))))
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Get the quote", substring = true)
        showCard("Organize", position(GuideChapter.ORGANIZE, "dependencies"), "Blockers")
        waitText("No blocked task right now.")
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Here").fetchSemanticsNodes().isEmpty() }
    }
}
