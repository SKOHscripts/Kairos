package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.LevelNames
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
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
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Noms des niveaux (docs/spec/planning-personnalise.md § Lot D, issue #93) : un nom choisi remplace le code sur les badges,
 * s'écrit « nom (valeur) » dans les pastilles et le guide des points ; la carte des Réglages enregistre un nom et refuse
 * un nom trop long.
 */
@OptIn(ExperimentalTestApi::class)
class LevelNamesUiTest {
    private val now = Instant.parse("2026-11-03T08:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(names: LevelNames?): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(
            KairosSnapshot(
                tasks = listOf(Task(1, "Quarterly report", priority = 0, fibonacciPoints = 3, taskType = "Development", estimatedMinutes = 60, createdAt = now, updatedAt = now)),
                settings = Settings(levelNames = names),
            ),
        )
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun chosenNamesReplaceTheCodesAndKeepTheValueInTheSheet() = runComposeUiTest {
        val services = services(LevelNames(priority = mapOf(0 to "Urgent"), points = mapOf(3 to "M")))
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Quarterly report", substring = true)
        onAllNodesWithText("Urgent").onFirst().assertExists()
        onAllNodesWithText("M").onFirst().assertExists()
        assertEquals(0, onAllNodesWithText("P0").fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithText("3 pts").fetchSemanticsNodes().size)

        onAllNodesWithContentDescription("Edit").onFirst().performClick()
        waitText("Edit task")
        onNodeWithText("Urgent (P0)", substring = true).assertExists()
        onNodeWithText("P1 Important", substring = true).assertExists()
        onNodeWithText("M (3)").assertExists()
        onNodeWithText("How to estimate points?").performScrollTo().performClick()
        waitText("M (3): ", substring = true)
        onNodeWithText("1 · trivial", substring = true).assertExists()
    }

    @Test
    fun withoutNamesNothingChanges() = runComposeUiTest {
        val services = services(null)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Quarterly report", substring = true)
        onAllNodesWithText("P0").onFirst().assertExists()
        onAllNodesWithText("3 pts").onFirst().assertExists()
    }

    @Test
    fun theSettingsCardSavesANameAndRefusesALongOne() = runComposeUiTest {
        val services = services(null)
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.PLANNING)
        waitText("Level names")
        fun type(label: String, text: String) {
            val field = onNode(hasSetTextAction() and hasText(label)).performScrollTo()
            field.performTextClearance()
            field.performTextInput(text)
        }
        type("P2 Useful", "Someday, maybe later")
        onNodeWithText("Save").performClick()
        waitText("16 characters at most.")
        type("P2 Useful", "Someday")
        type("5 substantial", "L")
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.settings.levelNames != null }
        assertEquals(LevelNames(priority = mapOf(2 to "Someday"), points = mapOf(5 to "L")), repository.snapshot.value.settings.levelNames)
    }
}
