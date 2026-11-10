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
import com.skohscripts.kairos.core.model.WorkSession
import kotlin.time.Duration.Companion.minutes

/**
 * La fin probable d'une tâche perso dans « Pourquoi à cette place ? » et la semaine réaliste de la vue Semaine
 * (docs/spec/previsions-taches.md § Étape C). Horloge figée au lundi 16 novembre 2026.
 */
@OptIn(ExperimentalTestApi::class)
class SoloForecastUiTest {
    private val now = Instant.parse("2026-11-16T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    /** Huit semaines à 2 h chronométrées par jour ouvré (midi UTC : le même jour dans tous les fuseaux), [weeks] dernières seulement. */
    private fun services(weeks: Int = 8): AppServices = runBlocking {
        val sessions = ArrayList<WorkSession>()
        var id = 1L
        for (week in 8 - weeks until 8) for (weekday in 0 until 5) {
            val start = Instant.parse("2026-09-21T12:00:00Z") + ((week * 7 + weekday) * 24 * 60).minutes
            sessions += WorkSession(id++, 1000L + (week * 5 + weekday) % 8, start, start + 120.minutes, start)
        }
        val done = (0 until 8).map { Task(1000L + it, "Done $it", status = TaskStatus.DONE, estimatedMinutes = 600, createdAt = now, updatedAt = now) }
        val open = (1L..3L).map { Task(it, "Open $it", priority = 1, fibonacciPoints = 3, taskType = "Writing", estimatedMinutes = 90, deadline = LocalDate(2026, 11, 20), createdAt = now, updatedAt = now) }
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(KairosSnapshot(tasks = done + open, workSessions = sessions, settings = Settings(taskTypes = "Writing,Admin")))
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
    fun whyShowsTheLikelyFinishWithItsRangeAndItsSource() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Open 1", substring = true)
        onAllNodesWithContentDescription("Why this position?", substring = true).onFirst().performClick()
        // Jamais une date sans sa condition ni sa source.
        waitText("Likely finish:", substring = true)
        waitText("if nothing more urgent comes up", substring = true)
        waitText("From your last 8 weeks:", substring = true)
        waitText("Deadline met", substring = true)
    }

    @Test
    fun withTooLittleTrackedTimeNothingIsShown() = runComposeUiTest {
        val services = services(weeks = 3)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Open 1", substring = true)
        onAllNodesWithContentDescription("Why this position?", substring = true).onFirst().performClick()
        waitText("Why this position?")
        assertTrue(onAllNodesWithText("Likely finish:", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aTaskThatOverrunsSaysWhatComparableTasksTook() = runComposeUiTest {
        // Même titre : 30, 70, 80 et 120 min. Estimée 45 min, chronométrée depuis 50 min : restent 70, 80 et 120.
        val done = listOf(30, 70, 80, 120).mapIndexed { i, m -> Task(10L + i, "Weekly report", status = TaskStatus.DONE, manualTimeSpentMinutes = m, createdAt = now, updatedAt = now) }
        val running = Task(1, "Weekly report", priority = 1, fibonacciPoints = 3, taskType = "Writing", estimatedMinutes = 45, createdAt = now, updatedAt = now)
        val started = now - 50.minutes
        val repository = runBlocking {
            KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock).also {
                it.replaceAll(KairosSnapshot(tasks = done + running, workSessions = listOf(WorkSession(1, 1, started, null, started)), settings = Settings(taskTypes = "Writing,Admin")))
            }
        }
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        val services = AppServices(repository, files, { _, _ -> }, clock = clock)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Your 3 comparable tasks that ran longer than 50 min took 1 h 20", substring = true)
    }

    @Test
    fun theWeekSaysWhatItCanReallyHold() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.WEEK, TeamDestination.BOARD) { services } }
        // 10 h chaque semaine ; trois tâches de 1 h 30 à échéance vendredi.
        waitText("Realistic:", substring = true)
        waitText("due or scheduled this week", substring = true)
    }
}
