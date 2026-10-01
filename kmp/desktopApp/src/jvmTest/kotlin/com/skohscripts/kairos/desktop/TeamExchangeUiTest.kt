package com.skohscripts.kairos.desktop

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.exchange.ReceivedTasks
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.data.Reassignment
import com.skohscripts.kairos.data.TaskEdit
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Échanges par fichier, jalon E6 (docs/spec/equipe-echanges.md § Interface) : l'aiguillage du bouton « Importer »
 * (export complet, paquet, rapport), les aperçus de réception et d'intégration, la sauvegarde préalable, « Envoyer ses
 * tâches… » de la fiche membre, « Renvoyer l'avancement… » des Réglages, la marque « de <manager> » des tâches reçues
 * et leur avancement. Deux bases en mémoire : le manager (Corentin) et un membre sans espace Équipe (Léa). Horloge
 * figée (mardi 29 septembre 2026), interface en anglais.
 */
@OptIn(ExperimentalTestApi::class)
class TeamExchangeUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    /** Sélecteur de fichier simulé : [toOpen] est ce que « Importer » lit, [written] ce que les enregistrements ont reçu. */
    private class Files(var toOpen: String? = null, var accept: Boolean = true) : FileService {
        val written = mutableListOf<Pair<String, String>>()

        /** [accept] faux : l'utilisateur annule la boîte d'enregistrement. */
        override suspend fun saveText(suggestedName: String, text: String): Boolean {
            if (accept) written += suggestedName to text
            return accept
        }

        override suspend fun openText(): String? = toOpen
    }

    private val backups = mutableMapOf<String, String>()
    private var failBackups = false

    private fun repository(): KairosRepository = runBlocking {
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
    }

    private fun services(repository: KairosRepository, files: Files = Files()): AppServices {
        val store = BackupStore { name, text ->
            check(!failBackups) { "sauvegarde impossible" }
            backups[name] = text
        }
        return AppServices(repository, files, store, clock = clock)
    }

    /** Le manager : équipe « Équipe Plateforme », Léa (deux tâches ouvertes dont une sous-tâche) et Marc (une tâche qui bloque celle de Léa). */
    private class Manager(val repo: KairosRepository, val lea: Long, val marc: Long, val migration: Long, val recette: Long, val extra: Long) {
        val snap get() = repo.snapshot.value
        val leaUid get() = snap.members.single { it.id == lea }.uid
    }

    private fun manager(): Manager = runBlocking {
        val repo = repository()
        repo.updateSettings(Settings(team = TeamSettings(enabled = true, name = "Équipe Plateforme", managerName = "Corentin", lastSpace = TeamSettings.SPACE_TEAM)))
        val lea = repo.createMember("Léa", "Dev", 100, 7.0, false)!!
        val marc = repo.createMember("Marc", "Dev", 100, 7.0, false)!!
        repo.createMember("Zoé", "Design", 100, 7.0, false)!!
        val migration = repo.createTeamTask("Migration API")!!
        val apiV2 = repo.createTeamTask("API v2")!!
        val day = LocalDate(2026, 9, 29)
        repo.updateTask(
            migration,
            TaskEdit(title = "Migration API", priority = 1, points = 5, taskType = "Développement", deadline = LocalDate(2026, 10, 10), estimatedMinutes = 480, pinDay = day, reassign = Reassignment(lea)),
        )
        val extra = repo.createTeamTask("Écrire les migrations", migration)!!
        val recette = repo.createTeamTask("Recette")!!
        repo.updateTask(recette, TaskEdit(title = "Recette", priority = 2, points = 2, pinDay = day, reassign = Reassignment(lea), blockerIds = setOf(migration, apiV2)))
        repo.updateTask(apiV2, TaskEdit(title = "API v2", priority = 1, points = 3, pinDay = day, reassign = Reassignment(marc)))
        repo.setProgress(migration, 40)
        Manager(repo, lea, marc, migration, recette, extra)
    }

    /** Le fichier-paquet de Léa. */
    private fun pack(manager: Manager): String = runBlocking { manager.repo.exportPack(manager.lea, "pack-1")!! }

    /** Léa a reçu le paquet [text] ; elle n'a pas d'espace Équipe. */
    private fun memberWith(text: String): KairosRepository = repository().also { runBlocking { it.receivePack(text) } }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitGone(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isEmpty() }

    private fun ComposeUiTest.count(text: String, substring: Boolean = false) =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private fun ComposeUiTest.openSettings() {
        waitText("Settings")
        onAllNodesWithText("Settings").onFirst().performClick()
        waitText("Appearance")
    }

    /** Réglages → Données → « Importer » (le sélecteur simulé rend [Files.toOpen]). */
    private fun ComposeUiTest.pressImport() {
        onNodeWithText("Import").performScrollTo().performClick()
    }

    // --- Côté membre : recevoir un paquet ----------------------------------------------------------------------------------

    @Test
    fun importingAPackShowsAPreviewThenReceivesItAfterABackup() = runComposeUiTest {
        val manager = manager()
        val files = Files(pack(manager))
        val services = services(repository(), files)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openSettings()
        pressImport()

        // Aperçu : rien n'est encore écrit.
        waitText("Receive 3 tasks from Corentin (Équipe Plateforme)?")
        waitText("New (3)")
        waitText("Migration API")
        waitText("Écrire les migrations")
        // Le bloqueur tenu par Marc est montré à titre d'information.
        waitText("Blocked by other members (for information)")
        waitText("Recette: blocked by “API v2” (Marc)")
        waitText("A backup of your current data is made just before.")
        assertTrue(services.repository.snapshot.value.tasks.isEmpty())
        assertTrue(backups.isEmpty())
        assertEquals(0, count("Replace all data?"))

        onNodeWithText("Receive").performClick()
        waitUntil(timeoutMillis = 5_000) { services.repository.snapshot.value.tasks.size == 3 }
        waitText("Packet received: 3 new, 0 updated, 0 removed.")
        assertEquals(1, backups.keys.count { it.startsWith("avant-reception-paquet-") && it.endsWith(".json") })
        val received = services.repository.snapshot.value.tasks
        assertTrue(received.all { ReceivedTasks.isReceived(it) })
        assertEquals("Corentin", ReceivedTasks.originOf(received.first())!!.managerName)
    }

    @Test
    fun cancelingThePackPreviewChangesNothing() = runComposeUiTest {
        val manager = manager()
        val services = services(repository(), Files(pack(manager)))
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openSettings()
        pressImport()
        waitText("New (3)")
        onNodeWithText("Cancel").performClick()
        waitGone("New (3)")
        assertTrue(services.repository.snapshot.value.tasks.isEmpty())
        assertTrue(backups.isEmpty())
    }

    @Test
    fun withoutASuccessfulBackupNothingIsReceived() = runComposeUiTest {
        val manager = manager()
        val services = services(repository(), Files(pack(manager)))
        failBackups = true
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openSettings()
        pressImport()
        waitText("New (3)")
        onNodeWithText("Receive").performClick()
        waitText("The file could not be read or written.")
        assertTrue(services.repository.snapshot.value.tasks.isEmpty())
    }

    @Test
    fun receivingThePackAgainIsAnnouncedAsAlreadyReceived() = runComposeUiTest {
        val manager = manager()
        val text = pack(manager)
        val services = services(memberWith(text), Files(text))
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openSettings()
        pressImport()
        waitText("Nothing to change: this packet was already received or contains no task.")
        // Rien à confirmer : seul « Fermer » est proposé.
        assertEquals(0, count("Receive"))
        onNodeWithText("Close").performClick()
        waitGone("Nothing to change: this packet was already received or contains no task.")
        assertEquals(3, services.repository.snapshot.value.tasks.size)
        assertTrue(backups.isEmpty())
    }

    @Test
    fun aPackFromTheTeamManagedOnThisDeviceIsRefused() = runComposeUiTest {
        val manager = manager()
        val services = services(manager.repo, Files(pack(manager)))
        val before = manager.snap.tasks.size
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openSettings()
        pressImport()
        waitText("Packet not received")
        waitText("This packet comes from the team managed on this device: it is not to be received here.")
        assertEquals(0, count("Receive"))
        onNodeWithText("Close").performClick()
        waitGone("Packet not received")
        assertEquals(before, manager.snap.tasks.size)
        assertTrue(backups.isEmpty())
    }

    @Test
    fun aNewPackMarksMissingTasksAsRemovedAndNamesTheUpdates() = runComposeUiTest {
        val manager = manager()
        val member = memberWith(pack(manager))
        // Le manager retire « Recette » à Léa et renomme « Migration API » : second paquet.
        runBlocking {
            manager.repo.updateTask(manager.recette, TaskEdit(title = "Recette", priority = 2, points = 2, pinDay = LocalDate(2026, 9, 29), reassign = Reassignment(manager.marc)))
            manager.repo.updateTask(manager.migration, TaskEdit(title = "Migration API v2", priority = 1, points = 5, taskType = "Développement", deadline = LocalDate(2026, 10, 10), estimatedMinutes = 480, pinDay = LocalDate(2026, 9, 29)))
        }
        val services = services(member, Files(pack(manager)))
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openSettings()
        pressImport()
        waitText("Updated (1)")
        waitText("Migration API v2")
        waitText("title")
        waitText("Removed (1)")
        waitText("They are not deleted: they stay in your tasks, marked “removed by Corentin”.", substring = true)
        onNodeWithText("Receive").performClick()
        waitText("Packet received: 0 new, 1 updated, 1 removed.")
        val tasks = services.repository.snapshot.value.tasks
        assertEquals(3, tasks.size)
        assertTrue(tasks.single { it.title == "Recette" }.originRemoved)
    }

    @Test
    fun anEmptyPacketPreviewsTheRemovalsAndAppliesThem() = runComposeUiTest {
        val manager = manager()
        val member = memberWith(pack(manager))
        runBlocking {
            manager.repo.deleteTask(manager.extra)
            manager.repo.assign(listOf(manager.migration, manager.recette), manager.marc)
        }
        val services = services(member, Files(pack(manager)))
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openSettings()
        pressImport()
        waitText("Update the tasks received from Corentin (Équipe Plateforme)?")
        waitText("Removed (3)")
        assertEquals(0, count("Receive 0 tasks", substring = true))
        assertEquals(0, count("They join your personal tasks", substring = true))
        onNodeWithText("Receive").performClick()
        waitText("Packet received: 0 new, 0 updated, 3 removed.")
        assertTrue(services.repository.snapshot.value.tasks.all { it.originRemoved })
    }

    // --- Côté manager : intégrer un rapport --------------------------------------------------------------------------------

    /** Léa a reçu le paquet, avance « Migration API » à 70 % et ajoute une sous-tâche : le rapport qu'elle renvoie. */
    private fun report(manager: Manager): String = runBlocking {
        val member = memberWith(pack(manager))
        val migration = member.snapshot.value.tasks.single { it.title == "Migration API" }
        member.setReceivedProgress(migration.id, 70)
        member.createTask("Écrire la recette", migration.id)
        val key = member.origins().single().key
        member.buildReport(key)!!
    }

    @Test
    fun importingAReportShowsWhatChangesThenIntegratesIt() = runComposeUiTest {
        val manager = manager()
        val text = report(manager)
        val services = services(manager.repo, Files(text))
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openSettings()
        pressImport()

        waitText("Integrate the report from Léa?")
        waitText("Updated tasks (1)")
        waitText("Progress: 40% → 70%")
        waitText("New subtasks (1)")
        waitText("Écrire la recette (under “Migration API”)")
        // Rien n'est encore écrit chez le manager.
        assertEquals(40, manager.snap.tasks.single { it.id == manager.migration }.progressPercent)
        assertNull(manager.snap.members.single { it.id == manager.lea }.lastReportAt)

        onNodeWithText("Integrate").performClick()
        waitUntil(timeoutMillis = 5_000) { manager.snap.tasks.single { it.id == manager.migration }.progressPercent == 70 }
        waitText("Report from Léa integrated: 1 tasks updated, 1 subtasks added.")
        assertNotNull(manager.snap.members.single { it.id == manager.lea }.lastReportAt)
        assertTrue(manager.snap.tasks.any { it.title == "Écrire la recette" })
        assertEquals(1, backups.keys.count { it.startsWith("avant-integration-rapport-") })
    }

    @Test
    fun aReportOlderThanTheLastOneIsRefusedWithItsDate() = runComposeUiTest {
        val manager = manager()
        val text = report(manager)
        runBlocking { manager.repo.integrateReport(text) }
        val services = services(manager.repo, Files(text))
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openSettings()
        pressImport()
        waitText("Report not integrated")
        waitText("This report is older than the one of Sep 29 already integrated.")
        assertEquals(0, count("Integrate"))
        onNodeWithText("Close").performClick()
        waitGone("Report not integrated")
        assertTrue(backups.isEmpty())
    }

    @Test
    fun aReportAddressedToAnotherTeamIsRefused() = runComposeUiTest {
        val manager = manager()
        // Cette base-ci n'est pas l'équipe du rapport (aucun espace Équipe).
        val services = services(repository(), Files(report(manager)))
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openSettings()
        pressImport()
        waitText("Report not integrated")
        waitText("This report is addressed to another team.")
        assertEquals(0, count("Integrate"))
        assertTrue(backups.isEmpty())
    }

    // --- Aiguillage ---------------------------------------------------------------------------------------------------------

    @Test
    fun aFullExportStillAsksToReplaceEverything() = runComposeUiTest {
        val source = repository()
        runBlocking { source.createTask("Imported task") }
        val text = com.skohscripts.kairos.data.ExportCodec.encode(source.snapshot.value, "3.0.0", now)
        val services = services(repository(), Files(text))
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openSettings()
        pressImport()
        waitText("Replace all data?")
        assertEquals(0, count("Receive", substring = true))
        onNodeWithText("Replace").performClick()
        waitUntil(timeoutMillis = 5_000) { services.repository.snapshot.value.tasks.size == 1 }
        assertEquals(1, backups.keys.count { it.startsWith("avant-import-") })
    }

    @Test
    fun anUnknownFileKeepsTheUsualErrorMessage() = runComposeUiTest {
        val services = services(repository(), Files("""{"format":"something-else"}"""))
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openSettings()
        pressImport()
        waitText("This file is not a Kairos export.")
        assertTrue(services.repository.snapshot.value.tasks.isEmpty())
    }

    // --- Côté manager : envoyer ses tâches -------------------------------------------------------------------------------------

    private fun ComposeUiTest.openMember(name: String) {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Team").fetchSemanticsNodes().isNotEmpty() }
        waitText("Tracking")
        onAllNodesWithText("Team").onFirst().performClick()
        waitText("Add a member")
        onNodeWithText(name).performClick()
        waitText("Member")
    }

    @Test
    fun sendingTheTasksOfAMemberSavesThePacketAndLogsIt() = runComposeUiTest {
        val manager = manager()
        val files = Files()
        val services = services(manager.repo, files)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openMember("Léa")
        waitText("Exchanges")
        waitText("No report integrated yet.")
        onNodeWithText("It contains task titles and descriptions and is not encrypted.", substring = true).assertExists()

        onNodeWithText("Send their tasks…").performScrollTo().performClick()
        waitText("Packet saved for Léa.")
        val (name, text) = files.written.single()
        assertTrue(Regex("""kairos-paquet-léa-\d{8}-\d{4}\.json""").matches(name), name)
        assertTrue("\"kairos-team-pack\"" in text)
        assertTrue("Migration API" in text)
        // Le journal des tâches envoyées porte l'envoi.
        assertEquals(3, manager.snap.teamEvents.count { it.kind.name == "SENT" })
    }

    @Test
    fun cancelingTheSaveDialogLogsNoSentEvent() = runComposeUiTest {
        val manager = manager()
        val files = Files(accept = false)
        val services = services(manager.repo, files)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openMember("Léa")
        onNodeWithText("Send their tasks…").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { true }
        mainClock.advanceTimeBy(500)
        assertTrue(files.written.isEmpty())
        assertEquals(0, count("Packet saved for Léa."))
        assertEquals(0, manager.snap.teamEvents.count { it.kind.name == "SENT" })

        // Enregistrer pour de bon journalise l'envoi.
        files.accept = true
        onNodeWithText("Send their tasks…").performScrollTo().performClick()
        waitText("Packet saved for Léa.")
        assertEquals(3, manager.snap.teamEvents.count { it.kind.name == "SENT" })
    }

    @Test
    fun aMemberWhoLostAllTasksStillGetsAnEmptyPacketOnceSentBefore() = runComposeUiTest {
        val manager = manager()
        runBlocking {
            manager.repo.exportPack(manager.lea, "pack-1")
            manager.repo.deleteTask(manager.extra)
            manager.repo.assign(listOf(manager.migration, manager.recette), manager.marc)
        }
        val files = Files()
        val services = services(manager.repo, files)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openMember("Léa")
        onNodeWithText("Send their tasks…").performScrollTo().performClick()
        waitText("Packet saved: it removes the tasks sent to Léa.")
        val (name, text) = files.written.single()
        assertTrue(name.startsWith("kairos-paquet-léa-"), name)
        assertTrue("\"kairos-team-pack\"" in text)
        assertEquals(0, com.skohscripts.kairos.data.TeamExchangeCodec.decodePack(text).tasks.size)
    }

    @Test
    fun aMemberWithoutOpenTasksHasNothingToSend() = runComposeUiTest {
        val manager = manager()
        val files = Files()
        val services = services(manager.repo, files)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openMember("Zoé")
        onNodeWithText("Send their tasks…").performScrollTo().performClick()
        waitText("Zoé has no open task: nothing to send.")
        assertTrue(files.written.isEmpty())
        assertEquals(0, manager.snap.teamEvents.count { it.kind.name == "SENT" })
    }

    @Test
    fun theSheetShowsTheDateOfTheLastIntegratedReportAndNoExchangesForMe() = runComposeUiTest {
        val manager = manager()
        runBlocking {
            manager.repo.integrateReport(report(manager))
            manager.repo.createMember("Claire", "Lead", 100, 7.0, true)
        }
        val services = services(manager.repo)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openMember("Léa")
        waitText("Last report integrated: Sep 29", substring = true)
        // Le membre « moi » n'a pas de paquet à recevoir de lui-même.
        onNodeWithText("Cancel").performClick()
        waitGone("Member")
        onNodeWithText("Claire").performClick()
        waitText("Member")
        assertEquals(0, count("Exchanges"))
        assertEquals(0, count("Send their tasks…"))
    }

    // --- Côté membre : marque, avancement, rapport -----------------------------------------------------------------------------

    @Test
    fun receivedTasksCarryTheManagerMarkAndRemovedOnesSayWho() = runComposeUiTest {
        val manager = manager()
        val member = memberWith(pack(manager))
        val services = services(member)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("task received from Corentin").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(count("from Corentin") >= 1)
        assertEquals(0, count("removed by Corentin"))

        // Le manager supprime la sous-tâche : le paquet suivant ne la contient plus, elle est marquée « retirée par Corentin ».
        runBlocking {
            manager.repo.deleteTask(manager.extra)
            member.receivePack(pack(manager))
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("task removed by Corentin").fetchSemanticsNodes().isNotEmpty() }
        waitText("removed by Corentin")
    }

    @Test
    fun keepingARemovedTaskAsPersonalDropsTheMark() = runComposeUiTest {
        val manager = manager()
        val member = memberWith(pack(manager))
        runBlocking {
            manager.repo.deleteTask(manager.extra)
            member.receivePack(pack(manager))
            // Il ne reste que la tâche retirée dans la vue : un seul « Modifier ».
            member.snapshot.value.tasks.filter { !it.originRemoved }.sortedByDescending { it.id }.forEach { member.deleteTask(it.id) }
        }
        val services = services(member)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("removed by Corentin")
        onNodeWithContentDescription("Edit").performClick()
        waitText("Edit task")
        // Supprimer ou garder : les deux choix sont proposés dans la fiche.
        onNodeWithText("Delete").performScrollTo().assertExists()
        onNodeWithText("Keep as a personal task").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { services.repository.snapshot.value.tasks.single().origin == null }
        waitGone("Edit task")
        waitGone("removed by Corentin")
        assertEquals(0, count("from Corentin", substring = true))
        assertTrue(services.repository.origins().isEmpty())
    }

    @Test
    fun aKeepButtonIsOnlyOfferedForRemovedTasks() = runComposeUiTest {
        val manager = manager()
        val member = memberWith(pack(manager))
        runBlocking { member.snapshot.value.tasks.filter { it.title != "Écrire les migrations" }.sortedByDescending { it.id }.forEach { member.deleteTask(it.id) } }
        val services = services(member)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Edit").fetchSemanticsNodes().size == 1 }
        onNodeWithContentDescription("Edit").performClick()
        waitText("Edit task")
        assertEquals(0, count("Keep as a personal task"))
    }

    @Test
    fun aSoloBaseShowsNoMarkAndNoSendBackButton() = runComposeUiTest {
        val repo = repository()
        runBlocking {
            val id = repo.createTask("Mine")!!
            repo.setPriority(id, 1)
            repo.setPoints(id, 2)
        }
        val services = services(repo)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Mine", substring = true)
        assertEquals(0, count("from ", substring = true))
        assertEquals(0, onAllNodes(hasContentDescription("task received from", substring = true)).fetchSemanticsNodes().size)
        openSettings()
        waitText("Import")
        assertEquals(0, count("Send back progress", substring = true))
        assertEquals(0, count("Saves a file with the status", substring = true))
    }

    @Test
    fun theProgressOfAReceivedTaskIsSetInTheEditDialog() = runComposeUiTest {
        val manager = manager()
        val member = memberWith(pack(manager))
        val services = services(member)
        val migration = member.snapshot.value.tasks.single { it.title == "Migration API" }
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Edit").fetchSemanticsNodes().isNotEmpty() }
        // Une seule tâche reçue reste : un seul « Modifier ».
        runBlocking { member.snapshot.value.tasks.filter { it.id != migration.id }.sortedByDescending { it.id }.forEach { member.deleteTask(it.id) } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Edit").fetchSemanticsNodes().size == 1 }
        onNodeWithContentDescription("Edit").performClick()
        waitText("Edit task")
        waitText("Received from Corentin. Title, description, priority, points, category, deadline and duration are replaced with every new packet; progress is yours.")
        onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        onNodeWithText("Save").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { services.repository.snapshot.value.tasks.single { it.id == migration.id }.progressPercent == 30 }
        assertNotNull(services.repository.snapshot.value.tasks.single { it.id == migration.id }.startedOn)
    }

    @Test
    fun sendingBackTheProgressSavesTheReportOfTheTeam() = runComposeUiTest {
        val manager = manager()
        val files = Files()
        val services = services(memberWith(pack(manager)), files)
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openSettings()
        waitText("Send back progress…")
        onNodeWithText("Saves a file with the status", substring = true).performScrollTo()
        onNodeWithText("Send back progress…").performScrollTo().performClick()
        waitText("Report saved.")
        val (name, text) = files.written.single()
        assertTrue(Regex("""kairos-rapport-léa-\d{8}-\d{4}\.json""").matches(name), name)
        assertTrue("\"kairos-team-report\"" in text)
    }

    @Test
    fun severalManagersGetOneSendBackButtonEach() = runComposeUiTest {
        val first = manager()
        val second = manager()
        val member = memberWith(pack(first))
        runBlocking { member.receivePack(pack(second)) }
        val services = services(member, Files())
        setContent { KairosApp(Platform.DESKTOP) { services } }
        openSettings()
        waitText("Send back progress to", substring = true)
        assertEquals(2, count("Send back progress to Corentin (Équipe Plateforme)…"))
        assertEquals(0, count("Send back progress…"))
        assertFalse(services.repository.origins().isEmpty())
        assertEquals(TaskStatus.TODO, services.repository.snapshot.value.tasks.first().status)
    }
}
