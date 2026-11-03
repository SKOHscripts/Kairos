package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.sync.RecordKind
import com.skohscripts.kairos.core.sync.RecordRef
import com.skohscripts.kairos.core.sync.Stamp
import com.skohscripts.kairos.core.team.TeamMember
import com.skohscripts.kairos.core.team.Workspaces
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Synchronisation dans le dépôt (docs/spec/synchronisation.md § Dépôt, § Stockage, § Isolation du mode solo). */
class SyncRepositoryTest {
    private var now = Instant.parse("2026-10-26T08:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now.also { now += 1.minutes }
    }
    private val day = LocalDate(2026, 10, 26)
    private val examples = { ExampleData.snapshot(day, Instant.parse("2026-10-26T08:00:00Z"), "fr") }

    private suspend fun open(file: File? = null, posed: () -> KairosSnapshot = examples): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(if (file == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:${file.path}")), posed, clock, timeZone = TimeZone.UTC)

    private fun KairosRepository.export(): String = ExportCodec.encode(snapshot.value, "4.0.0", now, sync.value)

    private suspend fun KairosRepository.pull(from: KairosRepository): MergeResult = merge(ExportCodec.decodeSynced(from.export())) { true }

    private fun KairosRepository.task(title: String): Task? = snapshot.value.tasks.firstOrNull { it.title == title }

    /** Renomme une tâche par le chemin de la fiche (`updateTask`), sans rien changer d'autre. */
    private suspend fun KairosRepository.updateTitle(id: Long, title: String) {
        val t = snapshot.value.tasks.first { it.id == id }
        updateTask(id, TaskEdit(
            title = title, description = t.description, priority = t.priority, points = t.fibonacciPoints, deadline = t.deadline,
            estimatedMinutes = t.estimatedMinutes, projectTag = t.projectTag, taskType = t.taskType, scheduledDate = t.scheduledDate,
            recurrence = t.recurrence, recurrenceDayOfMonth = t.recurrenceDayOfMonth, pinTime = t.pinnedStart?.time,
            pinDay = t.pinnedStart?.date ?: t.scheduledDate ?: day, manualTimeSpentMinutes = t.manualTimeSpentMinutes,
            blockerIds = snapshot.value.dependencies.filter { it.taskId == id }.mapTo(HashSet()) { it.blockerId },
        ))
    }

    /** Deux appareils sans exemples, qui partagent déjà deux tâches ordinaires (les exemples ont la marque d'origine). */
    private suspend fun pair(): Pair<KairosRepository, KairosRepository> {
        val a = open(posed = { KairosSnapshot() })
        a.enableSync("PC")
        a.createTask("Rapport")
        a.createTask("Courrier")
        val b = open(posed = { KairosSnapshot() })
        b.enableSync("Téléphone")
        b.pull(a)
        a.pull(b)
        return a to b
    }

    private fun tmp(): File = Files.createTempFile("kairos-sync", ".db").toFile().also { it.delete() }

    @Test
    fun nothingIsStoredBeforeActivation() = runTest {
        val repo = open()
        repo.createTask("Rapport")
        assertNull(repo.sync.value)
        assertTrue(repo.syncReview.value.isEmpty())
        // L'export d'une base qui ne synchronise pas reste en format 1, sans aucun champ de synchronisation.
        val text = repo.export()
        assertTrue("\"formatVersion\": 1" in text)
        assertFalse("\"uid\"" in text || "\"sync\"" in text)
    }

    @Test
    fun activationGivesEveryRecordAUidAndUntouchedExamplesTheirExampleUid() = runTest {
        val repo = open()
        val firstExample = repo.snapshot.value.tasks.first()
        repo.updateTitle(repo.snapshot.value.tasks[1].id, "Modifié")
        val d = repo.enableSync("Bureau")
        assertEquals(12, d.device.length)
        assertEquals("Bureau", d.label)
        assertEquals(repo.personalSnapshot.value.tasks.size, d.uids[RecordKind.TASK]?.size)
        assertTrue(d.uidOf(RecordKind.TASK, firstExample.id)!!.startsWith("example:"))
        assertEquals(Stamp.ORIGIN, d.marks.getValue(RecordRef(RecordKind.TASK, d.uidOf(RecordKind.TASK, firstExample.id)!!)).born)
        assertTrue(d.uidOf(RecordKind.TASK, repo.snapshot.value.tasks[1].id)!!.startsWith("legacy:"), "un exemple modifié devient une donnée ordinaire")
        assertEquals(d, repo.enableSync("Autre"), "sans effet si la base synchronise déjà")
    }

    @Test
    fun everyWriteIsReconciledAndPersisted() = runTest {
        val file = tmp()
        val repo = open(file)
        repo.enableSync("PC")
        val id = repo.createTask("Rapport")!!
        val d = repo.sync.value!!
        val uid = d.uidOf(RecordKind.TASK, id)!!
        val m = d.marks.getValue(RecordRef(RecordKind.TASK, uid))
        assertEquals(d.device, m.born.device)
        repo.updateTitle(id, "Rapport final")
        assertTrue("title" in repo.sync.value!!.marks.getValue(RecordRef(RecordKind.TASK, uid)).stamps)
        // Rouvrir la base rend le même état de synchronisation.
        val reopened = open(file)
        assertEquals(repo.sync.value, reopened.sync.value)
        file.delete()
    }

    @Test
    fun aWriteMissedByACrashIsCaughtUpAtOpening() = runTest {
        val file = tmp()
        val repo = open(file)
        repo.enableSync("PC")
        val id = repo.createTask("Rapport")!!
        val uid = repo.sync.value!!.uidOf(RecordKind.TASK, id)!!
        // Modification faite hors du dépôt (arrêt entre la transaction et la réconciliation).
        DriverManager.getConnection("jdbc:sqlite:${file.path}").use { it.createStatement().execute("UPDATE task SET title = 'Rattrapé' WHERE id = $id") }
        val reopened = open(file)
        assertTrue("title" in reopened.sync.value!!.marks.getValue(RecordRef(RecordKind.TASK, uid)).stamps)
        file.delete()
    }

    @Test
    fun twoDevicesConvergeThroughTheFile() = runTest {
        val a = open()
        a.enableSync("PC")
        a.createTask("Courrier")
        val b = open(posed = { KairosSnapshot() })
        b.enableSync("Téléphone")
        assertIs<MergeResult.Applied>(b.pull(a))
        assertNotNull(b.task("Courrier"))
        b.updateTitle(b.task("Courrier")!!.id, "Courrier envoyé")
        val back = a.pull(b)
        assertIs<MergeResult.Applied>(back)
        assertNotNull(a.task("Courrier envoyé"))
        assertEquals(1, a.lastMerge.value?.modified)
        // Convergés : plus rien à écrire dans les deux sens.
        assertFalse((a.pull(b) as MergeResult.Applied).outcome.writeNeeded)
        assertFalse((b.pull(a) as MergeResult.Applied).outcome.writeNeeded)
    }

    @Test
    fun aMergeThatModifiesNeedsABackupFirst() = runTest {
        val (a, b) = pair()
        a.updateTitle(a.task("Rapport")!!.id, "Changé ailleurs")
        val before = b.snapshot.value
        val result = b.merge(ExportCodec.decodeSynced(a.export())) { false }
        assertEquals(MergeResult.BackupFailed, result)
        assertEquals(before, b.snapshot.value, "rien n'est fusionné sans sauvegarde")
        var saved: KairosSnapshot? = null
        assertIs<MergeResult.Applied>(b.merge(ExportCodec.decodeSynced(a.export())) { saved = it; true })
        assertEquals(before, saved, "la sauvegarde est l'état d'avant la fusion")
    }

    @Test
    fun conflictsGoToReviewAndTakingTheOtherValueWins() = runTest {
        val (a, b) = pair()
        val title = "Rapport"
        a.updateTitle(a.task(title)!!.id, "Version A")
        b.updateTitle(b.task(title)!!.id, "Version B")
        a.pull(b)
        val item = a.syncReview.value.single()
        assertEquals("title", item.field)
        assertFalse(item.kept)
        val applied = a.snapshot.value.tasks.first { it.title.startsWith("Version") }.title
        val other = if (applied == "Version A") "Version B" else "Version A"
        a.resolveReview(item.id, takeOther = true)
        assertNotNull(a.task(other))
        assertTrue(a.syncReview.value.isEmpty())
        // Le choix est une modification comme les autres : elle s'impose ailleurs.
        b.pull(a)
        assertNotNull(b.task(other))
    }

    @Test
    fun aKeptElementCanBeDeletedAgain() = runTest {
        val (a, b) = pair()
        val t = a.task("Rapport")!!
        a.deleteTask(t.id)
        b.updateTitle(b.task(t.title)!!.id, "Modifiée sur le téléphone")
        a.pull(b)
        val kept = a.syncReview.value.single { it.kept }
        assertNotNull(a.task("Modifiée sur le téléphone"))
        a.deleteKept(kept.id)
        assertNull(a.task("Modifiée sur le téléphone"))
        b.pull(a)
        assertNull(b.task("Modifiée sur le téléphone"), "la suppression a vu la modification : elle se propage")
    }

    @Test
    fun importingIsASeriesOfLocalModifications() = runTest {
        val repo = open()
        repo.enableSync("PC")
        val backup = repo.export()
        val id = repo.snapshot.value.tasks.first().id
        val uid = repo.sync.value!!.uidOf(RecordKind.TASK, id)!!
        repo.updateTitle(id, "Après la sauvegarde")
        // Restaurer une sauvegarde de cet appareil : mêmes uid, l'ancienne valeur revient comme une modification récente.
        val decoded = ExportCodec.decodeSynced(backup)
        repo.replaceAll(decoded.snapshot, decoded.data.uids)
        assertEquals(uid, repo.sync.value!!.uidOf(RecordKind.TASK, id))
        val stamp = repo.sync.value!!.marks.getValue(RecordRef(RecordKind.TASK, uid)).stamps.getValue("title")
        assertEquals(repo.sync.value!!.device, stamp.device)
        assertNull(repo.task("Après la sauvegarde"))
    }

    @Test
    fun noTeamDataEntersASoloBase() = runTest {
        // Le fichier d'un utilisateur d'équipe : membres, tâches d'équipe, réglages d'équipe.
        val manager = open()
        manager.replaceAll(manager.snapshot.value.copy(
            tasks = manager.snapshot.value.tasks + Task(800, "Perso du manager", createdAt = now, updatedAt = now) + Task(900, "Tâche d'équipe", space = TaskSpace.TEAM, teamUid = "t-900", createdAt = now, updatedAt = now),
            members = listOf(TeamMember(1, "m-1", "Moi", "Dev", 100, 7.0, isSelf = true, archived = false, createdAt = now, updatedAt = now)),
            settings = manager.snapshot.value.settings.copy(team = TeamSettings(enabled = true, name = "Équipe", managerName = "Paul", identity = "id")),
        ))
        manager.enableSync("Manager")
        val solo = open(posed = { KairosSnapshot() })
        solo.enableSync("Solo")
        solo.pull(manager)
        assertFalse(Workspaces.hasTeamData(solo.snapshot.value))
        assertNull(solo.snapshot.value.settings.team)
        assertNotNull(solo.task("Perso du manager"), "les tâches perso, elles, sont arrivées")
        assertNull(solo.task("Tâche d'équipe"))
        // Et l'export de la base solo ne porte aucun champ d'équipe.
        assertFalse("\"members\"" in solo.export())
    }

    @Test
    fun theLocalSettingsStayOnTheDevice() = runTest {
        val a = open()
        a.enableSync("PC")
        // Après l'activation : une écriture datée, qui l'emporte (avant, la première rencontre en ferait un conflit).
        a.updateSettings(a.snapshot.value.settings.copy(language = "fr", workdayStartHour = 7))
        val b = open(posed = { KairosSnapshot(settings = Settings(language = "en")) })
        b.enableSync("Téléphone")
        b.pull(a)
        assertEquals("en", b.snapshot.value.settings.language)
        assertEquals(7, b.snapshot.value.settings.workdayStartHour)
    }
}
