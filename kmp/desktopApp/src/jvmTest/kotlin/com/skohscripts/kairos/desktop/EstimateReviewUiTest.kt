package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.settings.SettingsCategory
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking as block
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Estimations qui changent et estimations saisies à revoir (docs/spec/estimateur-modele.md, besoins 12 et 13) : bandeau après
 * de nouvelles tâches faites, revue depuis les Réglages avec « Passer à … » et « Annuler », proposition de la fiche, réglage.
 * Horloge figée au mardi 29 septembre 2026.
 */
@OptIn(ExperimentalTestApi::class)
class EstimateReviewUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private val words = listOf("apple", "birch", "cedar", "dune", "ember", "fjord", "grove", "heath", "inlet", "jade", "kelp", "lotus", "maple", "nectar", "oasis", "pine", "quartz", "reef", "sage", "tide")

    /** 40 tâches faites, titres tous différents : rapports « Writing » à 3 points de [reportMinutes], courriers « Admin » de 10 min. */
    private fun history(reportMinutes: Int = 45, from: Long = 1): List<Task> = (from until from + 40).map { i ->
        val w = words[((i - from) / 2).toInt() % words.size] + i
        if ((i - from) % 2 == 0L) {
            Task(i, "Quarterly report $w", status = TaskStatus.DONE, priority = 2, fibonacciPoints = 3, taskType = "Writing", manualTimeSpentMinutes = reportMinutes, createdAt = now, updatedAt = now)
        } else {
            Task(i, "Mail to the bank $w", status = TaskStatus.DONE, priority = 2, fibonacciPoints = 1, taskType = "Admin", manualTimeSpentMinutes = 10, createdAt = now, updatedAt = now)
        }
    }

    private fun services(tasks: List<Task>): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(KairosSnapshot(tasks = tasks, settings = Settings(taskTypes = "Writing,Admin")))
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private val open = Task(500, "Quarterly report for the board", priority = 2, fibonacciPoints = 3, taskType = "Writing", createdAt = now, updatedAt = now)
    private val typed = Task(501, "Quarterly report for the committee", priority = 2, fibonacciPoints = 3, taskType = "Writing", estimatedMinutes = 120, createdAt = now, updatedAt = now)

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun newFinishedTasksThatMoveAnEstimateRaiseTheBannerAndTheReview() = runComposeUiTest {
        val services = services(history() + open)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Quarterly report for the board", substring = true)
        waitForIdle()
        // Les nouveaux rapports prennent 2 h : la durée du rapport à faire passe de 45 min à 2 h.
        block { services.repository.replaceAll(services.repository.snapshot.value.copy(tasks = history() + history(120, from = 100) + open)) }
        waitText("estimate(s) changed after your last finished tasks", substring = true)
        onNodeWithText("View").performClick()
        waitText("What changed")
        waitText("Quarterly report for the board: 45 min →", substring = true)
        onNodeWithText("OK").performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("What changed").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun typedEstimatesAreReviewedFromSettingsAndCanBeUndone() = runComposeUiTest {
        val services = services(history() + typed)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.PLANNING)
        waitText("Review typed estimates (1)")
        onNodeWithText("Review typed estimates (1)").performScrollTo().performClick()
        waitText("typed 2 h, machine learning estimates 45 min", substring = true)
        onNodeWithText("Set to 45 min").performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.tasks.single { it.id == 501L }.estimatedMinutes == 45 }
        waitText("Estimate set to 45 min")
        // La revue fermée, « Annuler » de la snackbar remet la durée saisie : l'annulation survit au dialogue.
        onNodeWithText("OK").performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Set to 45 min").fetchSemanticsNodes().isEmpty() }
        onNodeWithText("Undo").performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.tasks.single { it.id == 501L }.estimatedMinutes == 120 }
    }

    @Test
    fun theSheetProposesTheModelForATypedEstimateOnAction() = runComposeUiTest {
        val services = services(history() + typed.copy(scheduledDate = kotlinx.datetime.LocalDate(2026, 9, 29)))
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Quarterly report for the committee", substring = true)
        onAllNodesWithContentDescription("Edit").onFirst().performClick()
        // Section repliée : son résumé signale la durée contestée ; ouverte, la phrase et la puce.
        waitText("the model suggests 45 min", substring = true)
        openSheetSection("Dates and time")
        waitText("Machine learning estimates 45 min", substring = true)
        onNode(hasSetTextAction() and hasText("120")).assertExists()
        onNodeWithText("Set to 45 min").performScrollTo().performClick()
        onNode(hasSetTextAction() and hasText("45")).assertExists()
    }

    @Test
    fun theNotificationSwitchIsSaved() = runComposeUiTest {
        val services = services(history() + open)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.PLANNING)
        waitText("Tell me when estimates change")
        onNodeWithText("Tell me when estimates change").performScrollTo().performClick()
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.settings.learning?.notifyChanges == false }
    }
}
