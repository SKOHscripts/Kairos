package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Durées réelles (docs/spec/durees-reelles.md, issue #271) : « Temps passé ? » à la coche, coches groupées, « Fait, suivante »,
 * revue du Bilan. Horloge figée au mardi 24 novembre 2026, 10 h (UTC).
 */
@OptIn(ExperimentalTestApi::class)
class SpentUiTest {
    private val now = Instant.parse("2026-11-24T10:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun todo(id: Long, title: String, priority: Int) =
        Task(id, title, priority = priority, fibonacciPoints = 3, estimatedMinutes = 30, createdAt = now - 3.days, updatedAt = now - 3.days)

    private fun done(id: Long, title: String, ago: Int) =
        Task(id, title, status = TaskStatus.DONE, priority = 1, fibonacciPoints = 3, createdAt = now - 10.days, updatedAt = now - ago.days)

    private fun services(tasks: List<Task>, settings: Settings = Settings(taskTypes = "")): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(KairosSnapshot(tasks = tasks, settings = settings))
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private val plan = listOf(todo(1, "Write the report", 0), todo(2, "Prepare the slides", 1), todo(3, "Answer the mail", 2))

    private fun ComposeUiTest.show(services: AppServices, destination: Destination = Destination.DAY) =
        setContent { Box(Modifier.width(1200.dp).height(1600.dp)) { KairosApp(Platform.DESKTOP, destination, TeamDestination.BOARD) { services } } }

    private fun ComposeUiTest.waitTag(tag: String) =
        waitUntil(conditionDescription = "repère « $tag »", timeoutMillis = 15_000) { onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.doneOnNowCard() {
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText("Done") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasText("Done") and hasClickAction()).performScrollTo().performClick()
    }

    private fun AppServices.task(id: Long) = repository.snapshot.value.tasks.single { it.id == id }

    @Test
    fun tickingATaskWithoutTimeAsksAndOneTapNotesIt() = runComposeUiTest {
        val services = services(plan)
        show(services)
        doneOnNowCard()
        waitTag("spent-question")
        onNodeWithTag("spent-30", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 15_000) { services.task(1).manualTimeSpentMinutes == 30 }
        assertEquals(TaskStatus.DONE, services.task(1).status)
        waitText("Time noted: 30 min")
    }

    @Test
    fun ticksLessThanAMinuteApartAreNotAsked() = runComposeUiTest {
        val services = services(plan)
        show(services)
        doneOnNowCard()
        waitTag("spent-question")
        // L'horloge est figée : la seconde coche suit la première de 0 s, c'est un lot.
        doneOnNowCard()
        waitUntil(timeoutMillis = 15_000) { services.task(2).status == TaskStatus.DONE }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag("spent-question", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        assertNull(services.task(1).manualTimeSpentMinutes)
        assertNull(services.task(2).manualTimeSpentMinutes)
    }

    @Test
    fun theSettingTurnsTheQuestionOff() = runComposeUiTest {
        val services = services(plan, Settings(taskTypes = "", askSpentOnDone = false))
        show(services)
        doneOnNowCard()
        waitUntil(timeoutMillis = 15_000) { services.task(1).status == TaskStatus.DONE }
        waitForIdle()
        assertTrue(onAllNodesWithTag("spent-question", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun doneNextChainsTheTimerToTheFollowingTask() = runComposeUiTest {
        val services = services(plan)
        runBlocking { services.repository.startTimer(1) }
        show(services)
        waitTag("done-next")
        onNodeWithTag("done-next").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { services.task(1).status == TaskStatus.DONE }
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.workSessions.any { it.taskId == 2L && it.endedAt == null } }
        // La tâche a son temps de chrono : aucune question.
        assertTrue(services.repository.snapshot.value.workSessions.any { it.taskId == 1L && it.endedAt != null })
        assertTrue(onAllNodesWithTag("spent-question", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        waitText("Timer started on “Prepare the slides”")
    }

    @Test
    fun theRecapReviewsTheDoneTasksWithoutATime() = runComposeUiTest {
        val services = services(plan + listOf(done(10, "Fix the printer", 1), done(11, "Call the bank", 2), done(12, "Too old", 9)))
        show(services, Destination.WEEK)
        waitText("2 done tasks without a time")
        assertTrue(onAllNodesWithText("“Too old”", substring = true).fetchSemanticsNodes().isEmpty())
        onNodeWithTag("review-10-45", useUnmergedTree = true).performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { services.task(10).manualTimeSpentMinutes == 45 }
        assertEquals(now - 1.days, services.task(10).updatedAt)
        waitText("1 done task without a time")
        onNodeWithTag("review-11-ignore", useUnmergedTree = true).performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { services.task(11).manualTimeSpentMinutes == 0 }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag("spent-review", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }
}
