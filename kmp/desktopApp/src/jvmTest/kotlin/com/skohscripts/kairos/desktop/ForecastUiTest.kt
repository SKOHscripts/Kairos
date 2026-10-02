package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.forecast.ForecastData
import com.skohscripts.kairos.core.team.forecast.ForecastModel
import com.skohscripts.kairos.core.team.forecast.ForecastRequest
import com.skohscripts.kairos.core.team.forecast.MonteCarlo
import com.skohscripts.kairos.core.team.forecast.ScenarioModification
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.data.TaskEdit
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.team.forecast.TeamUiState
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Espace Équipe, jalon E5 (docs/spec/equipe-simulation.md § Interface) : la destination Prévisions (simulation à graine
 * fixe, modèle par débit, résultat périmé, arrêt en cours de calcul), les scénarios (créer, comparer, appliquer,
 * dupliquer, supprimer) et les réglages de simulation. Base SQLite en mémoire, horloge figée (mardi 29 septembre 2026),
 * interface en anglais.
 */
@OptIn(ExperimentalTestApi::class)
class ForecastUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        var at: Instant = now
        override fun now() = at
    }
    private val zone = TimeZone.currentSystemDefault()
    private val today: LocalDate = now.toLocalDateTime(zone).date
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun settings(runs: Int = 500) = KairosSnapshot(
        settings = Settings(team = TeamSettings(enabled = true, name = "Platform", identity = "id-1", lastSpace = TeamSettings.SPACE_TEAM, simulationRuns = runs)),
    )

    /** Deux membres, quatre tâches à faire (deux avec échéance), et, avec [history], dix tâches faites sur dix semaines. */
    private fun services(runs: Int = 500, history: Boolean = true, ui: TeamUiState = TeamUiState(seed = { 42L }), extraTasks: Int = 0): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(settings(runs))
        val alex = repository.createMember("Alex", "", 100, 8.0, false)!!
        val sam = repository.createMember("Sam", "", 100, 8.0, false)!!
        suspend fun task(title: String, hours: Int, to: Long, deadline: LocalDate? = null): Long {
            val id = repository.createTeamTask(title)!!
            repository.updateTask(id, TaskEdit(title = title, priority = 1, estimatedMinutes = hours * 60, deadline = deadline, pinDay = today))
            repository.assign(listOf(id), to)
            return id
        }
        if (history) {
            for (week in 1..10) {
                clock.at = now - (7 * week + 2).days
                val id = repository.createTeamTask("Past $week")!!
                val spent = if (week % 2 == 0) 5 else 3
                repository.updateTask(id, TaskEdit(title = "Past $week", priority = 1, estimatedMinutes = 240, manualTimeSpentMinutes = spent * 60, pinDay = today))
                repository.assign(listOf(id), if (week % 2 == 0) alex else sam)
                repository.toggleDone(id)
            }
            clock.at = now
        }
        task("Billing API", 10, alex, today.plus(3))
        task("Export module", 8, alex)
        task("Workshop", 6, sam, today.plus(10))
        task("Mockups", 4, sam)
        repeat(extraTasks) { task("Extra $it", 2, if (it % 2 == 0) alex else sam) }
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock, teamUi = ui)
    }

    private fun LocalDate.plus(days: Int): LocalDate = kotlinx.datetime.LocalDate.fromEpochDays(toEpochDays() + days)

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.count(text: String, substring: Boolean = false) =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private fun ComposeUiTest.openForecast() {
        waitText("Tracking")
        onAllNodesWithText("Forecast").onFirst().performClick()
        waitText("Run the simulation")
    }

    private fun ComposeUiTest.run() {
        onNodeWithText("Run the simulation").performClick()
        waitText("Result")
    }

    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private fun text(date: LocalDate) = "${months[date.month.ordinal]} ${date.day}"

    @Test
    fun aSimulationWithAFixedSeedShowsTheDatesOfTheEngineAndItsHonestyNote() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openForecast()
        run()

        val snapshot = services.repository.snapshot.value
        val expected = MonteCarlo.prepare(ForecastRequest(snapshot, now, zone, ForecastData.build(snapshot, today, now, zone), 42L)).run()
        val finish = expected.finish!!
        assertNotNull(finish.p50)
        onNodeWithText("50% chance by ${text(finish.p50!!)}").assertExists()
        onNodeWithText("85% chance by ${text(finish.p85!!)}").assertExists()
        onNodeWithText("95% chance by ${text(finish.p95!!)}").assertExists()
        // Honnêteté : tirages, graine, source des données, date du calcul.
        onNodeWithText("500 draws · seed 42").assertExists()
        onNodeWithText("History: ${expected.source.samples} finished tasks over 12 weeks").assertExists()
        assertTrue(count("Computed on", substring = true) >= 1)
        assertEquals(expected, services.teamUi.forecast!!.result)
        // Les panneaux par tâche du modèle par effort.
        listOf("Deadlines", "Criticality", "Bottleneck", "Most likely outcome", "How many by…").forEach { assertEquals(1, count(it), it) }
    }

    @Test
    fun theThroughputModelIsDisabledWithItsReasonWithoutHistoryAndAvailableWithIt() = runComposeUiTest {
        val bare = services(history = false)
        setContent { KairosApp(Platform.DESKTOP) { bare } }
        openForecast()
        waitText("The by-throughput model is unavailable", substring = true)
        onNodeWithText("By throughput").assertIsNotEnabled()
        onNodeWithText("By effort").assertIsEnabled()
    }

    @Test
    fun theThroughputModelRunsWithHistoryAndHidesThePerTaskPanels() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openForecast()
        waitUntil(timeoutMillis = 15_000) {
            runCatching { onNodeWithText("By throughput").assertIsEnabled() }.isSuccess
        }
        assertEquals(0, count("The by-throughput model is unavailable", substring = true))
        onNodeWithText("By throughput").performClick()
        run()
        waitText("does not know the tasks one by one", substring = true)
        assertEquals(ForecastModel.THROUGHPUT, services.teamUi.forecast!!.result.model)
        listOf("Deadlines", "Criticality", "Bottleneck", "Most likely outcome").forEach { assertEquals(0, count(it), it) }
        assertEquals(1, count("How many by…"))
    }

    @Test
    fun aResultBecomesOutdatedWhenTheDataChangesWithoutBeingRecomputed() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openForecast()
        run()
        assertEquals(0, count("Outdated result", substring = true))
        val first = services.teamUi.forecast
        runBlocking { services.repository.createTeamTask("Late arrival") }
        waitText("Outdated result", substring = true)
        assertTrue(first === services.teamUi.forecast)
    }

    @Test
    fun stoppingWhileItComputesKeepsAPartialResultMarkedInterrupted() = runComposeUiTest {
        // Le calcul est tenu en suspens après la première tranche jusqu'au clic sur « Stop » : le test ne dépend
        // pas de la vitesse de la machine (un calcul trop rapide finirait avant le clic).
        // « Stop » est visible dès la préparation, où l'arrêt garde l'ancien résultat : on attend que le
        // calcul soit tenu après sa première tranche ([reached]) avant de cliquer.
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val ui = TeamUiState(seed = { 7L }, batchSize = 10, batchGate = { done ->
            if (done >= 10) { reached.complete(Unit); release.await() }
        })
        val services = services(runs = 50_000, ui = ui, extraTasks = 120)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openForecast()
        onNodeWithText("Run the simulation").performClick()
        waitUntil(timeoutMillis = 15_000) { reached.isCompleted }
        onNodeWithText("Stop").performClick()
        release.complete(Unit)
        waitText("Interrupted,", substring = true)
        val result = services.teamUi.forecast!!.result
        assertTrue(result.interrupted)
        assertEquals(10, result.runs)
        assertEquals(50_000, result.requestedRuns)
        assertEquals(1, count("of 50,000 draws.", substring = true))
        onNodeWithText("Run the simulation").assertExists()
    }

    @Test
    fun scenariosAreCreatedComparedAppliedDuplicatedAndDeleted() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openForecast()

        // Créer : un nom, un membre hypothétique, une absence (dates : le jour proposé par le sélecteur).
        onNodeWithText("New scenario").performScrollTo().performClick()
        waitText("Scenario name")
        onNodeWithText("Scenario name").performTextInput("Plan")
        onNodeWithText("Add a change").performClick()
        onNodeWithText("Add a hypothetical member").performClick()
        waitText("Hours per day")
        onNodeWithText("Name").performTextInput("Jo")
        onAllNodesWithText("OK").onLast().performClick()
        waitText("Add the hypothetical member “Jo”", substring = true)
        onNodeWithText("Add a change").performClick()
        onNodeWithText("Add an absence").performClick()
        waitText("First day of absence")
        onAllNodesWithText("Pick a date").onFirst().performClick()
        waitText("Cancel", substring = false)
        onAllNodesWithText("OK").onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { count("Pick a date") == 1 }
        onNodeWithText("Pick a date").performClick()
        onAllNodesWithText("OK").onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { count("Pick a date") == 0 }
        onAllNodesWithText("OK").onLast().performClick()
        waitText("Absence of", substring = true)
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.teamScenarios.size == 1 }
        val scenario = repository.snapshot.value.teamScenarios.single()
        assertEquals("Plan", scenario.name)
        assertEquals(2, scenario.modifications.size)
        assertTrue(scenario.modifications[0] is ScenarioModification.AddMember)
        assertTrue(scenario.modifications[1] is ScenarioModification.AddAbsence)

        // Comparer : la situation réelle et le scénario, mêmes tirages.
        waitText("2 changes")
        onNodeWithContentDescription("Compare “Plan”").performScrollTo().performClick()
        onNodeWithText("Compare (1)").performScrollTo().performClick()
        waitUntil(timeoutMillis = 30_000) { services.teamUi.comparison != null }
        waitText("Actual situation")
        val comparison = services.teamUi.comparison!!
        assertEquals(listOf(null, scenario.id), comparison.columns.map { it.scenarioId })
        assertEquals(setOf(42L), comparison.columns.map { it.result.seed }.toSet())
        assertEquals(setOf(500), comparison.columns.map { it.result.runs }.toSet())
        listOf("Finish at 50%", "Finish at 85%", "Load rate").forEach { assertTrue(count(it) >= 1, it) }

        // Appliquer : l'absence est créée, le membre hypothétique ne l'est pas.
        val membersBefore = repository.snapshot.value.members.size
        onNodeWithContentDescription("Actions for Plan").performScrollTo().performClick()
        onNodeWithText("Apply…").performClick()
        waitText("Will be applied (1)")
        assertEquals(1, count("Will not be applied: hypothetical (1)"))
        onNodeWithText("Apply").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.absences.size == 1 }
        assertEquals(membersBefore, repository.snapshot.value.members.size)
        waitText("Scenario applied.", substring = true)
        assertEquals(1, count("Applied: 1 · skipped: 0 · not applicable: 1.", substring = true))

        // Dupliquer, puis supprimer la copie après confirmation.
        onNodeWithContentDescription("Actions for Plan").performScrollTo().performClick()
        onNodeWithText("Duplicate").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.teamScenarios.size == 2 }
        assertTrue(repository.snapshot.value.teamScenarios.any { it.name == "Plan (copy)" })
        onNodeWithContentDescription("Actions for Plan (copy)").performScrollTo().performClick()
        onNodeWithText("Delete").performClick()
        waitText("Delete this scenario?")
        // Rien n'est supprimé avant la confirmation.
        assertEquals(2, repository.snapshot.value.teamScenarios.size)
        onNodeWithText("Delete").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.teamScenarios.size == 1 }
        assertEquals("Plan", repository.snapshot.value.teamScenarios.single().name)
    }

    @Test
    fun theTeamCardOfTheSettingsOffersTheFourForecastSettingsAndRefusesOutOfRangeValues() = runComposeUiTest {
        val services = services(history = false)
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Settings").onFirst().performClick()
        waitText("Appearance")
        listOf("Draws per simulation", "History considered (weeks)", "Reliable minimum sample", "Deadline at risk below (%)").forEach {
            onNodeWithText(it).performScrollTo().assertExists()
        }

        onNodeWithText("Draws per simulation").performTextClearance()
        onNodeWithText("Draws per simulation").performTextInput("100")
        onNodeWithText("Deadline at risk below (%)").performTextClearance()
        onNodeWithText("Deadline at risk below (%)").performTextInput("100")
        onNodeWithText("Save").performClick()
        waitText("At least 500.")
        waitText("At most 99.")
        assertEquals(500, repository.snapshot.value.settings.team!!.simulationRuns)

        onNodeWithText("Draws per simulation").performTextClearance()
        onNodeWithText("Draws per simulation").performTextInput("2000")
        onNodeWithText("History considered (weeks)").performTextClearance()
        onNodeWithText("History considered (weeks)").performTextInput("20")
        onNodeWithText("Reliable minimum sample").performTextClearance()
        onNodeWithText("Reliable minimum sample").performTextInput("5")
        onNodeWithText("Deadline at risk below (%)").performTextClearance()
        onNodeWithText("Deadline at risk below (%)").performTextInput("60")
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.settings.team!!.simulationRuns == 2000 }
        val team = repository.snapshot.value.settings.team!!
        assertEquals(listOf(20, 5, 60), listOf(team.historyWeeks, team.minSamples, team.deadlineRiskPercent))
    }
}
