package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
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
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** L'échéance implicite apprise est dite dans les Réglages (docs/spec/taches-qui-trainent.md § Échéance apprise). */
@OptIn(ExperimentalTestApi::class)
class LingerSettingsUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    /** [count] tâches sans date faites aux âges 0, 1, 2… */
    private fun services(count: Int): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        val created = now - 100.days
        val tasks = (0 until count).map { age ->
            Task(age + 1L, "Done $age", priority = 1, fibonacciPoints = 3, status = TaskStatus.DONE, createdAt = created, updatedAt = created + age.days)
        }
        repository.replaceAll(KairosSnapshot(tasks = tasks, settings = Settings()))
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.openScore(services: AppServices) {
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.PLANNING)
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Advanced: how tasks are ordered").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Advanced: how tasks are ordered").performScrollTo().performClick()
    }

    @Test
    fun anEmptyFieldAppliesTheLearnedValueAndATypedOneWins() = runComposeUiTest {
        val services = services(40)
        openScore(services)
        // 40 tâches faites de 0 à 39 j : 4 sur 5 le sont avant 31 j. Le champ reste vide : rien ne se remplit.
        onNodeWithText("Applied, learned from your pace: 31 d", substring = true).performScrollTo().assertExists()
        val field = onNode(hasSetTextAction() and hasText("Implicit deadline of undated tasks (days)")).performScrollTo()
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))

        // Une valeur saisie l'emporte ; la valeur calculée reste dite, pour info.
        field.performTextInput("45")
        onNodeWithText("Your value applies. For your information, your pace would give 31 d", substring = true).performScrollTo().assertExists()
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.settings.implicitDeadlineDays == 45 }
    }

    @Test
    fun theFallbackIsSaidWithoutHistory() = runComposeUiTest {
        openScore(services(5))
        onNodeWithText("Not enough history to learn it yet", substring = true).performScrollTo().assertExists()
        onNodeWithText("the default 30 d apply", substring = true).assertExists()
    }
}
