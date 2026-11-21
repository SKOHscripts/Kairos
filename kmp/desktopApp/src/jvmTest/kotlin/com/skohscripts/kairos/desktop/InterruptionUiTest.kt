package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.InterruptionSource
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
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Dérangements (docs/spec/derangements.md § Critères de succès) : un geste (bouton ou `D`, puis une durée), chrono mis en pause
 * après coup, « Annuler », carte des Stats. Horloge figée au mardi 24 novembre 2026, 10 h (UTC).
 */
@OptIn(ExperimentalTestApi::class)
class InterruptionUiTest {
    private var now = Instant.parse("2026-11-24T10:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(
            KairosSnapshot(
                tasks = listOf(Task(1, "Write the report", priority = 0, fibonacciPoints = 3, estimatedMinutes = 120, createdAt = now - 3.days, updatedAt = now - 3.days)),
                settings = Settings(taskTypes = ""),
            ),
        )
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.show(services: AppServices, destination: Destination = Destination.DAY) =
        setContent { Box(Modifier.width(1200.dp).height(1600.dp)) { KairosApp(Platform.DESKTOP, destination, TeamDestination.BOARD) { services } } }

    private fun ComposeUiTest.waitTag(tag: String) =
        waitUntil(conditionDescription = "repère « $tag »", timeoutMillis = 15_000) { onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun oneGesturePausesTheTimerAfterTheFactAndUndoRestoresIt() = runComposeUiTest {
        val services = services()
        runBlocking { services.repository.startTimer(1) }
        now += 60.minutes
        show(services)
        waitTag("interrupted")
        onNodeWithTag("interrupted").performScrollTo().performClick()
        waitTag("interruption-dialog")
        onNodeWithTag("interruption-source-call", useUnmergedTree = true).performClick()
        onNodeWithTag("interruption-15", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.interruptions.isNotEmpty() }
        val i = services.repository.snapshot.value.interruptions.single()
        assertEquals(15, i.minutes)
        assertEquals(InterruptionSource.CALL, i.source)
        val sessions = services.repository.snapshot.value.workSessions
        assertEquals(now - 15.minutes, sessions.single { it.endedAt != null }.endedAt)
        assertEquals(now, sessions.single { it.endedAt == null }.startedAt)
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Interrupted 15 min: noted").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Undo")[0].performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.interruptions.isEmpty() }
        assertEquals(1, services.repository.snapshot.value.workSessions.size)
    }

    @Test
    fun theDKeyOpensThePanel() = runComposeUiTest {
        val services = services()
        show(services)
        waitTag("interrupted")
        onRoot().performKeyInput { pressKey(Key.D) }
        waitTag("interruption-dialog")
        onNodeWithTag("interruption-5", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.interruptions.singleOrNull()?.minutes == 5 }
    }

    @Test
    fun theStatisticsShowAndCorrectThem() = runComposeUiTest {
        val services = services()
        runBlocking {
            services.repository.reportInterruption(30, InterruptionSource.MESSAGE)
            now += 60.minutes
            services.repository.reportInterruption(10, null)
        }
        show(services, Destination.STATS)
        waitTag("interruption-stats")
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Recent interruptions (2)").fetchSemanticsNodes().isNotEmpty() }
        val first = services.repository.snapshot.value.interruptions.first().id
        onNodeWithTag("interruption-delete-$first").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.interruptions.size == 1 }
    }
}
