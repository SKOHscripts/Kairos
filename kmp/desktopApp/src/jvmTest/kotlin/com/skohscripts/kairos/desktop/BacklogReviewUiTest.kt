package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
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
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Revue de ce qui traîne dans le Bilan (docs/spec/taches-qui-trainent.md) : décompte, pastille « traîne depuis N j »,
 * ordre par âge, « Déplacer » depuis le backlog. Horloge figée au mardi 29 septembre 2026, échéance implicite à 30 j.
 */
@OptIn(ExperimentalTestApi::class)
class BacklogReviewUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun task(id: Long, title: String, age: Int) =
        Task(id, title, priority = 1, fibonacciPoints = 3, createdAt = now - age.days, updatedAt = now)

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(
            KairosSnapshot(
                tasks = listOf(task(1, "Fresh idea", 3), task(2, "Old chore", 47), task(3, "Ancient chore", 120)),
                settings = Settings(),
            ),
        )
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun lingeringTasksAreCountedMarkedAndSchedulable() = runComposeUiTest {
        val services = services()
        setContent { Box(Modifier.width(1200.dp).height(1600.dp)) { KairosApp(Platform.DESKTOP, Destination.WEEK, TeamDestination.BOARD) { services } } }
        waitText("Backlog, no date (3) · lingering: 2")
        onNodeWithText("Backlog, no date (3) · lingering: 2").performScrollTo().performClick()
        waitText("lingering for 120 d")
        onNodeWithText("lingering for 47 d").assertExists()
        assertTrue(onAllNodesWithText("lingering for 3 d").fetchSemanticsNodes().isEmpty())
        // Du plus ancien au plus récent.
        val ancient = onNodeWithText("Ancient chore").getUnclippedBoundsInRoot().top
        val old = onNodeWithText("Old chore").getUnclippedBoundsInRoot().top
        val fresh = onNodeWithText("Fresh idea").getUnclippedBoundsInRoot().top
        assertTrue(ancient < old && old < fresh, "ordre par âge : $ancient, $old, $fresh")

        // Toucher la tâche ouvre « Déplacer », aujourd'hui compris : elle passe en tête de la journée.
        onNode(hasText("Ancient chore") and hasClickAction()).performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText("Tue 9/29") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasText("Tue 9/29") and hasClickAction()).performClick()
        waitUntil(timeoutMillis = 15_000) {
            services.repository.snapshot.value.tasks.single { it.id == 3L }.scheduledDate == LocalDate(2026, 9, 29)
        }
        waitText("Backlog, no date (2) · lingering: 1")
    }
}
