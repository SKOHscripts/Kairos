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
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.BackupStore
import com.skohscripts.kairos.ui.app.FileService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
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
 * Espace Équipe, jalon E2 (docs/spec/equipe.md § Destination Équipe) : ajout et
 * modification d'un membre, « C'est moi » unique, absences, archivage,
 * suppression, « Supprimer les données d'équipe… ». Base SQLite en mémoire,
 * horloge figée (mardi 29 septembre 2026), interface en anglais.
 */
@OptIn(ExperimentalTestApi::class)
class TeamMembersUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    /** Sauvegardes reçues (nom → contenu) ; [failBackups] simule une sauvegarde impossible. */
    private val backups = mutableMapOf<String, String>()
    private var failBackups = false

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private val enabledTeam = KairosSnapshot(settings = Settings(team = TeamSettings(enabled = true, name = "Platform", identity = "id-1", lastSpace = TeamSettings.SPACE_TEAM)))

    private fun services(seed: (suspend (KairosRepository) -> Unit)? = null): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(enabledTeam)
        seed?.invoke(repository)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        val store = BackupStore { name, text ->
            check(!failBackups) { "sauvegarde impossible" }
            backups[name] = text
        }
        AppServices(repository, files, store, clock = clock)
    }

    private fun task(id: Long, assignee: Long?, status: TaskStatus = TaskStatus.TODO, space: TaskSpace = TaskSpace.TEAM) = Task(
        id, "Task $id", priority = 1, fibonacciPoints = 3, estimatedMinutes = 30, status = status, space = space, assigneeId = assignee,
        createdAt = now, updatedAt = now,
    )

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitGone(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun ComposeUiTest.count(text: String, substring: Boolean = false) =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    /** Ouvre l'écran Équipe (membres) : l'espace Équipe s'ouvre sur Suivi, le rail a « Team ». */
    private fun ComposeUiTest.openMembers() {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Team").fetchSemanticsNodes().isNotEmpty() }
        waitText("Tracking")
        onAllNodesWithText("Team").onFirst().performClick()
        waitText("Add a member")
    }

    private fun ComposeUiTest.typeInto(label: String, text: String) {
        onNodeWithText(label).performTextClearance()
        if (text.isNotEmpty()) onNodeWithText(label).performTextInput(text)
    }

    private fun ComposeUiTest.save() = onAllNodesWithText("Save").onLast().performClick()

    @Test
    fun anEmptyTeamExplainsMembersAndOffersTheSameButton() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openMembers()
        waitText("Team members")
        assertEquals(1, count("Add a member"))
        assertEquals(0, count("Former members", substring = true))
        assertTrue(count("Add the people on your team", substring = true) == 1)
    }

    @Test
    fun addingThenEditingAMemberValidatesEachField() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openMembers()

        onNodeWithText("Add a member").performClick()
        waitText("New member")
        save()
        waitText("The name is required.")
        assertTrue(repository.snapshot.value.members.isEmpty())

        typeInto("Name", "  Alex Dupont ")
        typeInto("Role", "Backend developer")
        save()
        waitGone("New member")
        waitText("Alex Dupont")
        val created = repository.snapshot.value.members.single()
        assertEquals("Alex Dupont", created.name)
        assertEquals("Backend developer", created.role)
        assertEquals(100, created.availabilityPercent)
        assertEquals(8.0, created.hoursPerDay)
        assertTrue(created.uid.isNotEmpty())
        waitText("100% · 8 h/day")

        // Modification : deux champs invalides, erreurs à la place de l'aide, rien d'enregistré.
        onNodeWithText("Alex Dupont").performClick()
        waitText("Member")
        typeInto("Availability (%)", "150")
        typeInto("Hours per day", "abc")
        save()
        waitText("At most 100.")
        waitText("A number is expected.")
        assertEquals(0, count("Share of their time given to the team, from 1 to 100."))
        assertEquals(100, repository.snapshot.value.members.single().availabilityPercent)

        // Corrigé : virgule décimale acceptée.
        typeInto("Availability (%)", "80")
        typeInto("Hours per day", "6,5")
        save()
        waitGone("At most 100.")
        waitText("80% · 6.5 h/day")
        val edited = repository.snapshot.value.members.single()
        assertEquals(80, edited.availabilityPercent)
        assertEquals(6.5, edited.hoursPerDay)
        assertEquals(created.uid, edited.uid)
    }

    @Test
    fun tickingThisIsMeOnASecondMemberTakesItFromTheFirst() = runComposeUiTest {
        var first = 0L
        var second = 0L
        val services = services {
            first = it.createMember("Claire", "", 100, 7.0, true)!!
            second = it.createMember("Bruno", "", 100, 7.0, false)!!
        }
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openMembers()
        waitText("Claire")
        // « moi » : une seule marque, sur la première carte.
        assertEquals(1, count("me"))

        onNodeWithText("Bruno").performClick()
        waitText("Member")
        onNodeWithText("This is me").performClick()
        save()
        waitGone("Member")
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.members.single { it.id == second }.isSelf }
        assertTrue(repository.snapshot.value.members.single { it.id == first }.isSelf.not())
        assertEquals(1, count("me"))
        // « moi » passe en tête de liste : Bruno avant Claire.
        val top = onAllNodesWithText("Bruno").fetchSemanticsNodes().first().positionInRoot.y
        val below = onAllNodesWithText("Claire").fetchSemanticsNodes().first().positionInRoot.y
        assertTrue(top < below)
    }

    @Test
    fun anAbsenceAddedInTheSheetAppearsOnTheCard() = runComposeUiTest {
        val services = services { it.createMember("Alex", "", 100, 7.0, false) }
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openMembers()
        waitText("Alex")
        assertEquals(0, count("Away", substring = true))

        onNodeWithText("Alex").performClick()
        waitText("No absence recorded.")
        onNodeWithText("Add an absence").performScrollTo().performClick()
        waitText("New absence")
        // « Save » est inactif tant qu'aucune date n'est choisie ; le mois affiché est celui de l'horloge (septembre 2026).
        onNodeWithText("September 30, 2026", substring = true).performClick()
        onNodeWithText("Label").performTextInput("Vacation")
        save()
        waitGone("New absence")
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.absences.isNotEmpty() }
        val absence = repository.snapshot.value.absences.single()
        assertEquals(LocalDate(2026, 9, 30), absence.start)
        assertEquals(LocalDate(2026, 9, 30), absence.end)
        assertEquals("Vacation", absence.label)
        // La fiche liste l'absence aussitôt, sans « Save » de la fiche.
        waitText("Sep 30")
        // Supprimer l'absence depuis la fiche.
        onNodeWithContentDescription("Delete the absence").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.absences.isEmpty() }
        waitText("No absence recorded.")

        // Une absence ajoutée par le dépôt s'affiche sur la carte une fois la fiche fermée.
        runBlocking { repository.addAbsence(absence.memberId, LocalDate(2026, 10, 12), LocalDate(2026, 10, 16), "Vacation") }
        onNodeWithText("Cancel").performClick()
        waitGone("Member")
        waitText("Away Oct 12 to 16 · Vacation")
    }

    @Test
    fun archivingAsksConfirmationCountsOpenTasksAndMovesTheCard() = runComposeUiTest {
        var alex = 0L
        val services = services {
            alex = it.createMember("Alex", "", 100, 7.0, false)!!
            it.replaceAll(
                it.snapshot.value.copy(
                    tasks = listOf(task(1, alex), task(2, alex), task(3, alex, TaskStatus.DONE)),
                ),
            )
        }
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openMembers()
        onNodeWithText("Alex").performClick()
        waitText("Member")
        // Ayant eu des tâches, il ne peut pas être supprimé : seul l'archivage est proposé.
        assertEquals(0, count("Delete the member"))
        onNodeWithText("Archive").performScrollTo().performClick()
        waitText("Archive Alex?")
        assertEquals(1, count("2 open tasks will go back to the backlog.", substring = true))
        // Annuler n'archive rien.
        onAllNodesWithText("Cancel").onLast().performClick()
        waitGone("Archive Alex?")
        assertEquals(false, repository.snapshot.value.members.single().archived)

        onNodeWithText("Archive").performScrollTo().performClick()
        waitText("Archive Alex?")
        onAllNodesWithText("Archive").onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.members.single().archived }
        assertNull(repository.snapshot.value.tasks.single { it.id == 1L }.assigneeId)
        assertNull(repository.snapshot.value.tasks.single { it.id == 2L }.assigneeId)
        assertEquals(alex, repository.snapshot.value.tasks.single { it.id == 3L }.assigneeId)

        // La carte passe dans « Former members (1) », repliée ; elle réapparaît en dépliant.
        waitText("Former members (1)")
        assertEquals(0, count("Alex"))
        onNodeWithText("Former members (1)").performClick()
        waitText("Alex")

        // Réactivation depuis la fiche.
        onNodeWithText("Alex").performClick()
        waitText("Reactivate")
        assertEquals(0, count("This is me"))
        onNodeWithText("Reactivate").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { !repository.snapshot.value.members.single().archived }
        waitGone("Former members (1)")
        waitText("Alex")
    }

    @Test
    fun aMemberWhoNeverHadATaskCanBeDeletedAfterConfirmation() = runComposeUiTest {
        val services = services { it.createMember("Alex", "", 100, 7.0, false) }
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openMembers()
        onNodeWithText("Alex").performClick()
        waitText("Member")
        onNodeWithText("Delete the member").performScrollTo().performClick()
        waitText("Delete Alex?")
        onAllNodesWithText("Cancel").onLast().performClick()
        waitGone("Delete Alex?")
        assertEquals(1, repository.snapshot.value.members.size)

        onNodeWithText("Delete the member").performScrollTo().performClick()
        waitText("Delete Alex?")
        onAllNodesWithText("Delete the member").onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.members.isEmpty() }
        // Plus aucun membre : l'état vide revient.
        waitText("Team members")
    }

    private fun ComposeUiTest.openSettings() {
        onAllNodesWithText("Settings").onFirst().performClick()
        waitText("Appearance")
    }

    private fun teamData(repository: KairosRepository) = runBlocking {
        val a = repository.createMember("Claire", "", 100, 7.0, true)!!
        val b = repository.createMember("Alex", "", 100, 7.0, false)!!
        repository.createMember("Sam", "", 100, 7.0, false)
        repository.replaceAll(
            repository.snapshot.value.copy(
                tasks = listOf(task(1, a), task(2, b), task(3, null, space = TaskSpace.PERSONAL).copy(title = "Personal chore")),
            ),
        )
    }

    @Test
    fun deletingTheTeamDataBacksUpFirstThenEmptiesTheTeam() = runComposeUiTest {
        val services = services { teamData(it) }
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Tracking")
        openSettings()

        onNodeWithText("Delete team data…").performScrollTo().performClick()
        waitText("Delete team data?")
        assertEquals(1, count("3 members and 2 team tasks will be deleted.", substring = true))
        // Annuler : rien ne change, aucune sauvegarde.
        onNodeWithText("Cancel").performClick()
        waitGone("Delete team data?")
        assertEquals(3, repository.snapshot.value.members.size)
        assertTrue(backups.isEmpty())

        onNodeWithText("Delete team data…").performScrollTo().performClick()
        waitText("Delete team data?")
        onNodeWithText("Delete").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.members.isEmpty() }
        val (name, text) = backups.entries.single()
        assertTrue(Regex("""avant-suppression-equipe-\d{8}-\d{4}\.json""").matches(name), name)
        // La sauvegarde contient ce qui vient d'être supprimé.
        assertTrue("\"Claire\"" in text && "\"Alex\"" in text)
        waitText("Team data deleted.")
        assertEquals(listOf(3L), repository.snapshot.value.tasks.map { it.id })
        // Les réglages d'équipe restent, l'espace aussi.
        assertEquals("Platform", repository.snapshot.value.settings.team!!.name)
        assertTrue(repository.snapshot.value.settings.teamModeEnabled)

        // L'écran Équipe est vide de nouveau.
        onAllNodesWithText("Team").onFirst().performClick()
        waitText("Add a member")
        waitText("Team members")
        assertEquals(0, count("Claire"))
    }

    @Test
    fun withoutASuccessfulBackupNothingIsDeleted() = runComposeUiTest {
        failBackups = true
        val services = services { teamData(it) }
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Tracking")
        openSettings()
        onNodeWithText("Delete team data…").performScrollTo().performClick()
        waitText("Delete team data?")
        onNodeWithText("Delete").performClick()
        waitText("The file could not be read or written.")
        assertEquals(3, repository.snapshot.value.members.size)
        assertEquals(3, repository.snapshot.value.tasks.size)
        assertEquals(0, count("Team data deleted."))
    }
}
