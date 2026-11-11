package com.skohscripts.kairos.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import com.skohscripts.kairos.ui.guide.GuideSection
import com.skohscripts.kairos.ui.guide.GuideSpotState
import com.skohscripts.kairos.ui.guide.LocalGuideSpot
import com.skohscripts.kairos.ui.stats.StatsScreen
import com.skohscripts.kairos.ui.theme.KairosTheme
import androidx.compose.material3.Surface
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
import kotlin.test.assertTrue
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
        // Une tâche sans estimation : pas d'efficacité (v2, issue #105).
        waitText(this, "Efficiency: not enough finished, estimated tasks yet (0 of 5)")
    }

    @Test
    fun theCardSaysWhenATypeFinishesFastest() = runComposeUiTest {
        // Six tâches à 9 h au temps prévu, six à 14 h en 40 min pour 60 prévues.
        val start = Instant.parse("2026-10-01T08:00:00Z")
        val tasks = (1L..12L).map { done(it, "Dev").copy(estimatedMinutes = 60) }
        val sessions = (1L..12L).map { id ->
            val day = 2 + (id % 6).toInt()
            val (from, to) = if (id <= 6) "09:00" to "10:00" else "14:00" to "14:40"
            WorkSession(id, id, Instant.parse("2026-10-%02dT%s:00Z".format(day, from)), Instant.parse("2026-10-%02dT%s:00Z".format(day, to)), start)
        }
        val services = services(KairosSnapshot(tasks = tasks, workSessions = sessions))
        setContent { KairosApp(Platform.DESKTOP, Destination.STATS, TeamDestination.BOARD) { services } }
        waitText(this, "Fastest at 14:00–16:00: 22% quicker than usual (12 estimated tasks)")
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
        // Rien de chronométré : les cartes de temps laissent place à une seule (docs/spec/statistiques.md § Ordre et cartes vides).
        val services = services(KairosSnapshot(tasks = listOf(done(1, "Documentation"))))
        // Le guide « Quand tu travailles chaque type » (issue #95) vise cette carte unique : il la trouve et consomme sa demande.
        val spot = GuideSpotState().apply { pending = GuideSection.STATS_HOURS }
        setContent {
            // L'écran seul : le shell fournirait son propre état de guide.
            CompositionLocalProvider(LocalGuideSpot provides spot) { KairosTheme { Surface { StatsScreen(services) } } }
        }
        waitText(this, "Nothing timed yet")
        assertTrue(onAllNodesWithText("When you work on each type").fetchSemanticsNodes().isEmpty())
        waitUntil(timeoutMillis = 15_000) { spot.pending == null && spot.shown == GuideSection.STATS_HOURS }
    }

    @Test
    fun aFewMinutesTimedKeepTheCardsAndTheHoursSayWhyTheyAreEmpty() = runComposeUiTest {
        // Trois minutes : du temps, mais aucune session de 5 minutes pour le profil horaire.
        val session = WorkSession(1, 1, Instant.parse("2026-10-09T10:00:00Z"), Instant.parse("2026-10-09T10:03:00Z"), Instant.parse("2026-10-01T08:00:00Z"))
        val services = services(KairosSnapshot(tasks = listOf(done(1, "Documentation")), workSessions = listOf(session)))
        setContent { KairosApp(Platform.DESKTOP, Destination.STATS, TeamDestination.BOARD) { services } }
        waitText(this, "No timed session of 5 minutes or more yet.")
        assertTrue(onAllNodesWithText("Nothing timed yet").fetchSemanticsNodes().isEmpty())
    }
}
