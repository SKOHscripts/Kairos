package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.Interruption
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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * « Ta journée » (docs/spec/ta-journee.md § Critères de succès) : la carte en tête, repliée et dépliée, la réservation d'un créneau
 * de deep work et « Annuler ». Horloge figée au mardi 24 novembre 2026, 9 h, dans le fuseau de la machine.
 */
@OptIn(ExperimentalTestApi::class)
class DayBriefUiTest {
    private val tz = TimeZone.currentSystemDefault()
    private val day = LocalDate(2026, 11, 24)
    private val now = day.atTime(9, 0).toInstant(tz)
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    /** Une tâche de 5 h et un historique dérangé la moitié du temps : 30 min de dépassement, une heure de deep work proposée. */
    private fun services(): AppServices = runBlocking {
        var id = 0L
        val history = (0 until 6).flatMap { k ->
            val d = LocalDate(2026, 11, 20).minus(DatePeriod(days = k))
            (9..17).map { h -> val at = d.atTime(h, 30).toInstant(tz); Interruption(++id, at, 30, createdAt = at) }
        }
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(
            KairosSnapshot(
                tasks = listOf(Task(1, "Write the report", priority = 1, fibonacciPoints = 3, estimatedMinutes = 300, createdAt = Instant.parse("2026-11-01T00:00:00Z"), updatedAt = Instant.parse("2026-11-01T00:00:00Z"))),
                interruptions = history,
                settings = Settings(taskTypes = "", holidaysFr = false),
            ),
        )
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitTag(tag: String) =
        waitUntil(conditionDescription = "repère « $tag »", timeoutMillis = 15_000) { onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = text, timeoutMillis = 15_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun theCardPresentsTheDayFoldsAndBooksDeepWork() = runComposeUiTest {
        val services = services()
        setContent { Box(Modifier.width(1200.dp).height(1600.dp)) { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } } }
        waitTag("day-brief")
        waitText("Your day")
        waitText("1 task to do, estimated at 5 h.")
        waitText("Without a dedicated deep-work slot, you will probably run over by about 30 min.")
        // Repliée : le verdict seul, retenu dans les réglages de l'appareil.
        onNodeWithTag("day-brief-fold").performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.settings.dayBriefFolded == true }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("1 task to do, estimated at 5 h.").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("day-brief-fold").performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.settings.dayBriefFolded == null }
        // Réserver : un créneau de deep work d'une heure à 9 h, puis « Annuler » le retire.
        waitTag("day-brief-reserve")
        onNodeWithTag("day-brief-reserve").performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.timeBlocks.any { it.kind == BlockKind.DEEPWORK } }
        val block = services.repository.snapshot.value.timeBlocks.single()
        assertEquals(day.atTime(9, 0), block.start)
        assertEquals(day.atTime(10, 0), block.end)
        waitText("Deep work booked", substring = true)
        onAllNodesWithText("Undo")[0].performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.timeBlocks.isEmpty() }
    }
}
