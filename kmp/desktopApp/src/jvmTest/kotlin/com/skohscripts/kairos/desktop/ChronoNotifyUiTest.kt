package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.skohscripts.kairos.ui.app.NotifyHint
import com.skohscripts.kairos.ui.app.NotifyState
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Notifications du chrono (docs/spec/temps-reel-chrono.md, issue #219) : la veille passe par la voie du chrono même quand
 * le système programme des alarmes, sans titre clignotant ; les freins s'affichent sous « Alertes chrono actives » et leur
 * bouton appelle la plateforme.
 */
@OptIn(ExperimentalTestApi::class)
class ChronoNotifyUiTest {
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private var now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@ChronoNotifyUiTest.now
    }

    /** Comme Android : le système programme des alarmes, la voie du chrono poste tout de suite. */
    private val chrono = mutableListOf<String>()
    private val plain = mutableListOf<String>()
    private val opened = mutableListOf<NotifyHint>()
    private val titles = mutableListOf<String?>()
    private val notifier = object : ChronoNotifier {
        override val state = MutableStateFlow(NotifyState.ACTIVE)
        override val systemHandlesAlerts = true
        override val hints: StateFlow<Set<NotifyHint>> = MutableStateFlow(setOf(NotifyHint.EXACT_ALARMS, NotifyHint.BATTERY))
        override suspend fun notify(title: String, body: String, tag: String): Boolean {
            plain += tag
            return true
        }
        override suspend fun notifyChrono(title: String, body: String, tag: String): Boolean {
            chrono += tag
            return true
        }
        override fun openHint(hint: NotifyHint) {
            opened += hint
        }
        override fun setTitle(prefix: String?) {
            titles += prefix
        }
    }

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        val id = repository.createTask("Write the report")!!
        repository.setPriority(id, 1)
        repository.setPoints(id, 3)
        repository.setTaskType(id, "Development")
        repository.updateSettings(repository.snapshot.value.settings.copy(pomodoroFocusMinutes = 1))
        AppServices(repository, object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }, { _, _ -> }, clock = clock, notifier = notifier)
    }

    @Test
    fun theThresholdGoesThroughTheChronoPathAtOnce() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Start the timer").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Start the timer").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Stop the timer").fetchSemanticsNodes().isNotEmpty() }
        now += 61.seconds
        mainClock.advanceTimeBy(2_000)
        waitUntil(timeoutMillis = 5_000) { chrono.isNotEmpty() }
        // Une seule fois, par la voie du chrono, même si le système programme aussi des alarmes ; pas de titre clignotant.
        assertEquals(listOf("POMODORO"), chrono)
        assertTrue(plain.isEmpty())
        assertTrue(titles.none { it != null && it.startsWith("⚠") })
        onNodeWithText("Write the report · 1 min of continuous focus: time for a short break?").assertExists()
    }

    @Test
    fun theHintsShowUnderActiveAlertsAndOpenTheSystemSetting() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Allow exact alarms").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Alerts may arrive late while the app is closed.").assertExists()
        onNodeWithText("Battery saving can delay alerts.").assertExists()
        onNodeWithText("Allow exact alarms").performScrollTo().performClick()
        onNodeWithText("Battery settings").performScrollTo().performClick()
        assertEquals(listOf(NotifyHint.EXACT_ALARMS, NotifyHint.BATTERY), opened)
    }
}
