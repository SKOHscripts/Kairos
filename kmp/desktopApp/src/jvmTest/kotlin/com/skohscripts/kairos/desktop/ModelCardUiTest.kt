package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Carte « Ce que Kairos apprend » du bilan (docs/spec/bilan.md § Ce que Kairos apprend). Mercredi 11 novembre 2026. */
@OptIn(ExperimentalTestApi::class)
class ModelCardUiTest {
    private val now = Instant.parse("2026-11-11T12:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(learning: LearningSettings = LearningSettings()): AppServices = runBlocking {
        // Trente rapports d'environ 45 min, un tous les deux jours jusqu'à aujourd'hui.
        val done = (1..30).map { i ->
            val end = now - (2 * (30 - i)).days
            val start = end - (40 + (i % 3) * 5).minutes
            Task(i.toLong(), "Activity report $i", status = TaskStatus.DONE, taskType = "Writing", fibonacciPoints = 3, estimatedMinutes = 30, createdAt = start, updatedAt = end) to
                WorkSession(i.toLong(), i.toLong(), start, end, start)
        }
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(KairosSnapshot(tasks = done.map { it.first }, workSessions = done.map { it.second }, settings = Settings(learning = learning)))
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 20_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun theCardSaysWhatTheModelLearnedAndHowItConverged() = runComposeUiTest {
        val services = services()
        setContent { Box(Modifier.width(1200.dp).height(2400.dp)) { KairosApp(Platform.DESKTOP, Destination.WEEK, TeamDestination.BOARD) { services } } }
        // L'onglet s'appelle « Recap » ; la carte suit le bilan.
        waitText("Recap")
        waitText("What Kairos learns")
        // Trente tâches apprises, dont deux cette semaine (lundi et aujourd'hui).
        waitText("30 (+2)")
        // Le détail : la convergence, semaine par semaine.
        onAllNodesWithText("Details").onLast().performScrollTo().performClick()
        waitText("Convergence")
        waitText("Over the period: ×", substring = true)
    }

    @Test
    fun aModelSwitchedOffSaysWhereToTurnItOn() = runComposeUiTest {
        val services = services(LearningSettings(model = false))
        setContent { Box(Modifier.width(1200.dp).height(2400.dp)) { KairosApp(Platform.DESKTOP, Destination.WEEK, TeamDestination.BOARD) { services } } }
        waitText("Machine-learning estimates are off: Settings → Learned durations.")
    }
}
