package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.TeamMember
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Coquille à deux espaces, jalon E1 (docs/spec/equipe.md) : activation dans les
 * Réglages, sélecteur d'espace, écrans d'équipe vides, désactivation confirmée
 * et isolation du mode solo. Base SQLite en mémoire, horloge figée (mardi
 * 29 septembre 2026, 7 h UTC), interface en anglais.
 */
@OptIn(ExperimentalTestApi::class)
class TeamSpaceUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(seed: KairosSnapshot? = null): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        if (seed != null) repository.replaceAll(seed)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitGone(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size

    /** Nombre d'endroits de la vue où une tâche est écrite (le titre peut figurer dans un libellé plus long). */
    private fun ComposeUiTest.mentions(title: String) = onAllNodesWithText(title, substring = true).fetchSemanticsNodes().size

    private fun ComposeUiTest.selectorShown() = onAllNodesWithContentDescription("Space").fetchSemanticsNodes().isNotEmpty()

    private val personalDestinations = listOf("Notes", "Day", "Week", "Stats", "Settings")
    private val teamDestinations = listOf("Tracking", "Backlog", "Team", "Forecast", "Settings")

    /** Ouvre les Réglages depuis l'espace courant (le rail affiche « Settings » dans les deux espaces). */
    private fun ComposeUiTest.openSettings() {
        onAllNodesWithText("Settings").onFirst().performClick()
        waitText("Appearance")
    }

    private fun ComposeUiTest.toggleTeamMode() {
        onNodeWithText("Team management").performScrollTo().performClick()
    }

    private fun member(id: Long, name: String, self: Boolean = false) = TeamMember(
        id = id, uid = "uid-$id", name = name, role = "", availabilityPercent = 100, hoursPerDay = 7.0,
        isSelf = self, archived = false, createdAt = now, updatedAt = now,
    )

    private fun task(id: Long, title: String, space: TaskSpace = TaskSpace.PERSONAL, assignee: Long? = null) = Task(
        id, title, priority = 1, fibonacciPoints = 3, estimatedMinutes = 30, space = space, assigneeId = assignee,
        createdAt = now, updatedAt = now,
    )

    @Test
    fun soloModeShowsNoSpaceSelectorAndOnlyTheFivePersonalDestinations() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Day")
        personalDestinations.forEach { assertTrue(count(it) >= 1, it) }
        listOf("Tracking", "Backlog", "Forecast").forEach { assertEquals(0, count(it), it) }
        assertFalse(selectorShown())
        assertEquals(0, onAllNodesWithContentDescription("Team").fetchSemanticsNodes().size)

        // La carte Équipe des Réglages est fermée : l'interrupteur et sa phrase, rien d'autre.
        openSettings()
        onNodeWithText("Team management").performScrollTo().assertExists()
        onNodeWithText("Manage a team: members, shared backlog, workload and forecasts. No effect on your personal tasks.").assertExists()
        assertEquals(0, count("Team name"))
        assertEquals(0, count("Your name (in packets)"))
        // Enregistrer sans toucher à l'interrupteur ne crée aucun réglage d'équipe.
        assertEquals(null, services.repository.snapshot.value.settings.team)
    }

    @Test
    fun enablingTeamManagementShowsTheSelectorAndTheTeamSpaceOpensOnTracking() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Settings")
        openSettings()

        // Un interrupteur touché ne change rien avant « Enregistrer ».
        toggleTeamMode()
        waitText("Unsaved changes")
        assertFalse(selectorShown())
        assertEquals(0, count("Team name"))
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.settings.teamModeEnabled }
        waitUntil(timeoutMillis = 5_000) { selectorShown() }
        // Mode activé et enregistré : nom de l'équipe et du manager apparaissent.
        waitText("Team name")
        onNodeWithText("Your name (in packets)").performScrollTo().assertExists()
        assertTrue(repository.snapshot.value.settings.team!!.identity.isNotEmpty())

        // Cible tactile du sélecteur : 48 dp au moins.
        onNodeWithContentDescription("Team").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)

        // L'espace Équipe s'ouvre sur Suivi, avec ses cinq destinations et un état vide.
        onNodeWithContentDescription("Team").performClick()
        waitText("Team tracking")
        teamDestinations.forEach { assertTrue(count(it) >= 1, it) }
        listOf("Notes", "Week", "Stats").forEach { assertEquals(0, count(it), it) }
        // Jalon E3 : sans membre, le Suivi explique comment le remplir (plus de « disponible dans une prochaine version »).
        onNodeWithText("Add members in the Team tab", substring = true).assertExists()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.settings.team!!.lastSpace == TeamSettings.SPACE_TEAM }

        // Backlog (E3) : capture en tête, sections vides ; Prévisions (E5) propose de lancer une simulation ; Réglages est le même écran qu'en Perso.
        onAllNodesWithText("Backlog").onFirst().performClick()
        waitText("To qualify (0)")
        onAllNodesWithText("Team").onFirst().performClick()
        waitText("Team members")
        onAllNodesWithText("Forecast").onFirst().performClick()
        waitText("Run the simulation")
        openSettings()
        onNodeWithText("Working day").assertExists()

        // Le nom de l'équipe préfixe les titres de l'espace Équipe.
        onNodeWithText("Team name").performScrollTo().performTextInput("Platform")
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.settings.team!!.name == "Platform" }
        onAllNodesWithText("Tracking").onFirst().performClick()
        waitText("Platform · Tracking")

        // Retour à « Perso » : la vue Jour.
        onNodeWithContentDescription("Personal").performClick()
        waitText("Today")
        assertEquals(0, count("Team tracking"))
        personalDestinations.forEach { assertTrue(count(it) >= 1, it) }
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.settings.team!!.lastSpace == TeamSettings.SPACE_PERSONAL }
    }

    @Test
    fun hidingTheTeamSpaceAsksForConfirmation() = runComposeUiTest {
        val services = services(KairosSnapshot(settings = Settings(team = TeamSettings(enabled = true, name = "Platform", identity = "id-1"))))
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { selectorShown() }
        onNodeWithContentDescription("Team").performClick()
        waitText("Platform · Tracking")
        openSettings()

        toggleTeamMode()
        onNodeWithText("Save").performClick()
        waitText("Hide the Team space?")
        onNodeWithText("Its data is kept and will come back if you turn it on again.").assertExists()

        // « Cancel » n'enregistre rien et garde l'espace.
        onNodeWithText("Cancel").performClick()
        waitGone("Hide the Team space?")
        assertTrue(repository.snapshot.value.settings.teamModeEnabled)
        assertTrue(selectorShown())
        onNodeWithText("Working day").assertExists()

        // « Hide » : retour à l'espace Perso, vue Jour, plus de sélecteur ; les données restent.
        onNodeWithText("Save").performClick()
        waitText("Hide the Team space?")
        onNodeWithText("Hide").performClick()
        waitUntil(timeoutMillis = 5_000) { !repository.snapshot.value.settings.teamModeEnabled }
        waitUntil(timeoutMillis = 5_000) { !selectorShown() }
        waitText("Today")
        personalDestinations.forEach { assertTrue(count(it) >= 1, it) }
        assertEquals(0, count("Backlog"))
        val team = repository.snapshot.value.settings.team!!
        assertEquals("Platform", team.name)
        assertEquals(TeamSettings.SPACE_PERSONAL, team.lastSpace)
    }

    @Test
    fun teamTasksReachThePersonalDayOnlyWhenAssignedToMeAndTheModeIsOn() = runComposeUiTest {
        val seed = KairosSnapshot(
            tasks = listOf(
                task(1, "Personal chore"),
                task(2, "Mine from the team", TaskSpace.TEAM, assignee = 10),
                task(3, "Someone else’s job", TaskSpace.TEAM, assignee = 11),
                task(4, "Unassigned backlog item", TaskSpace.TEAM),
            ),
            members = listOf(member(10, "Me", self = true), member(11, "Alex")),
            settings = Settings(team = TeamSettings(enabled = false, identity = "id-1")),
        )
        val services = services(seed)
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { mentions("Personal chore") > 0 }
        // Mode désactivé : aucune tâche d'équipe dans la vue Jour.
        assertEquals(0, mentions("Mine from the team"))
        assertEquals(0, mentions("Someone else’s job"))
        assertEquals(0, mentions("Unassigned backlog item"))

        openSettings()
        toggleTeamMode()
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.settings.teamModeEnabled }
        onAllNodesWithText("Day").onFirst().performClick()
        // Activé : seule la tâche assignée à « moi » rejoint l'espace Perso.
        waitUntil(timeoutMillis = 5_000) { mentions("Mine from the team") > 0 }
        assertTrue(mentions("Personal chore") >= 1)
        assertEquals(0, mentions("Someone else’s job"))
        assertEquals(0, mentions("Unassigned backlog item"))

        // Désactivée de nouveau : elle en repart.
        openSettings()
        toggleTeamMode()
        onNodeWithText("Save").performClick()
        waitText("Hide the Team space?")
        onNodeWithText("Hide").performClick()
        waitUntil(timeoutMillis = 5_000) { !repository.snapshot.value.settings.teamModeEnabled }
        onAllNodesWithText("Day").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { mentions("Personal chore") > 0 }
        assertEquals(0, mentions("Mine from the team"))
    }
}
