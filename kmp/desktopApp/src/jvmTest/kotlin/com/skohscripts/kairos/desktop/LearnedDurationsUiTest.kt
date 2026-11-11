package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.ui.settings.SettingsCategory
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Durées apprises à l'écran (docs/spec/durees-apprises.md) : pastille, puce de suggestion, réglage. */
@OptIn(ExperimentalTestApi::class)
class LearnedDurationsUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    /** Trois occurrences passées de 40 minutes chronométrées, et la suivante à faire aujourd'hui, sans estimation. */
    private fun services(): AppServices = runBlocking {
        val day = LocalDate(2026, 9, 29)
        val past = (1L..3L).map { Task(it, "Water the plants", status = TaskStatus.DONE, seriesId = 1, createdAt = now, updatedAt = now) }
        val next = Task(10, "Water the plants", priority = 2, fibonacciPoints = 3, scheduledDate = day, deadline = day, seriesId = 1, createdAt = now, updatedAt = now)
        val sessions = past.map { WorkSession(it.id, it.id, now - 120.minutes, now - 80.minutes, now) }
        val repository = KairosRepository.open(
            KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)),
            { KairosSnapshot(tasks = past + next, workSessions = sessions) },
            clock,
        )
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    @Test
    fun aLearnedDurationShowsOnTheRowAndTheEditDialogOffersIt() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Water the plants", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // La pastille « ~40 min » dit d'où vient la durée, pour le lecteur d'écran aussi.
        onAllNodesWithContentDescription("Estimated from your last 3 timed occurrences (median)").onFirst().assertExists()

        // Le dialogue propose la valeur sans la remplir ; un clic sur la puce l'inscrit.
        onAllNodesWithContentDescription("Edit").onFirst().performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Edit task").fetchSemanticsNodes().isNotEmpty() }
        openSheetSection("Dates and time")
        onNodeWithText("Use 40 min (your last 3)").performScrollTo().performClick()
        onNode(hasSetTextAction() and hasText("40")).assertExists()
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.tasks.single { it.id == 10L }.estimatedMinutes == 40 }
    }

    @Test
    fun switchingTheFeatureOffHidesTheBadgeAndTheSettingIsSaved() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.PLANNING)
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Learned durations").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Learn durations from past occurrences").performScrollTo().performClick()
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.settings.learning != null }
        assertFalse(repository.snapshot.value.settings.learning!!.enabled)
        assertEquals(5, repository.snapshot.value.settings.learning!!.history)
    }
}
