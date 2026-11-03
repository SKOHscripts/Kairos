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
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * L'estimateur par modèle à l'écran (docs/spec/estimateur-modele.md § Interface, issue #99) : pastille et description du
 * modèle, puce « Utiliser … (entre … et …) » et « Tâches proches » de la fiche, état de la vérification et interrupteur dans
 * les Réglages, durée posée au tri, 360 dp. Horloge figée au mardi 29 septembre 2026.
 */
@OptIn(ExperimentalTestApi::class)
class ModelEstimateUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val day = LocalDate(2026, 9, 29)
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private val words = listOf(
        "apple", "birch", "cedar", "dune", "ember", "fjord", "grove", "heath", "inlet", "jade", "kelp", "lotus", "maple", "nectar",
        "oasis", "pine", "quartz", "reef", "sage", "tide",
    )

    /**
     * 40 tâches faites, titres tous différents : rapports « Writing » à 3 points de 45 min, courriers « Admin » à 1 point de
     * 10 min. Rien ne se répète : seul le modèle peut estimer une tâche nouvelle, et il bat nettement les 30 min par défaut.
     */
    private fun history(): List<Task> = (1L..40L).map { i ->
        val w = words[((i - 1) / 2).toInt() % words.size]
        if (i % 2 == 1L) {
            Task(i, "Quarterly report $w", status = TaskStatus.DONE, priority = 2, fibonacciPoints = 3, taskType = "Writing", manualTimeSpentMinutes = 45, createdAt = now, updatedAt = now)
        } else {
            Task(i, "Mail to the bank $w", status = TaskStatus.DONE, priority = 2, fibonacciPoints = 1, taskType = "Admin", manualTimeSpentMinutes = 10, createdAt = now, updatedAt = now)
        }
    }

    private fun services(extra: List<Task>, settings: Settings = Settings(taskTypes = "Writing,Admin")): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(KairosSnapshot(tasks = history() + extra, settings = settings))
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private val target = Task(100, "Quarterly report for the board", priority = 2, fibonacciPoints = 3, taskType = "Writing", scheduledDate = day, createdAt = now, updatedAt = now)

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun theModelBadgeAndTheSheetShowTheEstimateWithItsEvidence() = runComposeUiTest {
        val services = services(listOf(target))
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Quarterly report for the board", substring = true)
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithContentDescription("Estimated by machine learning from 5 similar tasks", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodesWithContentDescription("Edit").onFirst().performClick()
        waitText("Edit task")
        // L'intitulé dit d'où vient la durée proposée.
        onNodeWithText("Machine-learning estimate").performScrollTo().assertExists()
        // « Tâches proches », repliée : chaque voisin avec sa durée réelle et sa ressemblance.
        onNodeWithText("Similar tasks (5)").performScrollTo().performClick()
        waitText("Quarterly report", substring = true)
        assertTrue(onAllNodesWithText("45 min, ", substring = true).fetchSemanticsNodes().size >= 5)
        // La puce ne remplit le champ que sur action.
        onNodeWithText("Use 45 min (between", substring = true).performScrollTo().performClick()
        onNode(hasSetTextAction() and hasText("45")).assertExists()
    }

    @Test
    fun settingsShowTheCheckAndTheSwitchIsSaved() = runComposeUiTest {
        val services = services(listOf(target))
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.PLANNING)
        waitText("Estimate with machine learning")
        waitText("On your last", substring = true)
        onNodeWithText("Estimate with machine learning").performScrollTo().performClick()
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.settings.learning?.model == false }
    }

    @Test
    fun triageFillsTheDurationFromTheModelFirst() = runComposeUiTest {
        // À traiter : type et priorité posés, il manque les points ; les donner pose la durée du modèle.
        val inbox = Task(200, "Quarterly report for the committee", priority = 2, taskType = "Writing", createdAt = now, updatedAt = now)
        val services = services(listOf(inbox))
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("To process (1)")
        onNodeWithText("3 moderate").performScrollTo().performClick()
        waitText("Estimated at 45 min by machine learning (5 similar tasks)")
        assertEquals(45, services.repository.snapshot.value.tasks.single { it.id == 200L }.estimatedMinutes)
    }

    @Test
    fun theModelBadgeFitsAt360dp() = runComposeUiTest {
        // Le dialogue s'ouvre dans la fenêtre entière (hors de la boîte de 360 dp) : on mesure la ligne de la tâche.
        val services = services(listOf(target))
        setContent { Box(Modifier.width(360.dp).height(1400.dp)) { KairosApp(Platform.ANDROID, Destination.DAY, TeamDestination.BOARD) { services } } }
        waitText("Quarterly report for the board", substring = true)
        onAllNodes(hasScrollToNodeAction()).onFirst()
            .performScrollToNode(hasContentDescription("Estimated by machine learning from 5 similar tasks", substring = true))
        val bounds = onAllNodesWithContentDescription("Estimated by machine learning from 5 similar tasks", substring = true).onFirst().getUnclippedBoundsInRoot()
        assertTrue(bounds.right <= 360.dp, "pastille du modèle hors de la fenêtre de 360 dp : $bounds")
    }
}
