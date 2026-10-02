package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import com.skohscripts.kairos.core.team.AssignmentSuggestion
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.data.TaskEdit
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Espace Équipe, jalon E4 (docs/spec/equipe-charge.md § Interface) : la destination Équipe avec la charge
 * (tuiles, barres, panneaux, horizon), la fiche membre avec sa charge, la suggestion de répartition, la charge
 * dans le menu « Assigner à… », les réglages de charge. Base SQLite en mémoire, horloge figée (mardi 29 septembre
 * 2026), interface en anglais. Aucun membre « moi » : la capacité du jour est entière, donc les chiffres sont
 * exacts (8 h × 0,8 de focus = 6,4 h par jour ouvré ; 4 jours ouvrés sur l'horizon d'une semaine = 25,6 h).
 */
@OptIn(ExperimentalTestApi::class)
class TeamLoadUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val today = LocalDate(2026, 9, 29)
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private val enabledTeam = KairosSnapshot(
        settings = Settings(team = TeamSettings(enabled = true, name = "Platform", identity = "id-1", lastSpace = TeamSettings.SPACE_TEAM, horizonWeeks = 1)),
    )

    private class Crew(var claire: Long = 0, var alex: Long = 0, var sam: Long = 0)

    private fun services(seed: suspend (KairosRepository) -> Unit): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(enabledTeam)
        seed(repository)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private suspend fun crew(repository: KairosRepository, crew: Crew) {
        crew.claire = repository.createMember("Claire", "", 100, 8.0, false)!!
        crew.alex = repository.createMember("Alex", "", 100, 8.0, false)!!
        crew.sam = repository.createMember("Sam", "", 100, 8.0, false)!!
    }

    /** Une tâche d'équipe qualifiée de [hours] h (durée estimée), assignée à [to] si fourni. */
    private suspend fun task(
        repository: KairosRepository, title: String, hours: Int, to: Long? = null, deadline: LocalDate? = null, type: String = "Dev",
    ): Long {
        val id = repository.createTeamTask(title)!!
        repository.updateTask(
            id,
            TaskEdit(title = title, priority = 1, points = 3, estimatedMinutes = hours * 60, taskType = type, deadline = deadline, pinDay = today),
        )
        if (to != null) repository.assign(listOf(id), to)
        return id
    }

    /** Alex : trois tâches de 10 h dont une due aujourd'hui (30 h pour 25,6 h : 117 %). Claire : 5 h. Sam : une tâche non estimée. Backlog : 6 h et une tâche non estimée. */
    private suspend fun loadedTeam(repository: KairosRepository, crew: Crew, ids: MutableMap<String, Long> = mutableMapOf()) {
        crew(repository, crew)
        ids["due"] = task(repository, "Due today", 10, crew.alex, deadline = today)
        ids["b"] = task(repository, "Second", 10, crew.alex)
        ids["c"] = task(repository, "Third", 10, crew.alex)
        ids["claire"] = task(repository, "Claire work", 5, crew.claire, type = "Review")
        val bare = repository.createTeamTask("No estimate")!!
        repository.assign(listOf(bare), crew.sam)
        ids["backlog"] = task(repository, "Backlog item", 6)
        repository.createTeamTask("Backlog bare")
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitGone(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isEmpty() }

    private fun ComposeUiTest.count(text: String, substring: Boolean = false) =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private fun ComposeUiTest.openMembers() {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Team").fetchSemanticsNodes().isNotEmpty() }
        waitText("Tracking")
        onAllNodesWithText("Team").onFirst().performClick()
        waitText("Add a member")
    }

    private fun ComposeUiTest.openBacklog() {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Backlog").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Backlog").onFirst().performClick()
        waitText("To qualify", substring = true)
    }

    @Test
    fun theTeamScreenShowsTheLoadOfEveryoneWithTheOverloadedMemberFlagged() = runComposeUiTest {
        val services = services { loadedTeam(it, Crew()) }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openMembers()

        // L'horizon des réglages (1 semaine) est choisi au départ ; les quatre chiffres clés arrivent hors composition.
        waitText("Load horizon")
        waitText("30 h / 25.6 h · 117%")
        // Tuiles : capacité 3 × 25,6 h, focus 80 %, charge 35 h, taux 46 %, backlog 6 h.
        assertEquals(1, count("76.8 h"))
        assertEquals(1, count("Team capacity · focus 80%"))
        assertEquals(1, count("35 h"))
        assertEquals(1, count("46%"))
        assertEquals(1, count("Load rate"))
        assertEquals(1, count("Unassigned backlog", substring = true))
        assertEquals(1, count("6 h"))
        // Ce que les chiffres ne comptent pas : une tâche non estimée chez Sam, une au backlog.
        assertEquals(3, count("+ 1 unestimated"))
        // La carte d'Alex : en danger (la tâche due aujourd'hui finit demain) ; les autres cartes sans alerte de charge.
        assertEquals(1, count("1 deadline at risk"))
        waitText("0 in progress · 3 to do")
        // Par catégorie : Dev (30 + 6 h) avant Review (5 h), « No category » (les non estimées) en dernier.
        waitText("By category")
        waitText("36 h · 47%")
        waitText("5 h · 7%")
        // Répartition : l'écart maximal.
        waitText("Distribution")
        waitText("Max gap: Alex 117%, Sam 0%")
        waitText("Plan without uncertainty: see Forecast for the probabilities.")
    }

    @Test
    fun theHorizonSelectorRecomputesTheLoadAndKeepsTheOldFiguresMeanwhile() = runComposeUiTest {
        val services = services { loadedTeam(it, Crew()) }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openMembers()
        waitText("76.8 h")

        // 2 semaines : 4 + 5 jours ouvrés = 9 × 6,4 h par membre.
        onNodeWithText("2 wk").performClick()
        waitText("172.8 h")
        assertEquals(0, count("76.8 h"))
        waitText("30 h / 57.6 h · 52%")
    }

    @Test
    fun theMemberSheetShowsTheCapacityTheWeeklyLoadAndTheTaskInDanger() = runComposeUiTest {
        val services = services { loadedTeam(it, Crew()) }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openMembers()
        waitText("30 h / 25.6 h · 117%")

        onAllNodesWithText("Alex").onFirst().performClick()
        waitText("Capacity: 25.6 h over 1 week")
        waitText("By week")
        waitText("Week of Sep 29")
        // La tâche due aujourd'hui commence en premier (en retard d'abord) et finit demain : en danger.
        waitText("Open tasks, in plan order")
        waitText("10 h · Planned end Sep 30, due Sep 29")
        assertEquals(3, count("10 h · Planned end", substring = true))
        assertTrue(count("Planned end Oct", substring = true) >= 1, "la troisième tâche déborde sur la semaine suivante")
    }

    @Test
    fun theAssignMenuShowsTheLoadOfEachMember() = runComposeUiTest {
        val services = services { loadedTeam(it, Crew()) }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openBacklog()
        waitText("Backlog bare")
        onAllNodesWithContentDescription("Task actions").onFirst().performClick()
        onNodeWithText("Assign to…").performClick()
        // Alex : 117 % (surchargé, contour + icône) ; Claire : 20 % ; Sam : 0 %.
        waitText("0 in progress · load 117%")
        waitText("Overloaded (117%)")
        assertEquals(1, count("0 in progress · load 20%"))
        assertEquals(1, count("0 in progress · load 0%"))
    }

    @Test
    fun applyingTheSuggestionAssignsOnlyTheCheckedLinesWithTheChosenMember() = runComposeUiTest {
        val crew = Crew()
        val ids = mutableMapOf<String, Long>()
        val services = services { r ->
            crew(r, crew)
            ids["one"] = task(r, "Line one", 4)
            ids["two"] = task(r, "Line two", 4)
            ids["three"] = task(r, "Line three", 4)
            r.createTeamTask("Not ready")
        }
        val repository = services.repository
        // La proposition attendue, pour choisir un autre membre que celui proposé à la ligne deux.
        val expected = AssignmentSuggestion.suggest(repository.snapshot.value, now, TimeZone.currentSystemDefault())
        assertEquals(3, expected.suggestions.size)
        val suggestedTwo = expected.suggestions.first { it.taskId == ids.getValue("two") }.memberId
        val names = repository.snapshot.value.members.associate { it.id to it.name }
        val other = names.keys.first { it != suggestedTwo }

        setContent { KairosApp(Platform.DESKTOP) { services } }
        openBacklog()
        waitText("Suggest a distribution")
        onNodeWithText("Suggest a distribution").performClick()
        waitText("Suggested distribution")
        waitText("3 of 3 kept. Nothing is assigned until “Apply”.")
        // Exclus : la tâche à qualifier.
        waitText("To qualify (1): priority or points are missing.")
        assertEquals(0, repository.snapshot.value.tasks.count { it.assigneeId != null })

        // Décoche la ligne un, change l'assigné de la ligne deux.
        onNodeWithContentDescription("Keep “Line one”").performClick()
        waitText("2 of 3 kept. Nothing is assigned until “Apply”.")
        onNodeWithContentDescription("Change the assignee of “Line two”").performClick()
        onAllNodesWithText(names.getValue(other)).onLast().performClick()
        waitText("${names.getValue(other)}: chosen by you")

        onNodeWithText("Apply").performClick()
        waitUntil(timeoutMillis = 10_000) { repository.snapshot.value.tasks.first { it.id == ids.getValue("three") }.assigneeId != null }
        val tasks = repository.snapshot.value.tasks.associateBy { it.id }
        assertNull(tasks.getValue(ids.getValue("one")).assigneeId, "la ligne décochée n'est pas assignée")
        assertEquals(other, tasks.getValue(ids.getValue("two")).assigneeId)
        assertEquals(expected.suggestions.first { it.taskId == ids.getValue("three") }.memberId, tasks.getValue(ids.getValue("three")).assigneeId)
        assertEquals(2, tasks.values.count { it.assigneeId != null })
        waitGone("Suggested distribution")
    }

    @Test
    fun cancellingTheSuggestionAssignsNothing() = runComposeUiTest {
        val services = services { r ->
            crew(r, Crew())
            task(r, "Line one", 4)
        }
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openBacklog()
        waitText("Suggest a distribution")
        onNodeWithText("Suggest a distribution").performClick()
        waitText("1 of 1 kept. Nothing is assigned until “Apply”.")
        onNodeWithText("Cancel").performClick()
        waitGone("Suggested distribution")
        assertEquals(0, repository.snapshot.value.tasks.count { it.assigneeId != null })
    }

    @Test
    fun theTaskSheetSaysWhereTheEffortComesFrom() = runComposeUiTest {
        val crew = Crew()
        val ids = mutableMapOf<String, Long>()
        val services = services { r ->
            crew(r, crew)
            ids["estimate"] = task(r, "With estimate", 4)
            val points = r.createTeamTask("With points")!!
            r.updateTask(points, TaskEdit(title = "With points", priority = 1, points = 3, pinDay = today))
            ids["points"] = points
            ids["bare"] = r.createTeamTask("Bare")!!
        }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openBacklog()
        // Le Backlog est une liste paresseuse : on filtre pour que la ligne soit à l'écran.
        fun open(title: String) {
            onNodeWithText("Search").performTextClearance()
            onNodeWithText("Search").performTextInput(title)
            // Le champ de recherche porte aussi le titre : la ligne est le dernier nœud.
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(title).fetchSemanticsNodes().size >= 2 }
            onAllNodesWithText(title).onLast().performClick()
        }
        waitText("Search")

        open("With estimate")
        waitText("4 h, estimate")
        onNodeWithText("Cancel").performClick()
        waitGone("4 h, estimate")

        open("With points")
        waitText("6 h, 3 points × 2 h")
        onNodeWithText("Cancel").performClick()
        waitGone("6 h, 3 points × 2 h")

        open("Bare")
        waitText("unestimated")
    }

    @Test
    fun theTeamCardOfTheSettingsOffersTheFiveLoadSettingsAndRefusesAFocusAboveOne() = runComposeUiTest {
        val services = services { }
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Settings").onFirst().performClick()
        waitText("Appearance")
        listOf("Load horizon (weeks)", "Focus rate", "Hours per point", "Load alert from (%)", "Affinity tolerance (working days)").forEach {
            onNodeWithText(it).performScrollTo().assertExists()
        }
        assertEquals(1, count("Share of the available time given to tasks", substring = true))

        onNodeWithText("Focus rate").performScrollTo()
        onNodeWithText("Focus rate").performTextClearance()
        onNodeWithText("Focus rate").performTextInput("1.5")
        onNodeWithText("Save").performClick()
        waitText("At most 1.")
        assertEquals(0.8, repository.snapshot.value.settings.team!!.focusFactor)

        onNodeWithText("Focus rate").performTextClearance()
        onNodeWithText("Focus rate").performTextInput("0.7")
        onNodeWithText("Hours per point").performTextClearance()
        onNodeWithText("Hours per point").performTextInput("3")
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.settings.team!!.focusFactor == 0.7 }
        assertEquals(3.0, repository.snapshot.value.settings.team!!.hoursPerPoint)
    }

}
