package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.ComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.NoteStatus
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.data.TaskEdit
import kotlinx.datetime.LocalDate
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Notes, Semaine, Statistiques et guide des points de bout en bout
 * (docs/spec-v3/notes-capture.md, vue-semaine.md, statistiques.md,
 * vue-jour.md § Comprendre les valeurs) : base SQLite en mémoire, horloge
 * réglable (mardi 29 septembre 2026, 7 h UTC).
 */
@OptIn(ExperimentalTestApi::class)
class M4ScreensUiTest {
    private val clock = object : Clock {
        var now = Instant.parse("2026-09-29T07:00:00Z")
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun noteIsCapturedThenTurnedIntoATask() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Notes")
        onAllNodesWithText("Notes").onFirst().performClick()
        waitText("Nothing waiting.")

        onNode(hasSetTextAction() and hasText("Note")).performTextInput("Call the plumber\nabout the leak")
        onNodeWithText("Capture").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.notes.isNotEmpty() }

        waitText("→ Task")
        onNodeWithText("→ Task").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.tasks.isNotEmpty() }
        val task = repository.snapshot.value.tasks.single()
        assertEquals("Call the plumber", task.title)
        assertEquals("about the leak", task.description)
        val note = repository.snapshot.value.notes.single()
        assertEquals(NoteStatus.ARCHIVED, note.status)
        assertEquals(task.id, note.convertedTaskId)
        waitText("Processed / archived", substring = true)
    }

    @Test
    fun weekOpensAnotherDayAndComesBack() = runComposeUiTest {
        val services = services()
        runBlocking {
            val id = services.repository.createTask("Quarterly review")!!
            val due = LocalDate.parse("2026-10-01")
            services.repository.updateTask(id, TaskEdit("Quarterly review", priority = 1, points = 2, deadline = due, pinDay = due))
        }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Week")
        onAllNodesWithText("Week").onFirst().performClick()
        waitText("Week · from Monday, September 28, 2026")
        onNodeWithText("Quarterly review", substring = true).assertExists()

        // Troisième carte (mercredi) : la vue Jour de ce jour-là, avec le retour à aujourd'hui.
        waitText("See the day")
        onAllNodesWithText("See the day")[2].performScrollTo().performClick()
        waitText("Day · Wednesday, September 30, 2026")
        waitText("Back to today")
        onNodeWithText("Back to today").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Back to today").fetchSemanticsNodes().isEmpty() }

        // Semaine suivante puis précédente : le titre suit.
        onAllNodesWithText("Week").onFirst().performClick()
        waitText("Next week")
        onNodeWithText("Next week").performClick()
        waitText("Week · from Monday, October 5, 2026")
    }

    @Test
    fun statsShowWhatWasDoneAndTimed() = runComposeUiTest {
        val services = services()
        runBlocking { timedDone(services.repository, "Fix the invoice", points = 3, minutes = 40) }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Stats")
        onAllNodesWithText("Stats").onFirst().performClick()
        waitText("Weekly throughput")
        onNodeWithText("Estimation calibration").assertExists()
        onAllNodesWithText("n=1, not reliable", substring = true).onFirst().assertExists()
    }

    @Test
    fun calibratedPointsSuggestTheDurationAndFeedTheGuide() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        runBlocking {
            listOf("Alpha", "Beta", "Gamma").forEach { timedDone(repository, it, points = 3, minutes = 45) }
            val id = repository.createTask("Delta")!!
            repository.setPriority(id, 2)
            repository.setPoints(id, 1)
        }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Delta", substring = true)
        onAllNodesWithContentDescription("Edit").onFirst().performClick()
        waitText("Edit task")

        onNodeWithText("How to estimate points?").performScrollTo().performClick()
        waitText("for you ≈ 45 min (median of 3 task(s))")
        onNodeWithText("e.g. “Gamma” · e.g. “Beta”").assertExists()

        onNodeWithText("3 moderate").performScrollTo().performClick()
        onNodeWithText("Save").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.tasks.single { it.title == "Delta" }.estimatedMinutes == 45 }
    }

    /** Tâche aux points posés, chronométrée `minutes` puis terminée (l'horloge avance d'autant). */
    private suspend fun timedDone(repository: KairosRepository, title: String, points: Int, minutes: Int) {
        val id = repository.createTask(title)!!
        repository.setPriority(id, 2)
        repository.setPoints(id, points)
        repository.startTimer(id)
        clock.now += minutes.minutes
        repository.stopTimer()
        repository.toggleDone(id)
        assertEquals(TaskStatus.DONE, repository.snapshot.value.tasks.single { it.id == id }.status)
    }
}
