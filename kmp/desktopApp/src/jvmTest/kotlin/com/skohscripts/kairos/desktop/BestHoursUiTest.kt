package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
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
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

/** Carte Statistiques « Quand tu travailles chaque type » (docs/spec/planning-personnalise.md § Lot C, étape C1) : fuseau UTC, horloge figée. */
@OptIn(ExperimentalTestApi::class)
class BestHoursUiTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val savedLocale = Locale.getDefault()
    private val savedZone = TimeZone.getDefault()

    @BeforeTest
    fun setUp() {
        Locale.setDefault(Locale.ENGLISH)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @AfterTest
    fun restore() {
        Locale.setDefault(savedLocale)
        TimeZone.setDefault(savedZone)
    }

    private fun services(snapshot: KairosSnapshot): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { snapshot }, clock)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun waitText(test: androidx.compose.ui.test.ComposeUiTest, text: String) =
        test.waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { test.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun done(id: Long, type: String) = Task(id, "Task $id", status = TaskStatus.DONE, taskType = type, createdAt = Instant.parse("2026-09-01T08:00:00Z"), updatedAt = Instant.parse("2026-10-10T08:00:00Z"))

    @Test
    fun theCardShowsWhenEachTypeIsWorkedWithItsSampleAndPeak() = runComposeUiTest {
        val tasks = listOf(done(1, "Documentation"))
        // Cinq heures chronométrées, de 10 h à 11 h, cinq jours de suite.
        val sessions = (6..10).map { WorkSession(it.toLong(), 1, Instant.parse("2026-10-%02dT10:00:00Z".format(it)), Instant.parse("2026-10-%02dT11:00:00Z".format(it)), Instant.parse("2026-10-01T08:00:00Z")) }
        val services = services(KairosSnapshot(tasks = tasks, workSessions = sessions))
        setContent { KairosApp(Platform.DESKTOP, Destination.STATS, TeamDestination.BOARD) { services } }
        waitText(this, "When you work on each type")
        waitText(this, "Stands out at 10:00–12:00: 100% of its time")
        // Cinq sessions et cinq heures : fiable.
        waitText(this, "n=5 · 5 h")
    }

    @Test
    fun aTypeWithFewSessionsIsMarkedNotReliable() = runComposeUiTest {
        val sessions = (6..7).map { WorkSession(it.toLong(), 1, Instant.parse("2026-10-%02dT10:00:00Z".format(it)), Instant.parse("2026-10-%02dT11:00:00Z".format(it)), Instant.parse("2026-10-01T08:00:00Z")) }
        val services = services(KairosSnapshot(tasks = listOf(done(1, "Documentation")), workSessions = sessions))
        setContent { KairosApp(Platform.DESKTOP, Destination.STATS, TeamDestination.BOARD) { services } }
        waitText(this, "n=2, not reliable")
    }

    @Test
    fun withoutAnyTimedSessionTheCardSaysSo() = runComposeUiTest {
        val services = services(KairosSnapshot(tasks = listOf(done(1, "Documentation"))))
        setContent { KairosApp(Platform.DESKTOP, Destination.STATS, TeamDestination.BOARD) { services } }
        waitText(this, "No timed session of 5 minutes or more yet.")
    }
}
