package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.ChronoNotifier
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.app.NotifyState
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Garde-fou du chrono (docs/spec/temps-reel-chrono.md § Garde-fou, issue #220) : la session est close à l'instant limite,
 * que la limite passe application ouverte ou qu'on l'ouvre après, et le bandeau le dit ; à 0, rien ne s'arrête.
 */
@OptIn(ExperimentalTestApi::class)
class ChronoGuardUiTest {
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private val start = Instant.parse("2026-09-29T07:00:00Z")
    private var now = start
    private val clock = object : Clock {
        override fun now() = this@ChronoGuardUiTest.now
    }
    private val notified = mutableListOf<String>()
    private val notifier = object : ChronoNotifier {
        override val state = MutableStateFlow(NotifyState.ACTIVE)
        override suspend fun notify(title: String, body: String, tag: String): Boolean {
            notified += body
            return true
        }
    }

    /** Une tâche « Forgotten » dont le chrono tourne depuis [start], et le garde-fou à [limit] minutes (`null` = défaut). */
    private fun services(limit: Int?): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        val id = repository.createTask("Forgotten")!!
        repository.setPriority(id, 1)
        repository.setPoints(id, 3)
        repository.setTaskType(id, "Development")
        repository.updateSettings(repository.snapshot.value.settings.copy(timerAutoStopMinutes = limit))
        repository.startTimer(id)
        AppServices(repository, object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }, { _, _ -> }, clock = clock, notifier = notifier)
    }

    private val banner = "Forgotten · timer stopped automatically after 4 h (limit adjustable in Timer and statistics)."

    @Test
    fun theLimitPassingWhileOpenClosesTheSessionAtTheLimit() = runComposeUiTest {
        val services = services(null)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Stop the timer").fetchSemanticsNodes().isNotEmpty() }
        now = start + 4.hours + 5.seconds
        mainClock.advanceTimeBy(2_000)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(banner).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(start + 4.hours, services.repository.snapshot.value.workSessions.single().endedAt)
        assertEquals(listOf("timer stopped automatically after 4 h (limit adjustable in Timer and statistics)."), notified)
    }

    @Test
    fun openingAfterTheLimitCatchesUpAtTheLimit() = runComposeUiTest {
        // Le lendemain : la session est close à début + 4 h, pas à l'ouverture.
        val services = services(null)
        now = start + 20.hours
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(banner).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(start + 4.hours, services.repository.snapshot.value.workSessions.single().endedAt)
    }

    @Test
    fun zeroNeverStops() = runComposeUiTest {
        val services = services(0)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Stop the timer").fetchSemanticsNodes().isNotEmpty() }
        now = start + 30.hours
        mainClock.advanceTimeBy(3_000)
        waitForIdle()
        assertNull(services.repository.snapshot.value.workSessions.single().endedAt)
        assertEquals(0, onAllNodesWithText("timer stopped automatically", substring = true).fetchSemanticsNodes().size)
        // Le réglage par défaut reste nul tant qu'on n'y touche pas ; ici il vaut 0.
        assertEquals(0, services.repository.snapshot.value.settings.timerAutoStopMinutes)
        // Les alertes ordinaires (pause, chrono oublié) sonnent, jamais l'arrêt automatique.
        assertEquals(0, notified.count { "stopped automatically" in it })
    }
}
