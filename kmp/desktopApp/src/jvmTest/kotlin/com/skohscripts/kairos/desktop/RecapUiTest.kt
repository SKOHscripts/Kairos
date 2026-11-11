package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
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
import kotlinx.datetime.LocalDateTime
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Bilan de la semaine et du mois (docs/spec/bilan.md, issue #233) : les chiffres, le détail, « Copy the recap », une
 * semaine passée ouverte d'office, le retour à aujourd'hui, la carte du mois, l'en-tête à 360 dp. Horloge figée au jeudi
 * 5 novembre 2026.
 */
@OptIn(ExperimentalTestApi::class)
class RecapUiTest {
    private val now = Instant.parse("2026-11-05T10:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()
    private var copied: String? = null
    private val clipboard = object : ClipboardManager {
        override fun getText(): AnnotatedString? = copied?.let(::AnnotatedString)
        override fun setText(annotatedString: AnnotatedString) {
            copied = annotatedString.text
        }
    }

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun at(text: String) = Instant.parse(text)

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(
            KairosSnapshot(
                tasks = listOf(
                    Task(1, "Quarterly report", projectTag = "Finance", status = TaskStatus.DONE, fibonacciPoints = 5, createdAt = now, updatedAt = at("2026-11-03T12:00:00Z")),
                    Task(2, "Migration", deadline = LocalDate(2026, 11, 4), createdAt = now, updatedAt = now),
                    Task(3, "Old audit", status = TaskStatus.DONE, createdAt = now, updatedAt = at("2026-10-28T12:00:00Z")),
                ),
                workSessions = listOf(WorkSession(1, 1, at("2026-11-03T12:00:00Z"), at("2026-11-03T13:30:00Z"), now)),
                timeBlocks = listOf(TimeBlock(1, "Team sync", LocalDateTime(2026, 11, 4, 9, 0), LocalDateTime(2026, 11, 4, 9, 30), createdAt = now)),
            ),
        )
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.openWeek(width: Int) {
        setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                Box(Modifier.width(width.dp).height(1600.dp)) { KairosApp(Platform.DESKTOP, Destination.WEEK, TeamDestination.BOARD) { services() } }
            }
        }
        waitText("Week recap")
    }

    @Test
    fun theWeekRecapShowsFiguresDetailsAndCopies() = runComposeUiTest {
        openWeek(1200)
        onNodeWithText("1 · 5 pts").assertExists()
        onNodeWithText("1 h 30").assertExists()
        // Semaine courante : le détail est replié.
        onNodeWithText("Done (1)").assertDoesNotExist()
        onAllNodesWithText("Details").onFirst().performClick()
        waitText("Done (1)")
        onNodeWithText("Meetings and time slots (1)").assertExists()
        onNodeWithText("Left (1)").assertExists()
        onNodeWithText("Copy the recap").performClick()
        waitUntil(timeoutMillis = 15_000) { copied != null }
        val text = copied!!
        assertTrue(text.startsWith("Recap · Week of "), text)
        assertTrue("Done (1)\n- Quarterly report [Finance] · 1 h 30\n" in text, text)
        assertTrue("Meetings and time slots (1)\n- Team sync · 30 min\n" in text, text)
        assertTrue("Left (1)\n- Migration · due " in text, text)
        assertTrue(text.endsWith("Time tracked: 1 h 30"), text)
        waitText("Recap copied.")
    }

    @Test
    fun aPastWeekOpensItsDetailsAndComesBackToToday() = runComposeUiTest {
        openWeek(1200)
        onNodeWithContentDescription("Previous week").performClick()
        // Semaine passée : le bilan est l'objet, le détail s'ouvre seul.
        waitText("Done (1)")
        onAllNodesWithText("Old audit").onFirst().assertExists()
        onNodeWithText("Back to today").performClick()
        waitText("1 · 5 pts")
        assertTrue(onAllNodesWithText("Back to today").fetchSemanticsNodes().isEmpty())
        // Le mois a sa carte.
        onAllNodesWithText("Month").onFirst().performClick()
        waitText("Month recap")
        onNodeWithText("Month recap").performScrollTo().assertExists()
    }

    @Test
    fun theHeaderFitsAt360dp() = runComposeUiTest {
        openWeek(360)
        onAllNodesWithContentDescription("Next week").fetchSemanticsNodes().forEach { node ->
            assertTrue(node.boundsInRoot.right <= 360f * density.density + 0.5f, "bouton hors de la fenêtre de 360 dp : ${node.boundsInRoot}")
        }
        onNodeWithContentDescription("Next week").assertExists()
    }
}
