package com.skohscripts.kairos.desktop

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.data.TaskEdit
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Espace Équipe, jalon E3 (docs/spec/equipe-backlog-suivi.md § Interface) : Backlog
 * (capture, qualification, assignation, sélection multiple), Suivi (lignes de membres,
 * réaffectation, avancement depuis la fiche, filtre « à surveiller »), tâche assignée à
 * « moi » dans la vue Jour. Base SQLite en mémoire, horloge figée (mardi 29 septembre
 * 2026), interface en anglais.
 */
@OptIn(ExperimentalTestApi::class)
class TeamBacklogBoardUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private val enabledTeam = KairosSnapshot(
        settings = Settings(team = TeamSettings(enabled = true, name = "Platform", identity = "id-1", lastSpace = TeamSettings.SPACE_TEAM)),
    )

    private fun services(seed: (suspend (KairosRepository) -> Unit)? = null): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(enabledTeam)
        seed?.invoke(repository)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitGone(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isEmpty() }

    private fun ComposeUiTest.count(text: String, substring: Boolean = false) =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private fun ComposeUiTest.openBacklog() {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Backlog").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Backlog").onFirst().performClick()
        waitText("To qualify", substring = true)
    }

    private fun ComposeUiTest.openBoard() {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Tracking").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Tracking").onFirst().performClick()
    }

    private fun ComposeUiTest.waitTask(repository: KairosRepository, id: Long, check: (com.skohscripts.kairos.core.model.Task) -> Boolean) =
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.tasks.firstOrNull { it.id == id }?.let(check) == true }

    /** Trois membres (« moi » d'abord) et les identifiants créés. */
    private class Crew(var me: Long = 0, var alex: Long = 0, var sam: Long = 0)

    private suspend fun crew(repository: KairosRepository, crew: Crew) {
        crew.me = repository.createMember("Claire", "", 100, 7.0, true)!!
        crew.alex = repository.createMember("Alex", "", 100, 7.0, false)!!
        crew.sam = repository.createMember("Sam", "", 100, 7.0, false)!!
    }

    private suspend fun ready(repository: KairosRepository, title: String, priority: Int = 1, points: Int = 3, deadline: LocalDate? = null): Long {
        val id = repository.createTeamTask(title)!!
        repository.updateTask(id, TaskEdit(title = title, priority = priority, points = points, deadline = deadline, pinDay = LocalDate(2026, 9, 29)))
        return id
    }

    @Test
    fun aCapturedTaskWaitsToBeQualifiedThenBecomesReadyAndNeverReachesTheDay() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openBacklog()
        waitText("To qualify (0)")

        onNodeWithText("New task").performTextInput("Write the spec")
        onNodeWithText("Add").performClick()
        waitText("To qualify (1)")
        val created = repository.snapshot.value.tasks.single()
        assertEquals(TaskSpace.TEAM, created.space)
        assertNull(created.assigneeId)
        assertTrue(repository.personalSnapshot.value.tasks.isEmpty())

        onNodeWithText("P1 Important").performClick()
        onNodeWithText("3 moderate").performClick()
        waitText("Ready (1)")
        waitText("To qualify (0)")

        // Jamais dans la vue Jour tant qu'elle n'est pas assignée à « moi ».
        onNodeWithContentDescription("Personal").performClick()
        waitText("Today")
        assertEquals(0, count("Write the spec", substring = true))
    }

    @Test
    fun assigningFromTheBacklogMovesTheTaskIntoTheMemberLaneAndBackAgain() = runComposeUiTest {
        val crew = Crew()
        var taskId = 0L
        val services = services {
            crew(it, crew)
            taskId = ready(it, "Ship the export")
        }
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openBacklog()
        waitText("Ready (1)")

        onNodeWithContentDescription("Task actions").performClick()
        onNodeWithText("Assign to…").performClick()
        // Le menu liste les membres actifs avec leur nombre d'en-cours.
        waitText("Alex")
        onNodeWithText("Alex").performClick()
        waitTask(repository, taskId) { it.assigneeId == crew.alex }
        waitText("Ready (0)")

        openBoard()
        waitText("Ship the export")
        // Dans la ligne d'Alex, colonne « À faire ».
        waitText("To do (1)")

        // Retour au backlog depuis le menu de la carte.
        onNodeWithContentDescription("Actions for “Ship the export”").performClick()
        onNodeWithText("Reassign to…").performClick()
        onNodeWithText("Put back in the backlog").performClick()
        waitTask(repository, taskId) { it.assigneeId == null }
        assertEquals(
            listOf("created", "qualified", "qualified", "assigned", "assigned"),
            repository.snapshot.value.teamEvents.filter { it.taskId == taskId }.sortedBy { it.id }.map { it.kind.code },
        )
    }

    @Test
    fun reassigningATaskInProgressAsksWhetherToKeepItInProgress() = runComposeUiTest {
        val crew = Crew()
        var taskId = 0L
        val services = services {
            crew(it, crew)
            taskId = ready(it, "Migrate the database")
            it.assign(listOf(taskId), crew.alex)
            it.startTeamTask(taskId)
        }
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openBoard()
        waitText("Migrate the database")

        onNodeWithContentDescription("Actions for “Migrate the database”").performClick()
        onNodeWithText("Reassign to…").performClick()
        // Le menu d'assignation : Alex est le titulaire actuel, Sam reprend la tâche.
        waitText("Sam")
        onAllNodesWithText("Sam").onLast().performClick()
        waitText("Keep the In progress status?")
        onNodeWithText("Yes").performClick()
        waitTask(repository, taskId) { it.assigneeId == crew.sam }
        assertNotNull(repository.snapshot.value.tasks.single { it.id == taskId }.startedOn)

        // Sans garder : la tâche repasse à « À faire ».
        onNodeWithContentDescription("Actions for “Migrate the database”").performClick()
        onNodeWithText("Reassign to…").performClick()
        waitText("Alex")
        onAllNodesWithText("Alex").onLast().performClick()
        waitText("Keep the In progress status?")
        onNodeWithText("No").performClick()
        waitTask(repository, taskId) { it.assigneeId == crew.alex }
        assertNull(repository.snapshot.value.tasks.single { it.id == taskId }.startedOn)
    }

    @Test
    fun settingTheProgressFromTheSheetShowsTheBarOnTheCardAndLogsTheHistoryInOrder() = runComposeUiTest {
        val crew = Crew()
        var taskId = 0L
        val services = services {
            crew(it, crew)
            taskId = ready(it, "Refactor billing")
            it.assign(listOf(taskId), crew.alex)
        }
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openBoard()
        waitText("Refactor billing")
        assertEquals(0, count("60%"))

        onNodeWithText("Refactor billing").performClick()
        waitText("Progress")
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        waitText("60%")
        onNodeWithText("Save").performScrollTo().performClick()
        waitTask(repository, taskId) { it.progressPercent == 60 && it.startedOn != null }
        // La carte : la barre et son « 60% », dans la colonne « En cours ».
        waitText("In progress (1)", substring = true)
        waitText("60%")

        // Historique : du plus récent au plus ancien.
        onNodeWithText("Refactor billing").performClick()
        waitText("History", substring = true)
        onNodeWithText("History", substring = true).performClick()
        waitText("Progress 0% → 60%", substring = true)
        val y = { text: String -> onAllNodesWithText(text, substring = true).fetchSemanticsNodes().first().positionInRoot.y }
        assertTrue(y("Progress 0% → 60%") < y("Started"))
        assertTrue(y("Started") < y("Assigned to Alex"))
        assertTrue(y("Assigned to Alex") < y("Created"))
    }

    @Test
    fun selectingTwoTasksAssignsThemAtOnce() = runComposeUiTest {
        val crew = Crew()
        val ids = mutableListOf<Long>()
        val services = services {
            crew(it, crew)
            ids += ready(it, "First job")
            ids += ready(it, "Second job")
            ids += ready(it, "Third job")
        }
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openBacklog()
        waitText("Ready (3)")

        // Largeur de bureau : une case à cocher par ligne.
        val boxes = onAllNodes(isToggleable())
        boxes[0].performClick()
        boxes[1].performClick()
        waitText("Selected: 2")
        onNodeWithText("Assign to…").performClick()
        waitText("Sam")
        onNodeWithText("Sam").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.tasks.count { it.assigneeId == crew.sam } == 2 }
        waitGone("Selected: 2", substring = true)
        waitText("Ready (1)")
    }

    @Test
    fun aTaskAssignedToMeIsMarkedInTheDayAndFinishingItFillsTheDoneColumn() = runComposeUiTest {
        val crew = Crew()
        var taskId = 0L
        val services = services {
            crew(it, crew)
            taskId = ready(it, "Review the roadmap", priority = 0)
            it.assign(listOf(taskId), crew.me)
        }
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Personal").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Personal").performClick()
        waitText("Review the roadmap", substring = true)
        // Icône « Groups » + le mot « Team », lu « team task ».
        assertTrue(count("Team") >= 1)
        assertTrue(onAllNodesWithContentDescription("team task").fetchSemanticsNodes().isNotEmpty())

        onAllNodesWithContentDescription("Mark as done").onFirst().performClick()
        waitTask(repository, taskId) { it.status == com.skohscripts.kairos.core.model.TaskStatus.DONE }
        assertEquals("self", repository.snapshot.value.teamEvents.last { it.taskId == taskId }.source.code)

        onNodeWithContentDescription("Team").performClick()
        waitText("Done, last 7 days (1)")
        waitText("Review the roadmap")
    }

    @Test
    fun theWatchFilterKeepsOnlyTasksCarryingASignal() = runComposeUiTest {
        val crew = Crew()
        val services = services {
            crew(it, crew)
            val late = ready(it, "Late task", deadline = LocalDate(2026, 9, 20))
            val fine = ready(it, "Fine task", deadline = LocalDate(2026, 10, 30))
            it.assign(listOf(late, fine), crew.alex)
        }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openBoard()
        waitText("Late task")
        waitText("Fine task")
        onNodeWithText("Only to watch").performClick()
        waitGone("Fine task")
        assertEquals(1, count("Late task"))
        // Le signal s'écrit : contour, icône et texte.
        assertTrue(count("Overdue") >= 1)
    }

    @Test
    fun aTeamBlockerOutsideMyDayIsNamedWithItsAssignee() = runComposeUiTest {
        val crew = Crew()
        val services = services {
            crew(it, crew)
            val colleague = ready(it, "Colleague job")
            val mine = ready(it, "My dependent job")
            it.assign(listOf(colleague), crew.alex)
            it.assign(listOf(mine), crew.me)
            it.updateTask(mine, TaskEdit(title = "My dependent job", priority = 1, points = 3, pinDay = LocalDate(2026, 9, 29), blockerIds = setOf(colleague)))
        }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Personal").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Personal").performClick()
        waitText("Blocked (1)")
        onNodeWithText("Blocked (1)").performScrollTo().performClick()
        waitText("waiting for: Colleague job (Alex)")
    }

    @Test
    fun aMemberSheetListsTheRecentActivityOfTheirTasks() = runComposeUiTest {
        val crew = Crew()
        val services = services {
            crew(it, crew)
            val id = ready(it, "Write the release notes")
            it.assign(listOf(id), crew.alex)
        }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Team").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Team").onFirst().performClick()
        waitText("Add a member")
        onNodeWithText("Alex").performClick()
        waitText("Activity")
        waitText("Write the release notes: Assigned to Alex", substring = true)
    }

    @Test
    fun theTeamCardOfTheSettingsOffersTheThreeTrackingThresholds() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Settings").onFirst().performClick()
        waitText("Appearance")
        onNodeWithText("No progress after (working days)").performScrollTo().assertExists()
        onNodeWithText("Bounced from (reassignments)").performScrollTo().assertExists()
        onNodeWithText("In-progress limit per member").performScrollTo().assertExists()
        assertEquals(1, count("Beyond it, the member’s row is flagged “too many in progress”. 0 = no limit."))
    }
}
