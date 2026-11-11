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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.WorkSession
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Corriger le temps passé dans la fiche (docs/spec/fiche-tache.md § Temps passé) : un chrono oublié, puis « Annuler ». */
@OptIn(ExperimentalTestApi::class)
class SpentCorrectionUiTest {
    private val now = Instant.parse("2026-11-11T08:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()
    private val forgottenEnd = Instant.parse("2026-11-10T15:00:00Z")

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(
            KairosSnapshot(
                tasks = listOf(Task(1, "Quarterly report", priority = 1, fibonacciPoints = 3, taskType = "Writing", createdAt = now, updatedAt = now)),
                // 1 h lundi, puis un chrono oublié de 6 h mardi.
                workSessions = listOf(
                    WorkSession(1, 1, Instant.parse("2026-11-09T09:00:00Z"), Instant.parse("2026-11-09T10:00:00Z"), now),
                    WorkSession(2, 1, Instant.parse("2026-11-10T09:00:00Z"), forgottenEnd, now),
                ),
            ),
        )
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun loweringTheTimeSpentTrimsTheForgottenTimerAndUndoBringsItBack() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithContentDescription("Edit").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription("Edit").onFirst().performClick()
        waitText("Edit task")
        // Repliée, la section dit déjà le total ; ouverte, le champ le montre avec la part chronométrée.
        waitText("7 h spent", substring = true)
        openSheetSection("Dates and time")
        waitText("including 7 h timed")
        onNode(hasSetTextAction() and hasText("420")).performScrollTo().performTextReplacement("120")
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.workSessions.single { it.id == 2L }.endedAt == Instant.parse("2026-11-10T10:00:00Z") }
        assertEquals(null, repository.snapshot.value.tasks.single().manualTimeSpentMinutes)
        // « Annuler » rend le chrono tel qu'il était.
        waitText("Time spent on “Quarterly report”: 7 h → 2 h")
        onNodeWithText("Undo").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.workSessions.single { it.id == 2L }.endedAt == forgottenEnd }
        assertTrue(repository.snapshot.value.workSessions.single { it.id == 1L }.endedAt == Instant.parse("2026-11-09T10:00:00Z"))
    }

    @Test
    fun anEmptiedFieldChangesNothing() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        val before = repository.snapshot.value.workSessions
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithContentDescription("Edit").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription("Edit").onFirst().performClick()
        openSheetSection("Dates and time")
        // Vider le champ (l'ancien geste « pas de temps saisi ») ne doit pas effacer le chronométré.
        onNode(hasSetTextAction() and hasText("420")).performScrollTo().performTextReplacement("")
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Edit task").fetchSemanticsNodes().isEmpty() }
        assertEquals(before, repository.snapshot.value.workSessions)
        assertEquals(null, repository.snapshot.value.tasks.single().manualTimeSpentMinutes)
    }
}
