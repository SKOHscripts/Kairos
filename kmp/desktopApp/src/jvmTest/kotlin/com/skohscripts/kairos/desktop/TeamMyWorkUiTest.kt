package com.skohscripts.kairos.desktop

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
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.data.TaskEdit
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Travail perso de « moi » dans l'espace Équipe (docs/spec/equipe-moi.md) : une tâche perso compte dans la charge de
 * « moi » (fiche du membre, pastille « Personal ») jusqu'à ce que l'interrupteur « Count in the team » de sa fiche
 * d'édition l'exclue. Base en mémoire, horloge figée (mardi 29 septembre 2026), interface en anglais.
 */
@OptIn(ExperimentalTestApi::class)
class TeamMyWorkUiTest {
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

    private class Seeded(val services: AppServices, val repository: KairosRepository, val taskId: Long)

    private fun seeded(withMe: Boolean): Seeded = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(
            KairosSnapshot(settings = Settings(team = TeamSettings(enabled = true, name = "Platform", identity = "id-1", lastSpace = TeamSettings.SPACE_TEAM, horizonWeeks = 1))),
        )
        repository.createMember("Claire", "", 100, 8.0, withMe)!!
        val id = repository.createTask("Quarterly report")!!
        repository.updateTask(id, TaskEdit(title = "Quarterly report", priority = 1, points = 3, estimatedMinutes = 240, pinDay = today))
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        Seeded(AppServices(repository, files, { _, _ -> }, clock = clock), repository, id)
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    private fun ComposeUiTest.openMySheet() {
        waitText("Tracking")
        onAllNodesWithText("Team").onFirst().performClick()
        waitText("Claire")
        onAllNodesWithText("Claire").onFirst().performClick()
        waitText("Open tasks, in plan order")
    }

    /** Ferme la fiche du membre : icône « Close » (plein écran) ou bouton « Cancel » (dialogue). */
    private fun ComposeUiTest.closeSheet() {
        if (onAllNodesWithContentDescription("Close").fetchSemanticsNodes().isNotEmpty()) {
            onAllNodesWithContentDescription("Close").onFirst().performClick()
        } else {
            onAllNodesWithText("Cancel").onFirst().performClick()
        }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Open tasks, in plan order").fetchSemanticsNodes().isEmpty() }
    }

    private fun ComposeUiTest.editTheTaskWithMoreOptions() {
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithContentDescription("Edit").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription("Edit").onFirst().performClick()
        waitText("Edit task")
        onNodeWithText("More options").performScrollTo().performClick()
    }

    @Test
    fun aPersonalTaskCountsForMeUntilExcluded() = runComposeUiTest {
        val s = seeded(withMe = true)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { s.services } }

        // Espace Équipe : la carte de « moi » compte la tâche perso, son plan la contient, marquée « Personal »,
        // et la charge dit d'où elle vient.
        waitText("Tracking")
        onAllNodesWithText("Team").onFirst().performClick()
        waitText("0 in progress · 0 to do · 1 personal")
        openMySheet()
        waitText("Quarterly report")
        waitText("Personal")
        waitText("Including 4 h of personal tasks", substring = true)

        // Espace Perso : l'interrupteur de la fiche l'exclut.
        closeSheet()
        onNodeWithContentDescription("Personal").performClick()
        editTheTaskWithMoreOptions()
        onNodeWithText("Count in the team").performScrollTo().performClick()
        // Le dialogue défile : le bouton doit être visible, sinon le clic tombe sur le fond et ferme sans enregistrer.
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { s.repository.snapshot.value.tasks.single { it.id == s.taskId }.teamExcluded }

        // Retour à l'équipe : elle n'y est plus.
        onNodeWithContentDescription("Team").performClick()
        openMySheet()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Quarterly report").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun theSwitchOnlyShowsWhenAMemberIsMe() = runComposeUiTest {
        val s = seeded(withMe = false)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { s.services } }
        waitText("Tracking")
        onNodeWithContentDescription("Personal").performClick()
        editTheTaskWithMoreOptions()
        // Les options avancées sont ouvertes : « Heure fixe » y est (« Bloquée par » est passé dans « Découpage et enchaînement »).
        waitText("Fixed time (HH:MM)", substring = true)
        assertEquals(0, onAllNodesWithText("Count in the team").fetchSemanticsNodes().size)
        assertTrue(s.repository.snapshot.value.tasks.none { it.teamExcluded })
    }
}
